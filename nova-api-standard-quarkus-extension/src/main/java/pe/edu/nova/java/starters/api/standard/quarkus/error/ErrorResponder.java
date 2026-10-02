package pe.edu.nova.java.starters.api.standard.quarkus.error;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import pe.edu.nova.java.libs.api.standard.error.ErrorPorts;
import pe.edu.nova.java.libs.api.standard.error.FieldError;
import pe.edu.nova.java.libs.api.standard.error.NovaError;
import pe.edu.nova.java.libs.api.standard.error.PlatformError;
import pe.edu.nova.java.libs.api.standard.error.SanitizedFailure;
import pe.edu.nova.java.libs.api.standard.error.SerializedError;

/**
 * El núcleo del manejo de errores en Quarkus (ADR-031 y ADR-050): responde cada error con los tres puertos
 * de {@link ErrorPorts}, y antes lo registra en el log una sola vez.
 * <p>
 * Los mappers de {@link NovaExceptionMappers} y los de validación y seguridad solo deciden de qué clase es
 * cada excepción; todo lo demás pasa por aquí, para que ningún camino se salte una regla:
 * <ul>
 *   <li>El log lleva {@code traceId}, {@code layer}, {@code code}, {@code status} y, si hay,
 *       {@code upstream}, como entradas del MDC, de modo que {@code quarkus-logging-json} las escribe como
 *       campos. Lo esperado ({@code domain} y {@code application}) va en {@code warn}, sin stack trace; un
 *       incidente ({@code infrastructure} y {@code platform}) va en {@code error}, con la causa.</li>
 *   <li>Los puertos nunca ven el proveedor ni la causa: reciben un {@link SanitizedFailure}.</li>
 *   <li>Si la petición no tiene {@code traceId}, se genera uno y se escribe igual en el log y en el
 *       cuerpo.</li>
 *   <li>Cada error respondido se cuenta en el {@link ErrorCounter}.</li>
 * </ul>
 * No se reemplaza: lo que un servicio o una organización cambia son los puertos.
 */
@Singleton
public class ErrorResponder {

    private static final Logger LOG = Logger.getLogger(ErrorResponder.class);

    /** Headers que describen el cuerpo de la excepción original y no el del sobre que se escribe en su lugar. */
    private static final Set<String> ENTITY_HEADERS = Set.of("content-type", "content-length", "transfer-encoding");

    private final ErrorPorts ports;
    private final ErrorCounter counter;

    /**
     * Crea el núcleo.
     *
     * @param ports   los tres puertos de errores: los de Nova o los del servicio
     * @param counter cuenta cada error respondido
     */
    @Inject
    public ErrorResponder(ErrorPorts ports, ErrorCounter counter) {
        this.ports = Objects.requireNonNull(ports, "ports es obligatorio");
        this.counter = Objects.requireNonNull(counter, "counter es obligatorio");
    }

    /**
     * Responde un error de Nova con el status de su tipo.
     *
     * @param error el error
     * @return la respuesta que deciden los puertos
     */
    public Response respond(NovaError error) {
        return respond(error, error);
    }

    /**
     * Responde cualquier excepción que no sea de Nova ni del framework: es un {@code PlatformError} y sale
     * como 500.
     *
     * @param cause la excepción, que va al log con su stack trace
     * @return la respuesta que deciden los puertos
     */
    public Response respondUnexpected(Throwable cause) {
        return respond(PlatformError.internal(cause), cause);
    }

    /**
     * Responde una excepción propia del framework, que ya trae su status: un 4xx es {@code application}; un
     * 502, un 503 o un 504, {@code infrastructure}; y cualquier otro 5xx, {@code platform}. El
     * {@code ErrorStatusMapper} no se consulta.
     *
     * @param status  el status, de 400 a 599
     * @param message el mensaje para el cliente, que solo llega en un 4xx (puede ser null)
     * @param fields  los errores por campo, que solo llegan en un 4xx
     * @param headers los headers que declara la excepción, como el {@code Allow} de un 405 (puede ser null)
     * @param cause   la excepción, para el log
     * @return la respuesta que deciden los puertos
     */
    public Response respondFramework(int status, String message, List<FieldError> fields,
                                     MultivaluedMap<String, Object> headers, Throwable cause) {
        String generated = ensureTraceId();
        try {
            SanitizedFailure failure = SanitizedFailure.ofStatus(status, null, message, fields, null);
            log(failure, null, cause);
            return write(ports.respond(failure), headers);
        } finally {
            releaseTraceId(generated);
        }
    }

