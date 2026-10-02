package pe.edu.nova.java.starters.api.standard.quarkus.error;

import io.quarkus.arc.DefaultBean;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import pe.edu.nova.java.libs.api.standard.error.ErrorCatalog;
import pe.edu.nova.java.libs.api.standard.error.ErrorPorts;
import pe.edu.nova.java.libs.api.standard.error.ErrorSerializer;
import pe.edu.nova.java.libs.api.standard.error.ErrorStatusMapper;
import pe.edu.nova.java.libs.api.standard.error.NovaErrorCatalog;
import pe.edu.nova.java.libs.api.standard.error.NovaErrorSerializer;
import pe.edu.nova.java.libs.api.standard.error.NovaErrorStatusMapper;

/**
 * Los puertos de errores de ADR-031 con la implementación de Nova.
 * <p>
 * Cada puerto es un {@code @DefaultBean}: un servicio, o la extensión de una organización como UTP, declara
 * su propio bean del mismo tipo y la extensión lo usa, sin forkear. Es el papel que cumple
 * {@code @ConditionalOnMissingBean} en Spring Boot. Lo que no se reemplaza es el núcleo, los mappers de
 * {@link NovaExceptionMappers}, que registran el error una sola vez y les pasan a los puertos un fallo sin
 * el proveedor ni la causa.
 * <p>
 * Lo registra el módulo de deployment de la extensión; no es una API para los servicios.
 */
@Singleton
public class ErrorPortProducers {

    /** Lo crea Quarkus. */
    public ErrorPortProducers() {
    }

    /**
     * El status de cada capa y tipo, con la tabla de ADR-031.
     *
     * @return el mapeador de Nova
     */
    @Produces
    @DefaultBean
    @Singleton
    public ErrorStatusMapper novaErrorStatusMapper() {
        return new NovaErrorStatusMapper();
    }

    /**
     * El código y el mensaje que ve el cliente, con el catálogo de la plataforma.
     *
     * @return el catálogo de Nova
     */
    @Produces
    @DefaultBean
    @Singleton
    public ErrorCatalog novaErrorCatalog() {
        return new NovaErrorCatalog();
    }

    /**
     * El cuerpo y los headers de un error: el sobre de Nova, con {@code metadata.traceId} y
     * {@code Retry-After}.
     *
     * @return el serializador de Nova
     */
    @Produces
    @DefaultBean
    @Singleton
    public ErrorSerializer novaErrorSerializer() {
        return new NovaErrorSerializer();
    }

    /**
     * Los tres puertos juntos, en el orden en que se consultan. Toma los beans del servicio si los hay, y si
     * no los de Nova.
     *
     * @param statusMapper el mapeador
     * @param catalog      el catálogo
     * @param serializer   el serializador
     * @return los puertos
     */
    @Produces
    @DefaultBean
    @Singleton
    public ErrorPorts novaErrorPorts(ErrorStatusMapper statusMapper, ErrorCatalog catalog,
                                     ErrorSerializer serializer) {
        return new ErrorPorts(statusMapper, catalog, serializer);
    }
}
