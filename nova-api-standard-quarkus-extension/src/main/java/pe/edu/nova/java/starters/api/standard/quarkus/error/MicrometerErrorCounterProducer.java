package pe.edu.nova.java.starters.api.standard.quarkus.error;

import io.micrometer.core.instrument.MeterRegistry;

import io.quarkus.arc.DefaultBean;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * El contador {@code nova.errors} de Micrometer.
 * <p>
 * Esta clase nombra {@link MeterRegistry}, así que el módulo de deployment la registra solo si Micrometer es el
 * sistema de métricas del servicio; si no, nadie la carga y la extensión no necesita Micrometer en el
 * classpath.
 */
@Singleton
public class MicrometerErrorCounterProducer {

    /** Lo crea Quarkus. */
    public MicrometerErrorCounterProducer() {
    }

    /**
     * Cuenta cada error en {@code nova.errors}, con las etiquetas {@code layer} y {@code code}, en el
     * registro de métricas del servicio.
     *
     * @param registry el registro de métricas de Quarkus
     * @return el contador
     */
    @Produces
    @DefaultBean
    @Singleton
    public ErrorCounter novaErrorCounter(MeterRegistry registry) {
        return new MicrometerErrorCounter(registry);
    }
}
