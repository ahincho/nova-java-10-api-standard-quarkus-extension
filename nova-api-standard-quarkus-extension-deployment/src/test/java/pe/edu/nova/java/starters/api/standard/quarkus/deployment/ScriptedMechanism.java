package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;

import io.smallrye.mutiny.Uni;

import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Un mecanismo de autenticación que no autentica a nadie y cuyo reto lo pide la petición con el header
 * {@value #SCRIPT}: cada prueba elige el reto que necesita sin armar un mecanismo por caso.
 */
@ApplicationScoped
public class ScriptedMechanism implements HttpAuthenticationMechanism {

    /** El header con el que la petición elige el reto. */
    public static final String SCRIPT = "X-Challenge";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        String script = context.request().getHeader(SCRIPT);
        if ("redirect".equals(script)) {
            return Uni.createFrom().item(new ChallengeData(302, "Location", "/login"));
        }
        if ("failure".equals(script)) {
            return Uni.createFrom().failure(new IllegalStateException("el proveedor de identidad de Acme no responde"));
        }
        if ("several".equals(script)) {
            Map<CharSequence, String> headers = new LinkedHashMap<>();
            headers.put("WWW-Authenticate", "Bearer realm=\"nova\"");
            headers.put("Cache-Control", "no-store");
            return Uni.createFrom().item(new ChallengeData(401, headers));
        }
        if ("none".equals(script)) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Scripted realm=\"nova\""));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "scripted"));
    }
}
