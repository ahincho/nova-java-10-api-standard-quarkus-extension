package pe.edu.nova.java.starters.api.standard.quarkus.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;

import io.vertx.ext.web.RoutingContext;

import jakarta.ws.rs.core.Response;

import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pe.edu.nova.java.libs.api.standard.error.ErrorPorts;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

/**
 * El reto de un 401 y lo que lo rodea, sin levantar Quarkus. El camino completo, con el {@code HttpAuthenticator}
 * que arma un mecanismo de autenticación de verdad, se prueba en el módulo de deployment.
 */
class SecurityExceptionMappersTest {

    private static final String CHALLENGE = "WWW-Authenticate";

    private final SecurityExceptionMappers mappers = new SecurityExceptionMappers(
            new ErrorResponder(ErrorPorts.defaults(), ErrorCounter.none()));

    private final UnauthorizedException exception = new UnauthorizedException("falta la identidad de Acme");

    @AfterEach
    void clearTheMdc() {
        MDC.clear();
    }

    @Test
    void theChallengeOfTheMechanismIsAddedToTheEnvelope() {
        Response response = mappers.answer(new ChallengeData(401, CHALLENGE, "Basic realm=\"nova\""), exception);

        assertEquals(401, response.getStatus());
        assertEquals("Basic realm=\"nova\"", response.getHeaderString(CHALLENGE));
        assertEquals("UNAUTHORIZED", code(response));
        assertEquals("Hace falta autenticarse", message(response));
    }

    @Test
    void everyHeaderOfTheChallengeIsKept() {
        Map<CharSequence, String> headers = new LinkedHashMap<>();
        headers.put(CHALLENGE, "Bearer realm=\"nova\"");
        headers.put("Cache-Control", "no-store");

        Response response = mappers.answer(new ChallengeData(401, headers), exception);

        assertEquals("Bearer realm=\"nova\"", response.getHeaderString(CHALLENGE));
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        assertEquals("UNAUTHORIZED", code(response));
    }

    @Test
    void aChallengeWithoutHeadersIsStillTheEnvelope() {
        Response response = mappers.answer(new ChallengeData(401), exception);

        assertEquals(401, response.getStatus());
        assertNull(response.getHeaderString(CHALLENGE));
        assertEquals("UNAUTHORIZED", code(response));
    }

    @Test
    void withoutAChallengeTheEnvelopeHasNoHeader() {
        Response response = mappers.answer(null, exception);

        assertEquals(401, response.getStatus());
        assertNull(response.getHeaderString(CHALLENGE));
        assertEquals("UNAUTHORIZED", code(response));
    }

    @Test
    void aRedirectionIsNotAnErrorAndKeepsItsWay() {
        Response response = mappers.answer(new ChallengeData(302, "Location", "/login"), exception);

        assertEquals(302, response.getStatus());
        assertEquals("/login", response.getHeaderString("Location"));
        assertNull(response.getEntity(), "una redirección no lleva el sobre de error");
    }

    @Test
    void theStatusIsThatOfTheExceptionWhateverTheChallengeSays() {
        Response response = mappers.answer(new ChallengeData(499, CHALLENGE, "OIDC"), exception);

        assertEquals(401, response.getStatus());
        assertEquals("OIDC", response.getHeaderString(CHALLENGE));
        assertEquals("UNAUTHORIZED", code(response));
    }

    @Test
    void theChallengeOfAServiceWithoutAMechanismLeavesTheUnauthorizedWithoutAHeader() {
        // Sin mecanismo de autenticación Quarkus contesta un 403 sin headers; aquí sigue siendo el 401 de siempre
        Response response = mappers.answer(new ChallengeData(403), exception);

        assertEquals(401, response.getStatus());
        assertNull(response.getHeaderString(CHALLENGE));
        assertEquals("UNAUTHORIZED", code(response));
    }

    @Test
    void withoutAnAuthenticatorAnUnauthorizedIdentityHasNoChallenge() {
        Response response = mappers.unauthorized(routing(new HashMap<>()), exception).await().indefinitely();

        assertEquals(401, response.getStatus());
        assertNull(response.getHeaderString(CHALLENGE));
        assertEquals("UNAUTHORIZED", code(response));
    }

    @Test
    void withoutAnAuthenticatorAnAuthenticationFailureHasNoChallenge() {
        Response response = mappers.authenticationFailed(routing(new HashMap<>()),
                new AuthenticationFailedException("el token de Acme venció")).await().indefinitely();

        assertEquals(401, response.getStatus());
        assertNull(response.getHeaderString(CHALLENGE));
        assertEquals("UNAUTHORIZED", code(response));
    }

    @Test
    void anAuthenticationFailureIsLeftInTheContextForTheSecurityEvent() {
        Map<String, Object> data = new HashMap<>();
        AuthenticationFailedException failure = new AuthenticationFailedException("el token de Acme venció");

        mappers.authenticationFailed(routing(data), failure).await().indefinitely();

        assertSame(failure, HttpSecurityUtils.getAuthenticationFailureFromEvent(routing(data)));
    }

    @Test
    void anIdentityWithoutPermissionIsForbiddenWithoutAChallenge() {
        Response response = mappers.forbidden(new ForbiddenException("el rol de Acme no alcanza"));

        assertEquals(403, response.getStatus());
        assertNull(response.getHeaderString(CHALLENGE));
        assertEquals("FORBIDDEN", code(response));
    }

    /** Un contexto de petición mínimo: guarda y entrega los valores que Quarkus deja en él. */
    private static RoutingContext routing(Map<String, Object> data) {
        return (RoutingContext) Proxy.newProxyInstance(
                RoutingContext.class.getClassLoader(), new Class<?>[] {RoutingContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "get" -> data.get(args[0]);
                    case "put" -> {
                        data.put((String) args[0], args[1]);
                        yield proxy;
                    }
                    case "toString" -> "RoutingContext de prueba " + data.keySet();
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static String code(Response response) {
        return ((ApiResponse<?>) response.getEntity()).errors().get(0).code();
    }

    private static String message(Response response) {
        return ((ApiResponse<?>) response.getEntity()).errors().get(0).message();
    }
}
