package pe.edu.nova.java.starters.api.standard.quarkus.response;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Flow;

import io.smallrye.mutiny.Multi;

import io.vertx.core.buffer.Buffer;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;

import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import pe.edu.nova.java.libs.api.standard.error.ErrorPorts;
import pe.edu.nova.java.libs.api.standard.error.SanitizedFailure;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

/**
 * El filtro de respuesta que envuelve el éxito en el sobre de Nova, como el {@code ApiResponseInterceptor} del
 * starter de Spring Boot (ADR-050, pregunta resuelta 1).
 * <p>
 * Un recurso devuelve el objeto y el filtro lo entrega como {@link ApiResponse}, con el status real de la
 * respuesta: un 201 dice 201 y no 200. Es la misma regla que aplica Spring, con el equivalente de cada parte en
 * JAX-RS:
 * <ul>
 *   <li>Lo que ya es un sobre no se envuelve de nuevo: una {@link ApiResponse} armada a mano sale tal cual.</li>
 *   <li>Lo que no es un cuerpo JSON sale tal cual. En Spring son el {@code String}, el {@code byte[]} y el
 *       {@code Resource}; aquí, esos mismos y los demás cuerpos crudos de Quarkus REST ({@code InputStream},
 *       {@code File}, {@code StreamingOutput}...), los flujos de {@link Multi} y cualquier respuesta con otro
 *       tipo de contenido, como texto, CSV, PDF o un evento SSE. Un número o un booleano suelto tampoco se
 *       envuelve cuando el recurso no declara JSON, porque Quarkus REST lo escribe como texto; Spring lo escribe
 *       como JSON y lo envuelve.</li>
 *   <li>Un 4xx o 5xx que el recurso contesta sin lanzar una excepción, como {@code Response.status(404).build()},
 *       sale como sobre de error armado con los puertos, con el cuerpo propio del recurso en {@code data} si lo
 *       mandó. No se escribe en el log ni se cuenta: el recurso lo contestó a propósito.</li>
 *   <li>Un 2xx sin cuerpo, como {@code Response.ok().build()}, sale como éxito con {@code data} en {@code null}.
 *       Un 204, un 205 o un 206 no se tocan, y tampoco un 1xx ni un 3xx. En un 204, un 205 y un 304 el resultado
 *       en el cable es el mismo que el de Spring, que arma el sobre y deja que Tomcat descarte su cuerpo. Un 206 y
 *       un 3xx sí los envuelve Spring; aquí no, porque un 206 es un fragmento del cuerpo y el de una redirección
 *       no lo lee nadie. Un método que devuelve {@code null} es un 204 en JAX-RS, y Spring lo contesta con un 200
 *       y {@code data} en {@code null}.</li>
 *   <li>Lo que contesta el manejo de excepciones nunca se envuelve: los mappers de la extensión ya contestan
 *       con el sobre que armaron los puertos, y una {@code WebApplicationException} con cuerpo propio se
 *       devuelve tal cual, como manda JAX-RS. Spring envuelve lo que contesta un {@code @ControllerAdvice} del
 *       servicio; aquí no hay cómo distinguir el mapper de un servicio del de la extensión sin marcar cada
 *       respuesta, y ADR-050 manda a cambiar la forma de un error con los puertos, no con un mapper.</li>
 *   <li>Solo ve los recursos JAX-RS. SmallRye Health, las métricas, OpenAPI y la Dev UI son rutas de Vert.x que
 *       no pasan por Quarkus REST, así que contestan con su propio formato, y un health caído sigue diciendo
 *       {@code "status":"DOWN"}. Es el equivalente de dejar fuera a Actuator.</li>
 *   <li>Si el cliente no acepta el JSON con que se escribiría el sobre no se toca la respuesta, como en Spring.
 *       HEAD y OPTIONS tampoco: no llevan cuerpo.</li>
 * </ul>
 * Spring no tiene una anotación para excluir un método del sobre, y esta extensión tampoco: se sale del sobre
 * devolviendo un {@code String}, un {@code byte[]} o un flujo.
 * <p>
 * Lo registra el módulo de deployment de la extensión; no es una API para los servicios.
 */
