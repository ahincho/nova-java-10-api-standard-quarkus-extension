/**
 * Nova Platform API Standard Quarkus Extension.
 * <p>
 * Es una extensión de Quarkus con su módulo de deployment
 * ({@code nova-api-standard-quarkus-extension-deployment}). Este módulo, el runtime, conecta los
 * tipos de {@code nova-api-standard} (independientes del framework) con Quarkus:
 * <ul>
 *   <li>{@link pe.edu.nova.java.starters.api.standard.quarkus.mapper.ApiExceptionMapper}
 *       mapea excepciones no controladas a {@code ApiResponse} JSON.</li>
 *   <li>{@link pe.edu.nova.java.starters.api.standard.quarkus.jackson.ApiObjectMapperCustomizer}
 *       configura el {@code ObjectMapper} para serializar correctamente
 *       {@code java.time.*} y records vacíos.</li>
 * </ul>
 * <p>
 * Los beans los registra el módulo de deployment al construir la aplicación, así que el servicio no
 * declara {@code quarkus.index-dependency}; el mismo módulo registra los records del sobre para la
 * imagen nativa.
 */
package pe.edu.nova.java.starters.api.standard.quarkus;
