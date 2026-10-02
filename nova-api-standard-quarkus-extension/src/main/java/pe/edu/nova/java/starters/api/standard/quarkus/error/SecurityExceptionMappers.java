package pe.edu.nova.java.starters.api.standard.quarkus.error;

import java.util.List;
import java.util.Objects;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Las excepciones de Quarkus Security que llegan a REST (ADR-050).
 * <p>
 * No extienden {@code WebApplicationException}, pero tienen un status inequívoco y se leen igual: una
 * identidad que falta o no es válida es un 401 ({@link UnauthorizedException} y
 * {@link AuthenticationFailedException}) y una identidad sin permiso es un 403 ({@link ForbiddenException}),
 * las dos de la capa {@code application}. Una {@link SecurityException} del JDK no es del framework: no dice
 * si falta la identidad o el permiso, y sale como 500 por el núcleo.
 * <p>
 * Solo cubre lo que llega a REST. La autenticación que Quarkus rechaza antes, en la capa HTTP, no pasa por
 * ningún mapper (ADR-050, pregunta abierta 3).
 * <p>
 * Esta clase nombra {@code io.quarkus.security}, así que el módulo de deployment la registra solo si el
 * servicio tiene la capacidad {@code io.quarkus.security}.
 */
@Singleton
public class SecurityExceptionMappers {

    private final ErrorResponder responder;

    /**
     * Crea los mappers.
     *
     * @param responder el núcleo que registra y responde
     */
    @Inject
    public SecurityExceptionMappers(ErrorResponder responder) {
        this.responder = Objects.requireNonNull(responder, "responder es obligatorio");
    }

    /**
     * Responde una identidad que falta (401).
     *
     * @param exception la excepción
     * @return la respuesta que deciden los puertos
     */
    @ServerExceptionMapper
    public Response unauthorized(UnauthorizedException exception) {
        return responder.respondFramework(401, null, List.of(), null, exception);
    }

    /**
     * Responde una identidad que no se pudo autenticar (401).
     *
     * @param exception la excepción
     * @return la respuesta que deciden los puertos
     */
    @ServerExceptionMapper
    public Response authenticationFailed(AuthenticationFailedException exception) {
        return responder.respondFramework(401, null, List.of(), null, exception);
    }

    /**
     * Responde una identidad sin permiso (403).
     *
     * @param exception la excepción
     * @return la respuesta que deciden los puertos
     */
    @ServerExceptionMapper
    public Response forbidden(ForbiddenException exception) {
        return responder.respondFramework(403, null, List.of(), null, exception);
    }
}