    private Response respond(NovaError error, Throwable logged) {
        String generated = ensureTraceId();
        try {
            SanitizedFailure failure = SanitizedFailure.of(error, ports.statusMapper().statusOf(error.type()));
            log(failure, error.upstream().orElse(null), logged);
            return write(ports.respond(failure), null);
        } finally {
            releaseTraceId(generated);
        }
    }

    /**
     * Escribe la línea de log del error, una sola vez y antes de llamar a los puertos, y lo cuenta.
     *
     * @param failure  el fallo saneado, que trae el status, la capa, el código y el {@code traceId}
     * @param upstream el proveedor que falló, si hay uno; solo va al log
     * @param cause    la excepción, cuyo stack trace va al log si es un incidente
     */
    private void log(SanitizedFailure failure, String upstream, Throwable cause) {
        boolean incident = failure.layer().isIncident();
        String layer = failure.layer().label();
        String traceId = failure.traceId().orElse(null);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("traceId", traceId);
        fields.put("layer", layer);
        fields.put("code", failure.code());
        fields.put("status", Integer.toString(failure.status()));
        fields.put("upstream", upstream);
        Map<String, Object> previous = putMdc(fields);
        try {
            // Lo esperado registra lo mismo que ve el cliente; un incidente, la excepción con su mensaje. Los
            // campos van también en el texto, porque un log de consola sin formato estructurado no los muestra
            String detail = incident ? describe(cause) : failure.message();
            String text = "[Nova Platform] %d %s layer=%s%s traceId=%s (%s): %s";
            String upstreamText = upstream != null ? " upstream=" + upstream : "";
            String type = cause.getClass().getSimpleName();
            if (incident) {
                LOG.errorf(cause, text, failure.status(), failure.code(), layer, upstreamText,
                        traceId != null ? traceId : "-", type, detail);
            } else {
                LOG.warnf(text, failure.status(), failure.code(), layer, upstreamText,
                        traceId != null ? traceId : "-", type, detail);
            }
        } finally {
            restoreMdc(previous);
        }
        counter.increment(layer, failure.code());
    }

    /**
     * Arma la respuesta con lo que decidieron el catálogo y el serializador: el status, el cuerpo y los
     * headers, sobre los de la excepción original.
     *
     * @param serialized lo que escribe el serializador
     * @param extra      los headers propios de la excepción, como el {@code Allow} de un 405 (puede ser null)
     * @return la respuesta
     */
    private static Response write(SerializedError serialized, MultivaluedMap<String, Object> extra) {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        if (extra != null) {
            extra.forEach((name, values) -> {
                if (!ENTITY_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    headers.put(name, new ArrayList<>(values));
                }
            });
        }
        String contentType = MediaType.APPLICATION_JSON;
        for (Map.Entry<String, String> header : serialized.headers().entrySet()) {
            if (header.getKey().equalsIgnoreCase("Content-Type")) {
                // Otro formato, como RFC 7807, declara su propio tipo de contenido
                contentType = header.getValue();
                continue;
            }
            headers.keySet().removeIf(name -> name.equalsIgnoreCase(header.getKey()));
            headers.putSingle(header.getKey(), header.getValue());
        }
        return Response.status(serialized.status())
                .replaceAll(headers)
                .type(contentType)
                .entity(serialized.body())
                .build();
    }

    /**
     * Si la petición no tiene {@code traceId}, genera uno y lo deja en el MDC mientras se responde el
     * error, para que el log y el cuerpo lleven el mismo.
     *
     * @return el que se generó, o null si ya había uno
     */
    private static String ensureTraceId() {
        Object current = MDC.get(MdcTraceIdSource.TRACE_ID_KEY);
        if (current != null && !current.toString().isBlank()) {
            return null;
        }
        String generated = UUID.randomUUID().toString().replace("-", "");
        MDC.put(MdcTraceIdSource.TRACE_ID_KEY, generated);
        return generated;
    }

    private static void releaseTraceId(String generated) {
        if (generated != null) {
            MDC.remove(MdcTraceIdSource.TRACE_ID_KEY);
        }
    }

    /**
     * Pone las entradas en el MDC y recuerda lo que había, para devolverlo al terminar. Una entrada sin valor
     * se salta.
     */
    private static Map<String, Object> putMdc(Map<String, String> entries) {
        Map<String, Object> previous = new LinkedHashMap<>();
        entries.forEach((key, value) -> {
            if (value != null) {
                previous.put(key, MDC.put(key, value));
            }
        });
        return previous;
    }

    private static void restoreMdc(Map<String, Object> previous) {
        previous.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }

    /**
     * Describe una excepción para el log, con su mensaje si lo tiene.
     *
     * @param cause la excepción
     * @return el mensaje, o el nombre de la clase si no trae uno
     */
    private static String describe(Throwable cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getName() : message;
    }
}