@Singleton
public class ApiResponseFilter {

    private static final int FIRST_ERROR_STATUS = 400;
    private static final int LAST_ERROR_STATUS = 599;
    private static final int NO_CONTENT = 204;
    private static final int RESET_CONTENT = 205;
    private static final int PARTIAL_CONTENT = 206;
    private static final int FIRST_SUCCESS_STATUS = 200;
    private static final int LAST_SUCCESS_STATUS = 299;

    /** El tipo de contenido de un flujo de objetos JSON, que Quarkus REST escribe uno a uno y no como un valor. */
    private static final String STREAM_JSON = "stream+json";

    private final ErrorPorts ports;

    /**
     * Crea el filtro.
     *
     * @param ports los puertos de errores, con los que arma el sobre de un 4xx o 5xx sin excepción
     */
    @Inject
    public ApiResponseFilter(ErrorPorts ports) {
        this.ports = Objects.requireNonNull(ports, "ports es obligatorio");
    }

    /**
     * Envuelve la respuesta en el sobre de Nova, si corresponde. Quarkus REST lo llama con cada respuesta de un
     * recurso, incluidas las que salen del manejo de excepciones, que trae la excepción en {@code thrown}.
     *
     * @param request  la petición
     * @param response la respuesta, cuyo cuerpo se reemplaza por el sobre
     * @param thrown   la excepción que dio lugar a la respuesta, o {@code null} si el recurso contestó solo
     */
    @ServerResponseFilter
    public void wrap(ContainerRequestContext request, ContainerResponseContext response, Throwable thrown) {
        MediaType mediaType = response.getMediaType();
        if (thrown != null || hasNoBody(request) || !accepts(request, mediaType)) {
            return;
        }
        Object envelope = envelopeOf(response.getStatus(), response.getEntity(), mediaType);
        if (envelope != null) {
            // El Content-Length que fijó el recurso, si lo fijó, era el del cuerpo que se reemplaza
            response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
            if (isUndetermined(mediaType)) {
                // El recurso no declaró un tipo y el sobre es JSON: se fija aquí, y no lo decide el Accept del
                // cliente, que con un comodín como application/* terminaba en application/octet-stream
                Annotation[] annotations = response.getEntityAnnotations();
                response.setEntity(envelope, annotations == null ? new Annotation[0] : annotations,
                        MediaType.APPLICATION_JSON_TYPE);
            } else {
                response.setEntity(envelope);
            }
        }
    }

    /**
     * Decide qué sobre lleva la respuesta de un recurso.
     *
     * @param status    el status real de la respuesta
     * @param entity    el cuerpo que armó el recurso, o {@code null} si no tiene
     * @param mediaType el tipo de contenido de la respuesta, o {@code null} si todavía no se negoció
     * @return el cuerpo que reemplaza al del recurso, o {@code null} si la respuesta sale como está
     */
    Object envelopeOf(int status, Object entity, MediaType mediaType) {
        if (entity instanceof ApiResponse<?> || !isJsonBody(entity, mediaType)) {
            return null;
        }
        if (status >= FIRST_ERROR_STATUS && status <= LAST_ERROR_STATUS) {
            return errorEnvelope(status, entity);
        }
        if (!isWrappedSuccess(status)) {
            return null;
        }
        // El builder marca success cuando no hay errores, así que con 200 el resultado es el de ApiResponse.ok
        return ApiResponse.<Object>builder().data(entity).status(status).build();
    }

