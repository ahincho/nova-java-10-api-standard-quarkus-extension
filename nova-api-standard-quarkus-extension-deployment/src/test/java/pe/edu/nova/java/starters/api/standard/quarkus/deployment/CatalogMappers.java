package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.util.Map;

import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Un mapper del servicio de las pruebas, que contesta con un cuerpo propio: el equivalente del
 * {@code @RestControllerAdvice} del servicio de las pruebas de Spring.
 */
public class CatalogMappers {

    /**
     * Contesta 423 con quién tiene bloqueado el producto.
     *
     * @param exception la excepción
     * @return el cuerpo propio del servicio
     */
    @ServerExceptionMapper
    public Response locked(ProductLockedException exception) {
        return Response.status(423).entity(Map.of("lockedBy", exception.getMessage())).build();
    }

    /** Un producto que otro proceso tiene bloqueado. */
    public static class ProductLockedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Crea la excepción.
         *
         * @param lockedBy quién tiene bloqueado el producto
         */
        public ProductLockedException(String lockedBy) {
            super(lockedBy);
        }
    }
}
