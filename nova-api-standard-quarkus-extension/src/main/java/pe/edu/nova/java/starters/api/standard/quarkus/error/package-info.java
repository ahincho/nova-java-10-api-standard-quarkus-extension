/**
 * El manejo de errores por capas de ADR-031 en Quarkus REST (ADR-050).
 * <p>
 * {@link pe.edu.nova.java.starters.api.standard.quarkus.error.NovaExceptionMappers} lee cada excepción por
 * lo que es, y {@link pe.edu.nova.java.starters.api.standard.quarkus.error.ErrorResponder} la registra una sola
 * vez, la cuenta y la responde con los tres puertos de {@code nova-api-standard}: el status, el catálogo de
 * códigos y el serializador. Los puertos los produce
 * {@link pe.edu.nova.java.starters.api.standard.quarkus.error.ErrorPortProducers} como {@code @DefaultBean}, así
 * que un servicio o la extensión de una organización los reemplaza con un bean propio.
 * <p>
 * El {@code traceId} lo da {@link pe.edu.nova.java.starters.api.standard.quarkus.error.MdcTraceIdSource}, y el
 * contador {@link pe.edu.nova.java.starters.api.standard.quarkus.error.ErrorCounter} es el de Micrometer si
 * el servicio lo tiene.
 */
package pe.edu.nova.java.starters.api.standard.quarkus.error;
