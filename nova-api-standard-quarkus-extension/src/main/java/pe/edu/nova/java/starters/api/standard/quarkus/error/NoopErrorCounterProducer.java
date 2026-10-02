package pe.edu.nova.java.starters.api.standard.quarkus.error;

import io.quarkus.arc.DefaultBean;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Un contador que no cuenta, para un servicio sin Micrometer.
 * <p>
 * El módulo de deployment lo registra solo si Micrometer no es el sistema de métricas del servicio; si lo es,
 * registra {@link MicrometerErrorCounterProducer} en su lugar, y así la extensión no arrastra Micrometer ni
 * deja dos {@code @DefaultBean} del mismo tipo.
 */
@Singleton
public class NoopErrorCounterProducer {

    /** Lo crea Quarkus. */
    public NoopErrorCounterProducer() {
    }

    /**
     * El contador vacío.
     *
     * @return el contador
     */
    @Produces
    @DefaultBean
    @Singleton
    public ErrorCounter novaErrorCounter() {
        return ErrorCounter.none();
    }
}
