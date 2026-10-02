package pe.edu.nova.java.starters.api.standard.quarkus.error;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import pe.edu.nova.java.libs.api.standard.error.NovaError;

/**
 * El núcleo del manejo de errores en Quarkus REST (ADR-031 y ADR-050): lee cada excepción por lo que es y
 * deja que {@link ErrorResponder} la registre y la responda con los puertos.
 * <p>
 * Una excepción del framework se lee por su status:
 * <ul>
 *   <li>Un {@link NovaError} sale con el status que le da el {@code ErrorStatusMapper} a su tipo.</li>
 *   <li>Una {@link WebApplicationException}, y sus subclases como {@code NotFoundException},
 *       {@code NotAllowedException} o {@code NotSupportedException}, sale con el status de su respuesta y
 *       conserva sus headers, como el {@code Allow} de un 405. Un 4xx es {@code application}; un 502, un 503
 *       o un 504, {@code infrastructure}; y cualquier otro 5xx, {@code platform}.</li>
 *   <li>Un cuerpo que Jackson no puede leer es un 400.</li>
 *   <li>Cualquier otra excepción, incluidas {@link IllegalArgumentException} y {@link SecurityException}, es
 *       un {@code PlatformError} y sale como 500.</li>
 * </ul>
 * La validación y la seguridad están en {@link ValidationExceptionMappers} y {@link SecurityExceptionMappers},
 * que el módulo de deployment registra solo si el servicio tiene Hibernate Validator o Quarkus Security.
 * <p>
 * Los mappers no son reemplazables, porque son el núcleo: lo que un servicio cambia son los puertos.
 * Lo registra el módulo de deployment de la extensión; no es una API para los servicios.
 */
@Singleton
public class NovaExceptionMappers {

    /** El mensaje de un cuerpo ilegible, el mismo texto fijo que el starter de Spring Boot. */
    static final String UNREADABLE_BODY = "No se pudo leer el cuerpo de la solicitud";

    /** Cuántas causas se recorren buscando el error de Jackson, para no girar en una cadena circular. */
    private static final int MAX_CAUSE_DEPTH = 10;

    private final ErrorResponder responder;

    /**
     * Crea los mappers.
     *
     * @param responder el núcleo que registra y responde
     */
    @Inject
    public NovaExceptionMappers(ErrorResponder responder) {
        this.responder = Objects.requireNonNull(responder, "responder es obligatorio");
    }

    /**
     * Responde un error de Nova con el status de su tipo.
     *
     * @param error el error
     * @return la respuesta que deciden los puertos
     */
    @ServerExceptionMapper
    public Response novaError(NovaError error) {
        return responder.respond(error);
    }

    /**
     * Responde una excepción de JAX-RS, que lleva su status en la respuesta: la ruta que no existe (404), el
     * método que no se acepta (405), el tipo de contenido que no se soporta (415) y las que lanza el propio
     * servicio. Conserva los headers que declara, y el cuerpo lo escribe el serializador.
     * <p>
     * Una respuesta que no es un error, como una redirección, sigue su camino sin tocarse.
     *
     * @param exception la excepción
     * @return la respuesta que deciden los puertos
     */
    @ServerExceptionMapper
    public Response webApplication(WebApplicationException exception) {
        Response original = exception.getResponse();
        int status = original.getStatus();
        if (status < 400) {
            return original;
        }
        String message = status == 400 && hasUnreadableBodyCause(exception) ? UNREADABLE_BODY : null;
        return responder.respondFramework(Math.min(status, 599), message, List.of(), original.getHeaders(), exception);
    }

    /**
     * Responde un cuerpo que no calza con lo que el recurso espera, como un campo con el tipo equivocado o un
     * cuerpo vacío (400). El mensaje de Jackson describe el parser y los tipos internos, así que no va al
     * cliente.
     *
     * @param exception la excepción
     * @return la respuesta 400
     */
    @ServerExceptionMapper
    public Response unreadableBody(MismatchedInputException exception) {
        return responder.respondFramework(400, UNREADABLE_BODY, List.of(), null, exception);
    }

    /**
     * Responde cualquier otra excepción. Es un {@code PlatformError} y sale como 500, con el mensaje
     * genérico del catálogo: la excepción y su mensaje van solo al log.
     *
     * @param exception la excepción
     * @return la respuesta que deciden los puertos
     */
    @ServerExceptionMapper
    public Response unexpected(Throwable exception) {
        return responder.respondUnexpected(exception);
    }

    /**
     * Indica si la excepción envuelve un error de Jackson al leer el cuerpo. Quarkus REST lanza un 400 con el
     * error de Jackson como causa cuando el JSON está mal formado.
     */
    private static boolean hasUnreadableBodyCause(Throwable exception) {
        Throwable cause = exception.getCause();
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof JsonProcessingException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
