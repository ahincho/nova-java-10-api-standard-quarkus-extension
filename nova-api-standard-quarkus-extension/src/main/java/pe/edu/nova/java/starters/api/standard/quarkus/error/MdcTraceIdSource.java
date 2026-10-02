package pe.edu.nova.java.starters.api.standard.quarkus.error;

import java.util.Optional;

import org.jboss.logging.MDC;

import pe.edu.nova.java.libs.api.standard.error.TraceIdSource;

/**
 * El {@code traceId} de la petición en curso, tomado del MDC de JBoss Logging.
 * <p>
 * Es donde lo deja {@code quarkus-opentelemetry}, con la clave {@value #TRACE_ID_KEY}: la misma que lee el
 * starter de Spring Boot. Un error de Nova lo captura al nacer a través de esta fuente, que se registra en
 * {@code META-INF/services} y, para la imagen nativa, en el módulo de deployment.
 */
public final class MdcTraceIdSource implements TraceIdSource {

    /** La clave del MDC con el identificador de traza. */
    public static final String TRACE_ID_KEY = "traceId";

    /** Crea la fuente; la instancia el {@code ServiceLoader}. */
    public MdcTraceIdSource() {
    }

    @Override
    public Optional<String> currentTraceId() {
        Object traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            return Optional.empty();
        }
        String text = traceId.toString();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }
}
