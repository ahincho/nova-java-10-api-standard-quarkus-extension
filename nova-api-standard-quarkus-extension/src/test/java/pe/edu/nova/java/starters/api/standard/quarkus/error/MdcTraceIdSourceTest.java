package pe.edu.nova.java.starters.api.standard.quarkus.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** La fuente del {@code traceId} lee la clave {@code traceId} del MDC de JBoss Logging, como el starter de Spring. */
class MdcTraceIdSourceTest {

    private final MdcTraceIdSource source = new MdcTraceIdSource();

    @AfterEach
    void clearTheMdc() {
        MDC.clear();
    }

    @Test
    void theTraceIdOfTheMdcIsTheOneOfTheRequest() {
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

        assertEquals(Optional.of("4bf92f3577b34da6a3ce929d0e0e4736"), source.currentTraceId());
    }

    @Test
    void withoutATraceIdThereIsNothing() {
        assertTrue(source.currentTraceId().isEmpty());
    }

    @Test
    void aBlankTraceIdIsNothing() {
        MDC.put("traceId", "  ");

        assertTrue(source.currentTraceId().isEmpty());
    }

    @Test
    void aValueThatIsNotATextIsReadAsText() {
        MDC.put("traceId", 42);

        assertEquals(Optional.of("42"), source.currentTraceId());
    }

    @Test
    void itIsTheKeyThatTheStarterOfSpringReads() {
        assertEquals("traceId", MdcTraceIdSource.TRACE_ID_KEY);
    }
}
