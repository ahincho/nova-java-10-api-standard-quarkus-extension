package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;

import pe.edu.nova.java.libs.api.standard.error.ApiError;
import pe.edu.nova.java.libs.api.standard.error.TraceIdSource;
import pe.edu.nova.java.libs.api.standard.link.ApiLink;
import pe.edu.nova.java.libs.api.standard.metadata.ApiMetadata;
import pe.edu.nova.java.libs.api.standard.page.PageInfo;
import pe.edu.nova.java.libs.api.standard.ratelimit.RateLimitInfo;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;
import pe.edu.nova.java.starters.api.standard.quarkus.error.ErrorPortProducers;
import pe.edu.nova.java.starters.api.standard.quarkus.error.ErrorResponder;
import pe.edu.nova.java.starters.api.standard.quarkus.error.MdcTraceIdSource;
import pe.edu.nova.java.starters.api.standard.quarkus.error.MicrometerErrorCounterProducer;
import pe.edu.nova.java.starters.api.standard.quarkus.error.NoopErrorCounterProducer;
import pe.edu.nova.java.starters.api.standard.quarkus.error.NovaExceptionMappers;
import pe.edu.nova.java.starters.api.standard.quarkus.error.SecurityExceptionMappers;
import pe.edu.nova.java.starters.api.standard.quarkus.error.ValidationExceptionMappers;
import pe.edu.nova.java.starters.api.standard.quarkus.jackson.ApiObjectMapperCustomizer;

/**
 * Los pasos de build de la extensión del estándar de API (ADR-050).
 *
 * <p>Corren al construir la aplicación, nunca al arrancarla: registran los beans de la extensión para que el
 * servicio no tenga que declarar {@code quarkus.index-dependency}, el {@link TraceIdSource} y los records del
 * sobre para la imagen nativa, y eligen qué mappers y qué contador activar según lo que el servicio trae.
 */
public class NovaApiStandardProcessor {

    /** El nombre con que Quarkus lista la extensión al arrancar. */
    static final String FEATURE = "nova-api-standard";

    /** Crea el procesador; lo instancia Quarkus. */
    public NovaApiStandardProcessor() {}

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Los beans de la extensión. Una librería con un índice de Jandex solo se descubre si el servicio la
     * declara con {@code quarkus.index-dependency}; registrados desde aquí, la extensión funciona con solo
     * agregar la dependencia. Se marcan como no removibles: nadie los inyecta por su clase, los llaman Quarkus
     * REST y Jackson.
     *
     * <p>Los productores de los puertos son {@code @DefaultBean}: el servicio los reemplaza con un bean
     * propio. El contador es el de Micrometer si Micrometer es el sistema de métricas del servicio y el vacío si
     * no; nunca los dos, porque serían dos {@code @DefaultBean} del mismo tipo. Los mappers de validación y de
     * seguridad nombran clases que solo existen si el servicio trae esas extensiones, así que se registran
     * únicamente entonces.
     *
     * <p>Micrometer no se detecta con una capacidad: {@code quarkus-micrometer} declara {@code io.quarkus.metrics},
     * el nombre genérico de cualquier sistema de métricas. Lo que dice si Micrometer está encendido es el
     * {@link MetricsCapabilityBuildItem} que produce, y Quarkus lo ofrece justo para esto.
     */
    @BuildStep
    AdditionalBeanBuildItem beans(Capabilities capabilities, Optional<MetricsCapabilityBuildItem> metrics) {
        AdditionalBeanBuildItem.Builder beans = AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClasses(ApiObjectMapperCustomizer.class, ErrorPortProducers.class, ErrorResponder.class)
                .addBeanClasses(names(mapperClasses(capabilities)));
        boolean micrometer = metrics.filter(capability -> capability.metricsSupported(MetricsFactory.MICROMETER))
                .isPresent();
        beans.addBeanClass(micrometer ? MicrometerErrorCounterProducer.class : NoopErrorCounterProducer.class);
        return beans.build();
    }

    /**
     * Las clases con {@code @ServerExceptionMapper}. Quarkus REST busca esa anotación en el índice de la
     * aplicación y de las dependencias que el servicio declara; ser bean no alcanza para que el mapper entre en
     * la cadena de excepciones.
     */
    @BuildStep
    AdditionalIndexedClassesBuildItem indexedClasses(Capabilities capabilities) {
        return new AdditionalIndexedClassesBuildItem(
                names(mapperClasses(capabilities)).toArray(String[]::new));
    }

    /**
     * La fuente del {@code traceId}. {@code TraceIdCapture}, de {@code nova-api-standard}, encuentra las fuentes
     * con {@code ServiceLoader}, y en una imagen nativa solo ve lo que se registró al construirla: sin este paso
     * un error nacería sin {@code traceId}.
     */
    @BuildStep
    ServiceProviderBuildItem traceIdSource() {
        return new ServiceProviderBuildItem(TraceIdSource.class.getName(), MdcTraceIdSource.class.getName());
    }

    /**
     * Los records que viajan en el cuerpo de una respuesta. El sobre se arma dentro de un mapper de
     * excepciones, donde el análisis de la imagen nativa no lo ve: sin registrarlos, Jackson no puede leer los
     * componentes de los records y cada respuesta de error termina en 500.
     */
    @BuildStep
    ReflectiveClassBuildItem envelopeRecords() {
        return ReflectiveClassBuildItem.builder(
                        ApiResponse.class,
                        ApiError.class,
                        ApiMetadata.class,
                        ApiLink.class,
                        RateLimitInfo.class,
                        PageInfo.class)
                .constructors()
                .methods()
                .fields()
                .build();
    }

    /**
     * Los mappers que corresponden a lo que el servicio trae: los del núcleo siempre, y los de validación y
     * seguridad solo con su extensión.
     */
    private static List<Class<?>> mapperClasses(Capabilities capabilities) {
        List<Class<?>> mappers = new ArrayList<>();
        mappers.add(NovaExceptionMappers.class);
        if (capabilities.isPresent(Capability.HIBERNATE_VALIDATOR)) {
            mappers.add(ValidationExceptionMappers.class);
        }
        if (capabilities.isPresent(Capability.SECURITY)) {
            mappers.add(SecurityExceptionMappers.class);
        }
        return mappers;
    }

    private static List<String> names(List<Class<?>> classes) {
        return classes.stream().map(Class::getName).toList();
    }
}
