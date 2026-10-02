package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

import pe.edu.nova.java.libs.api.standard.error.ApiError;
import pe.edu.nova.java.libs.api.standard.link.ApiLink;
import pe.edu.nova.java.libs.api.standard.metadata.ApiMetadata;
import pe.edu.nova.java.libs.api.standard.page.PageInfo;
import pe.edu.nova.java.libs.api.standard.ratelimit.RateLimitInfo;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;
import pe.edu.nova.java.starters.api.standard.quarkus.jackson.ApiObjectMapperCustomizer;
import pe.edu.nova.java.starters.api.standard.quarkus.mapper.ApiExceptionMapper;

/**
 * Los pasos de build de la extensión del estándar de API (ADR-050).
 *
 * <p>Corren al construir la aplicación, nunca al arrancarla: registran los beans de la extensión para que el
 * servicio no tenga que declarar {@code quarkus.index-dependency}, y los records del sobre para la imagen nativa.
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
     * agregar la dependencia. Se marcan como no removibles: nadie los inyecta, los llaman Quarkus REST y Jackson.
     */
    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(ApiExceptionMapper.class, ApiObjectMapperCustomizer.class)
                .setUnremovable()
                .build();
    }

    /**
     * La clase con {@code @ServerExceptionMapper}. Quarkus REST busca esa anotación en el índice de la
     * aplicación y de las dependencias que el servicio declara; ser un bean no alcanza para que el mapper
     * entre en la cadena de excepciones.
     */
    @BuildStep
    AdditionalIndexedClassesBuildItem indexedClasses() {
        return new AdditionalIndexedClassesBuildItem(ApiExceptionMapper.class.getName());
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
}
