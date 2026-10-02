package pe.edu.nova.java.starters.api.standard.quarkus.error;

import java.util.List;
import java.util.Objects;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticator;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;

import io.smallrye.mutiny.Uni;

import io.vertx.ext.web.RoutingContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
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
 * <strong>Un 401 lleva el reto del mecanismo de autenticación</strong>, el header {@code WWW-Authenticate} que
 * exige RFC 9110, sección 15.5.2. Estos mappers reemplazan a los de Quarkus, que lo añadían, así que hacen lo
 * mismo: le piden el reto al {@link HttpAuthenticator} que Quarkus guarda en el contexto de la petición y
 * suman sus headers a la respuesta, cuyo cuerpo sigue siendo el sobre de Nova. El status es el de la excepción,
 * 401, diga lo que diga el reto: de él se toman los headers. Si el reto falla o no los tiene, la respuesta
 * sale sin el header; es lo que pasa en un servicio sin mecanismo de autenticación, donde Quarkus contesta un
 * 403 sin headers y aquí sigue siendo el 401 de siempre. Un reto que no es un error, como la redirección a la
 * página de inicio de sesión de un formulario, sigue su camino como en Quarkus.
 * <p>
 * Solo cubre lo que llega a REST. La autenticación que Quarkus rechaza antes, en la capa HTTP, no pasa por
 * ningún mapper (ADR-050, pregunta abierta 3).
 * <p>
 * Esta clase nombra {@code io.quarkus.security}, así que el módulo de deployment la registra solo si el
 * servicio tiene la capacidad {@code io.quarkus.security}.
 */
@Singleton
public class SecurityExceptionMappers {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    /** Desde este status un reto es un error; por debajo es una redirección. */
    private static final int FIRST_ERROR_STATUS = 400;

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
     * Responde una identidad que falta (401), con el reto del mecanismo de autenticación.
     *
     * @param routing   el contexto de la petición, donde Quarkus guarda el autenticador
     * @param exception la excepción
     * @return la respuesta que deciden los puertos, con los headers del reto
     */
    @ServerExceptionMapper
    public Uni<Response> unauthorized(RoutingContext routing, UnauthorizedException exception) {
        return challenged(routing, exception);
    }

    /**
     * Responde una identidad que no se pudo autenticar (401), con el reto del mecanismo de autenticación.
     *
     * @param routing   el contexto de la petición, donde Quarkus guarda el autenticador
     * @param exception la excepción
     * @return la respuesta que deciden los puertos, con los headers del reto
     */
    @ServerExceptionMapper
    public Uni<Response> authenticationFailed(RoutingContext routing, AuthenticationFailedException exception) {
        // Igual que el mapper de Quarkus que reemplaza: deja el fallo en el contexto, de donde lo toma el evento
        // de seguridad de la autenticación fallida
        HttpSecurityUtils.addAuthenticationFailureToEvent(exception, routing);
        return challenged(routing, exception);
    }

    /**
     * Responde una identidad sin permiso (403). No lleva reto: la identidad ya se autenticó.
     *
     * @param exception la excepción
     * @return la respuesta que deciden los puertos
     */
    @ServerExceptionMapper
    public Response forbidden(ForbiddenException exception) {
        return responder.respondFramework(FORBIDDEN, null, List.of(), null, exception);
    }

    /**
     * Le pide el reto al autenticador de la petición, que Quarkus guarda en su contexto con el nombre de su clase,
     * igual que lo hace su mapper. Sin autenticador, o con un reto que falla, no hay header que añadir.
     */
    private Uni<Response> challenged(RoutingContext routing, Throwable exception) {
        HttpAuthenticator authenticator = routing.get(HttpAuthenticator.class.getName());
        if (authenticator == null) {
            return Uni.createFrom().item(() -> answer(null, exception));
        }
        return authenticator.getChallenge(routing)
                .onFailure().recoverWithNull()
                .map(challenge -> answer(challenge, exception));
    }

    /**
     * La respuesta a un 401 según el reto del mecanismo.
     *
     * @param challenge el reto, o {@code null} si no hay mecanismo de autenticación que lo dé
     * @param exception la excepción, para el log
     * @return el sobre de Nova con status 401 y los headers del reto; sin reto, o con uno sin headers, un 401
     *         sin ellos; si el reto es una redirección, esa redirección
     */
    Response answer(ChallengeData challenge, Throwable exception) {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        if (challenge != null) {
            challenge.getHeaders().forEach((name, value) -> headers.add(name.toString(), value));
            if (challenge.status < FIRST_ERROR_STATUS) {
                // Una redirección, como la de la página de inicio de sesión de un formulario: no es un error, y
                // convertirla en un 401 rompería el inicio de sesión que Quarkus resolvía con ella
                return Response.status(challenge.status).replaceAll(headers).build();
            }
        }
        return responder.respondFramework(UNAUTHORIZED, null, List.of(), headers, exception);
    }
}
