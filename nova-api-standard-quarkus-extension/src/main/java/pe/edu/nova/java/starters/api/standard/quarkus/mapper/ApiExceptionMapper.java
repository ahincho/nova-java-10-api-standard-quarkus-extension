package pe.edu.nova.java.starters.api.standard.quarkus.mapper;

import pe.edu.nova.java.libs.api.standard.error.ApiError;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Mapper generico de excepciones para cualquier {@link Throwable} no controlado.
 * Lo serializa como un {@link ApiResponse} JSON consistente con la convencion
 * definida en {@code nova-java-api-standard}.
 * <p>
 * <strong>Forma Quarkus idiomatica</strong>: usamos
 * {@link ServerExceptionMapper @ServerExceptionMapper} (la API especifica de
 * Quarkus REST / resteasy-reactive) en vez de implementar
 * {@code ExceptionMapper<Throwable>}. Razon: resteasy-reactive procesa
 * excepciones desde el dispatch chain ANTES de llegar al JAX-RS standard
 * ExceptionMapper chain, asi que un {@code ExceptionMapper<Throwable>}
 * generico nunca se invoca para excepciones lanzadas desde resource methods.
 * En cambio, {@code @ServerExceptionMapper} es interceptado por el build-time
 * augmentation de Quarkus y queda registrado como parte del dispatch chain.
 * <p>
 * Pensado como red de seguridad: las apps pueden definir sus propios mappers
 * para tipos especificos (e.g., {@code ConstraintViolationException}) y estos
 * tendran precedencia por la especificidad JAX-RS. Este mapper solo se
 * ejecuta cuando no hay otro mas especifico.
 * <p>
 * Codigos HTTP asignados:
 * <ul>
 *   <li>{@link IllegalArgumentException} o subclases no chequeadas de validacion -&gt; 400</li>
 *   <li>{@link SecurityException} -&gt; 403</li>
 *   <li>otros -&gt; 500</li>
 * </ul>
 * Mensajes tecnicos (con stack traces) solo se incluyen en responses 5xx para
 * no filtrar detalles internos al cliente en errores de usuario.
 * <p>
 * El módulo de deployment de la extensión lo registra como bean: el servicio ya no
 * necesita {@code quarkus.index-dependency} para que Quarkus lo descubra.
 */
@Singleton
public class ApiExceptionMapper {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @ServerExceptionMapper
    public Response toResponse(Throwable exception) {
        int status = resolveStatus(exception);
        String code = resolveCode(exception);
        String message = resolveMessage(exception, status);

        if (status >= 500) {
            LOG.error("Unhandled exception mapped to " + status, exception);
        } else {
            LOG.debugf("Client error %d: %s", status, exception.getMessage());
        }

        ApiResponse<Object> body = ApiResponse.builder()
                .status(status)
                .error(ApiError.of(code, message))
                .build();
        return Response.status(status)
                .type("application/json")
                .entity(body)
                .build();
    }

    private int resolveStatus(Throwable exception) {
        if (exception instanceof IllegalArgumentException) {
            return 400;
        }
        if (exception instanceof SecurityException) {
            return 403;
        }
        return 500;
    }

    private String resolveCode(Throwable exception) {
        if (exception instanceof IllegalArgumentException) {
            return "BAD_REQUEST";
        }
        if (exception instanceof SecurityException) {
            return "FORBIDDEN";
        }
        return "INTERNAL_ERROR";
    }

    private String resolveMessage(Throwable exception, int status) {
        if (status >= 500) {
            return "Internal server error";
        }
        String raw = exception.getMessage();
        return raw == null || raw.isBlank() ? exception.getClass().getSimpleName() : raw;
    }
}