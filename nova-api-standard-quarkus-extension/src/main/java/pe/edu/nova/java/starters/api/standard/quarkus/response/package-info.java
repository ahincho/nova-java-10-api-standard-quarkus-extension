/**
 * El sobre de éxito de Nova en Quarkus REST (ADR-050).
 * <p>
 * {@link pe.edu.nova.java.starters.api.standard.quarkus.response.ApiResponseFilter} envuelve en
 * {@link pe.edu.nova.java.libs.api.standard.response.ApiResponse} lo que un recurso JAX-RS devuelve como objeto,
 * con el status real de la respuesta, igual que el {@code ApiResponseInterceptor} del starter de Spring Boot. No
 * toca lo que ya es un sobre, lo que no es un cuerpo JSON ni lo que contesta el manejo de errores.
 */
package pe.edu.nova.java.starters.api.standard.quarkus.response;
