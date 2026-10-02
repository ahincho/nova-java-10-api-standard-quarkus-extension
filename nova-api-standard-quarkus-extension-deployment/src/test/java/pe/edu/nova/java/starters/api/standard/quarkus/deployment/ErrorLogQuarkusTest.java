package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.QuarkusUnitTest;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import pe.edu.nova.java.starters.api.standard.quarkus.deployment.RecordingLogHandler.Line;

/**
 * La línea de log de cada error (ADR-031 y ADR-050): lleva {@code traceId}, {@code layer}, {@code code},
 * {@code status} y, si hay, {@code upstream} como entradas del MDC; {@code domain} y {@code application} van en
 * {@code WARN} sin stack trace, e {@code infrastructure} y {@code platform} en {@code ERROR} con la causa.
 */
class ErrorLogQuarkusTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final RecordingLogHandler LOG = new RecordingLogHandler();

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(
                    OrderResource.class, TraceFilters.class, RecordingLogHandler.class, Line.class))
            // Sin OpenTelemetry cada petición llega sin traceId, y es el caso en que la extensión genera uno
            .overrideConfigKey("quarkus.otel.enabled", "false");

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
        TraceFilters.MDC_AFTER_ERROR.set(null);
    }

    @Test
    void anExpectedErrorIsAWarningWithoutStackTraceAndWithItsFields() {
        String traceId = given().get("/orders/42").then().statusCode(404).extract().path("metadata.traceId");

        Line line = onlyLine();
        assertTrue(line.isWarning(), "lo esperado va en WARN, fue " + line.level());
        assertNull(line.thrown(), "lo esperado no lleva stack trace");
        assertTrue(line.message().startsWith("[Nova Platform] 404 ORDER_NOT_FOUND layer=domain traceId=" + traceId));
        assertEquals("domain", line.mdc().get("layer"));
        assertEquals("ORDER_NOT_FOUND", line.mdc().get("code"));
        assertEquals("404", line.mdc().get("status"));
        assertEquals(traceId, line.mdc().get("traceId"));
        assertFalse(line.mdc().containsKey("upstream"));
    }

    @Test
    void anInfrastructureIncidentIsAnErrorWithTheCauseAndTheUpstream() {
        String body = given().get("/orders/42/shipping").then().statusCode(504).extract().asString();

        Line line = onlyLine();
        assertTrue(line.isError(), "un incidente va en ERROR, fue " + line.level());
        assertNotNull(line.thrown());
        assertInstanceOf(SocketTimeoutException.class, line.thrown().getCause(), "la causa completa va al log");
        assertEquals("infrastructure", line.mdc().get("layer"));
        assertEquals("GATEWAY_TIMEOUT", line.mdc().get("code"));
        assertEquals("504", line.mdc().get("status"));
        assertEquals(OrderResource.UPSTREAM, line.mdc().get("upstream"));
        assertTrue(line.message().contains("upstream=" + OrderResource.UPSTREAM));
        assertFalse(body.contains(OrderResource.UPSTREAM), "el proveedor va al log, nunca al cuerpo");
    }

    @Test
    void anUnexpectedExceptionIsAnErrorWithItsOwnStackTrace() {
        given().get("/orders/42/audit").then().statusCode(500);

        Line line = onlyLine();
        assertTrue(line.isError());
        assertInstanceOf(IllegalStateException.class, line.thrown(), "el log lleva la excepción original");
        assertEquals("platform", line.mdc().get("layer"));
        assertEquals("INTERNAL_SERVER_ERROR", line.mdc().get("code"));
        assertEquals("500", line.mdc().get("status"));
        assertTrue(line.message().contains("La auditoría de Acme falló"), "el mensaje real queda solo en el log");
    }

    @Test
    void theTraceIdOfTheRequestReachesTheBodyAndTheLog() {
        given().header(TraceFilters.HEADER, TRACE_ID)
                .get("/orders/42")
                .then()
                .statusCode(404)
                .body("metadata.traceId", org.hamcrest.Matchers.equalTo(TRACE_ID));

        Line line = onlyLine();
        assertEquals(TRACE_ID, line.mdc().get("traceId"));
        assertTrue(line.message().contains("traceId=" + TRACE_ID));
        assertEquals(TRACE_ID, TraceFilters.MDC_AFTER_ERROR.get().get("traceId"), "el id de la petición no se toca");
    }

    @Test
    void withoutATraceIdTheSameGeneratedOneGoesToTheBodyAndTheLogAndLeavesTheMdc() {
        String traceId = given().get("/orders/42/stock").then().statusCode(503).extract().path("metadata.traceId");

        assertNotNull(traceId);
        assertEquals(32, traceId.length(), "se genera un id de 32 caracteres hexadecimales");
        Line line = onlyLine();
        assertEquals(traceId, line.mdc().get("traceId"), "el log y el cuerpo llevan el mismo id");
        assertTrue(line.message().contains("traceId=" + traceId));
        assertFalse(TraceFilters.MDC_AFTER_ERROR.get().containsKey("traceId"), "el id generado sale del MDC al terminar");
    }

    @Test
    void theFieldsOfTheLogLeaveTheMdcWhenTheErrorIsAnswered() {
        given().header(TraceFilters.HEADER, TRACE_ID).get("/orders/42/shipping").then().statusCode(504);

        // El filtro de respuesta corre después del mapper, y de lo que este puso en el MDC ya no queda nada
        // más que el id de la petición, que es de quien lo abrió
        var mdc = TraceFilters.MDC_AFTER_ERROR.get();
        assertEquals(TRACE_ID, mdc.get("traceId"));
        for (String field : new String[] {"layer", "code", "status", "upstream"}) {
            assertFalse(mdc.containsKey(field), field + " se quedó en el MDC: " + mdc);
        }
    }

    private static Line onlyLine() {
        var lines = LOG.lines();
        assertEquals(1, lines.size(), "cada error se registra una sola vez: " + lines);
        return lines.get(0);
    }
}
