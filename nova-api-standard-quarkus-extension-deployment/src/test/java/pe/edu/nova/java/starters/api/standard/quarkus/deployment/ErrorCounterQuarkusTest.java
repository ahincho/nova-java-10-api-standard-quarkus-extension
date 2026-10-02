package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Con Micrometer, cada error respondido se cuenta en {@code nova.errors} con las etiquetas {@code layer} y
 * {@code code}, en el registro de métricas del servicio (ADR-031): el mismo contador del starter de Spring Boot.
 */
class ErrorCounterQuarkusTest {

    private static final SimpleMeterRegistry METERS = new SimpleMeterRegistry();

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(OrderResource.class));

    @BeforeAll
    static void collectTheMetrics() {
        Metrics.addRegistry(METERS);
    }

    @AfterAll
    static void stopCollecting() {
        Metrics.removeRegistry(METERS);
    }

    @Test
    void eachErrorIsCountedByLayerAndCode() {
        given().get("/orders/42");
        given().get("/orders/43");
        given().get("/orders/42/shipping");
        given().get("/nada");

        assertEquals(2, count("domain", "ORDER_NOT_FOUND"));
        assertEquals(1, count("infrastructure", "GATEWAY_TIMEOUT"));
        assertEquals(1, count("application", "NOT_FOUND"), "un error del framework también se cuenta");
    }

    private static double count(String layer, String code) {
        return METERS.counter("nova.errors", "layer", layer, "code", code).count();
    }
}
