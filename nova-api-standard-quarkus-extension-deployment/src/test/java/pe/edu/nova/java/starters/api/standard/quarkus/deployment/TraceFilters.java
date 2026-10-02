package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;

import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Simula lo que hace {@code quarkus-opentelemetry} en una petición: deja el {@code traceId} en el MDC con la
 * clave {@code traceId} mientras dura, y lo saca al responder.
 */
public class TraceFilters {

    /** El header con el que la prueba le dice a la petición cuál es su traza. */
    public static final String HEADER = "X-Trace-Id";

    /** Lo que había en el MDC cuando la petición ya estaba respondida, para saber si algo se quedó. */
    public static final AtomicReference<Map<String, Object>> MDC_AFTER_ERROR = new AtomicReference<>();

    @ServerRequestFilter
    public void open(ContainerRequestContext request) {
        String traceId = request.getHeaderString(HEADER);
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
    }

    @ServerResponseFilter
    public void close(ContainerResponseContext response) {
        MDC_AFTER_ERROR.set(new HashMap<>(MDC.getMap()));
        MDC.remove("traceId");
    }
}
