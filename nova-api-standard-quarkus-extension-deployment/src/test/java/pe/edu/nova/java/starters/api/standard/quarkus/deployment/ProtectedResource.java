package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import io.quarkus.security.Authenticated;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Rutas protegidas por anotaciones, como las de un servicio que tiene un mecanismo de autenticación. */
@Path("/protected")
public class ProtectedResource {

    @GET
    @Path("/orders")
    @Authenticated
    public String orders() {
        return "pedidos";
    }

    @GET
    @Path("/admin")
    @RolesAllowed("admin")
    public String admin() {
        return "administración";
    }
}
