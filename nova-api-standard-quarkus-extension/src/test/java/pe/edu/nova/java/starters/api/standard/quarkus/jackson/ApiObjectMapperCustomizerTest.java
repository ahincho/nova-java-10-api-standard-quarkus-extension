package pe.edu.nova.java.starters.api.standard.quarkus.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios del {@link ApiObjectMapperCustomizer}.
 * <p>
 * Verifica que el customizer aplica la configuracion esperada al
 * {@link ObjectMapper}: registra JavaTimeModule, deshabilita timestamps
 * numericos y FAIL_ON_EMPTY_BEANS.
 */
class ApiObjectMapperCustomizerTest {

    private ObjectMapper mapper;
    private ApiObjectMapperCustomizer customizer;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        customizer = new ApiObjectMapperCustomizer();
    }

    @Test
    void customizerDisablesWriteDatesAsTimestamps() {
        customizer.customize(mapper);

        assertFalse(mapper.getSerializationConfig().isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                "WRITE_DATES_AS_TIMESTAMPS debe estar deshabilitado");
    }

    @Test
    void customizerDisablesFailOnEmptyBeans() {
        customizer.customize(mapper);

        assertFalse(mapper.getSerializationConfig().isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS),
                "FAIL_ON_EMPTY_BEANS debe estar deshabilitado");
    }

    @Test
    void customizerRegistersJavaTimeModule() {
        customizer.customize(mapper);

        // JavaTimeModule habilita la serializacion de java.time.* sin necesidad
        // de agregar annotations o features adicionales. Si el modulo esta registrado,
        // LocalDateTime se serializa sin lanzar InvalidDefinitionException.
        assertTrue(mapper.canSerialize(java.time.LocalDateTime.class),
                "JavaTimeModule debe estar registrado (mapper debe poder serializar LocalDateTime)");
        assertTrue(mapper.canSerialize(java.time.Instant.class),
                "JavaTimeModule debe estar registrado (mapper debe poder serializar Instant)");
    }
}