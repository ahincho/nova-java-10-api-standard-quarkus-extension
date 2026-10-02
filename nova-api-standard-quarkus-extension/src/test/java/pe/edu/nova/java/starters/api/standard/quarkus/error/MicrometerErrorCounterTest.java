package pe.edu.nova.java.starters.api.standard.quarkus.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

/** El contador {@code nova.errors} lleva las etiquetas {@code layer} y {@code code}, como el del starter de Spring. */
class MicrometerErrorCounterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ErrorCounter counter = new MicrometerErrorCounter(registry);

    @Test
    void eachErrorCountsInItsOwnLayerAndCode() {
        counter.increment("domain", "ORDER_NOT_FOUND");
        counter.increment("domain", "ORDER_NOT_FOUND");
        counter.increment("infrastructure", "GATEWAY_TIMEOUT");

        assertEquals(2, registry.get("nova.errors").tags("layer", "domain", "code", "ORDER_NOT_FOUND").counter().count());
        assertEquals(1, registry.get("nova.errors").tags("layer", "infrastructure", "code", "GATEWAY_TIMEOUT").counter().count());
    }

    @Test
    void theNameIsTheSameInTheThreeStacks() {
        assertEquals("nova.errors", MicrometerErrorCounter.METER_NAME);
    }

    @Test
    void theEmptyCounterCountsNothingAndDoesNotFail() {
        ErrorCounter.none().increment("platform", "INTERNAL_SERVER_ERROR");
    }

    @Test
    void itNeedsARegistry() {
        assertThrows(NullPointerException.class, () -> new MicrometerErrorCounter(null));
    }
}
