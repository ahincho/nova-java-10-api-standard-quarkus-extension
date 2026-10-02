package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.UnauthorizedException;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Las excepciones de Quarkus Security que llegan a REST, lanzadas como lo hace la seguridad por anotaciones. */
@Path("/secure")
public class SecurityResource {

    @GET
    @Path("/unauthorized")
    public String unauthorized() {
        throw new UnauthorizedException("falta la identidad de Acme");
    }

    @GET
    @Path("/authentication-failed")
    public String authenticationFailed() {
        throw new AuthenticationFailedException("el token de Acme venció");
    }

    @GET
    @Path("/forbidden")
    public String forbidden() {
        throw new io.quarkus.security.ForbiddenException("el rol de Acme no alcanza");
    }
}
