package pe.edu.nova.java.starters.api.standard.quarkus.error;

/**
 * Cuenta cada error respondido, con su capa y su código (ADR-031). Con Micrometer es el contador
 * {@code nova.errors}; sin un registro de métricas, no cuenta nada.
 * <p>
 * Un servicio lo reemplaza declarando su propio bean {@code ErrorCounter}: el de Nova es
 * {@code @DefaultBean}.
 */
@FunctionalInterface
public interface ErrorCounter {

    /**
     * Cuenta un error.
     *
     * @param layer la capa, como {@code infrastructure}
     * @param code  el código que vio el cliente, como {@code GATEWAY_TIMEOUT}
     */
    void increment(String layer, String code);

    /**
     * Un contador que no cuenta nada, para un servicio sin métricas.
     *
     * @return el contador
     */
    static ErrorCounter none() {
        return (layer, code) -> {
        };
    }
}
