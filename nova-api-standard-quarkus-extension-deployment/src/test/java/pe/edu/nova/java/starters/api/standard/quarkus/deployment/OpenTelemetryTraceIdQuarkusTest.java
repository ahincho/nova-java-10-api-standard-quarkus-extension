package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import pe.edu.nova.java.starters.api.standard.quarkus.deployment.RecordingLogHandler.Line;

/**
 * El {@code traceId} que llena {@code quarkus-opentelemetry}, no un filtro de prueba: la petición trae un
 * {@code traceparent} de W3C, OpenTelemetry deja su trace id en el MDC con la clave {@code traceId} y el error
 * lo lleva a {@code metadata.traceId} y al log, para que lo que un alumno cita sea lo que hay en el tablero.
 */
class OpenTelemetryTraceIdQuarkusTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    private static final RecordingLogHandler LOG = new RecordingLogHandler();

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(OrderResource.class, RecordingLogHandler.class, Line.class))
            // Sin exportador: la prueba no sale a la red
            .overrideConfigKey("quarkus.otel.traces.exporter", "none");

    @BeforeAll
    static void captureTheLog() {
        LOG.attach();
    }

    @AfterAll
    static void releaseTheLog() {
        LOG.detach();
    }

    @BeforeEach
    void forgetEarlierLines() {
        LOG.clear();
    }

    @Test
    void theTraceIdOfOpenTelemetryReachesTheBodyAndTheLog() {
        given().header("traceparent", TRACEPARENT)
                .get("/orders/42")
                .then()
                .statusCode(404)
                .body("metadata.traceId", equalTo(TRACE_ID));

        var lines = LOG.lines();
        assertEquals(1, lines.size(), lines.toString());
        assertEquals(TRACE_ID, lines.get(0).mdc().get("traceId"));
        assertTrue(lines.get(0).message().contains("traceId=" + TRACE_ID));
    }

    @Test
    void anErrorBornInsideTheRequestCapturesItAtBirth() {
        given().header("traceparent", TRACEPARENT)
                .get("/orders/42/shipping")
                .then()
                .statusCode(504)
                .body("metadata.traceId", equalTo(TRACE_ID));
    }
}
