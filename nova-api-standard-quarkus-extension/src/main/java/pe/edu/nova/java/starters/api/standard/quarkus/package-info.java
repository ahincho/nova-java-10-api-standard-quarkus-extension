/**
 * Nova Platform API Standard Quarkus Extension.
 * <p>
 * Es una extensión de Quarkus con su módulo de deployment
 * ({@code nova-api-standard-quarkus-extension-deployment}). Este módulo, el runtime, conecta los
 * tipos de {@code nova-api-standard} (independientes del framework) con Quarkus:
 * <ul>
 *   <li>{@link pe.edu.nova.java.starters.api.standard.quarkus.error} responde cada error con el sobre de
 *       Nova y el modelo de errores por capas de ADR-031: los mappers del núcleo, los puertos que un
 *       servicio reemplaza con un bean, la fuente del {@code traceId} y el contador {@code nova.errors}.</li>
 *   <li>{@link pe.edu.nova.java.starters.api.standard.quarkus.jackson.ApiObjectMapperCustomizer}
 *       configura el {@code ObjectMapper} para serializar correctamente
 *       {@code java.time.*} y beans vacíos.</li>
 * </ul>
 * <p>
 * Los beans los registra el módulo de deployment al construir la aplicación, así que el servicio no
 * declara {@code quarkus.index-dependency}; el mismo módulo registra la fuente del {@code traceId} y los
 * records del sobre para la imagen nativa.
 */
package pe.edu.nova.java.starters.api.standard.quarkus;
