package pe.edu.nova.java.starters.api.standard.quarkus.error;

import java.util.Objects;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Cuenta cada error en {@code nova.errors}, con las etiquetas {@code layer} y {@code code}, en un
 * {@link MeterRegistry}. Es el mismo nombre y la misma forma que el contador del starter de Spring Boot, así
 * que un tablero agrupa igual los tres stacks.
 */
public final class MicrometerErrorCounter implements ErrorCounter {

    /** El nombre del contador. */
    public static final String METER_NAME = "nova.errors";

    private final MeterRegistry registry;

    /**
     * Crea el contador.
     *
     * @param registry el registro de métricas donde se cuenta
     */
    public MicrometerErrorCounter(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry es obligatorio");
    }

    @Override
    public void increment(String layer, String code) {
        registry.counter(METER_NAME, "layer", layer, "code", code).increment();
    }
}