    /**
     * Arma el sobre de error de un 4xx o 5xx con los puertos, como una excepción del framework con ese status.
     * Si el recurso mandó un cuerpo propio se conserva en {@code data} para no perderlo; si el serializador del
     * servicio no arma un {@link ApiResponse}, sale lo que él arme.
     */
    private Object errorEnvelope(int status, Object entity) {
        Object serialized = ports.respond(SanitizedFailure.ofStatus(status, null, null, null, null)).body();
        if (entity == null || !(serialized instanceof ApiResponse<?> error)) {
            return serialized;
        }
        return new ApiResponse<>(false, status, entity, error.errors(), error.metadata(), List.of(), null, null);
    }

    /** Los 2xx con un valor completo: un 204, un 205 y un 206 no lo llevan. */
    private static boolean isWrappedSuccess(int status) {
        return status >= FIRST_SUCCESS_STATUS
                && status <= LAST_SUCCESS_STATUS
                && status != NO_CONTENT
                && status != RESET_CONTENT
                && status != PARTIAL_CONTENT;
    }

    /** HEAD no lleva cuerpo y la respuesta de OPTIONS la arma el propio framework. */
    private static boolean hasNoBody(ContainerRequestContext request) {
        return HttpMethod.HEAD.equals(request.getMethod()) || HttpMethod.OPTIONS.equals(request.getMethod());
    }

    /**
     * Indica si el cliente acepta el JSON con que se escribiría el sobre: el tipo de contenido de la respuesta o,
     * si todavía no se negoció, {@code application/json}. Un cliente sin {@code Accept}, o con comodines, lo
     * acepta. Sin esta comprobación, un {@code Response.ok().build()} para un cliente que solo acepta otro tipo
     * pasaría de una respuesta vacía a un 406.
     * <p>
     * Un {@code Accept} que no se puede leer deja la respuesta como está: el filtro no tiene por qué convertir en
     * un 500 lo que Quarkus REST contesta, con ese mismo header, sin él.
     */
    private static boolean accepts(ContainerRequestContext request, MediaType mediaType) {
        List<MediaType> accepted;
        try {
            accepted = request.getAcceptableMediaTypes();
        } catch (RuntimeException malformedHeader) {
            return false;
        }
        if (accepted == null || accepted.isEmpty()) {
            return true;
        }
        MediaType written = isUndetermined(mediaType) ? MediaType.APPLICATION_JSON_TYPE : mediaType;
        return accepted.stream().anyMatch(type -> type.isCompatible(written));
    }

    /** El tipo de contenido todavía no se negoció: el recurso no lo declaró, o lo declaró con un comodín. */
    private static boolean isUndetermined(MediaType mediaType) {
        return mediaType == null || mediaType.isWildcardType() || mediaType.isWildcardSubtype();
    }

    /**
     * Indica si el cuerpo se escribe como JSON. Antes de negociar el tipo de contenido, que es lo que pasa con
     * un recurso que arma su {@code Response} sin tipo, un objeto cualquiera sale como JSON y un número o un
     * booleano suelto como texto.
     */
    private static boolean isJsonBody(Object entity, MediaType mediaType) {
        if (isRawContent(entity)) {
            return false;
        }
        if (isUndetermined(mediaType)) {
            return !(entity instanceof Number || entity instanceof Boolean || entity instanceof Character);
        }
        return "application".equalsIgnoreCase(mediaType.getType()) && isJsonSubtype(mediaType.getSubtype())
                && !STREAM_JSON.equalsIgnoreCase(mediaType.getSubtype());
    }

    private static boolean isJsonSubtype(String subtype) {
        String lower = subtype.toLowerCase(Locale.ROOT);
        return "json".equals(lower) || lower.endsWith("+json");
    }

    /** Los cuerpos que Quarkus REST escribe con su propio escritor y que no saben escribir un sobre. */
    private static boolean isRawContent(Object entity) {
        return entity instanceof CharSequence
                || entity instanceof byte[]
                || entity instanceof char[]
                || entity instanceof InputStream
                || entity instanceof Reader
                || entity instanceof File
                || entity instanceof Path
                || entity instanceof StreamingOutput
                || entity instanceof Buffer
                || entity instanceof Multi
                || entity instanceof Flow.Publisher;
    }
}
