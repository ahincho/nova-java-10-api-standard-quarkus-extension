package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.net.URI;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.RedirectionException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Un caso por cada forma en que falla un recurso de Quarkus REST con una excepción del framework o una
 * cualquiera, sin pasar por un error de Nova.
 */
@Path("/items")
public class ItemResource {

    /** Un ítem por crear. */
    public record NewItem(String name, int quantity) {}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String list() {
        return "[]";
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public NewItem create(NewItem item) {
        return item;
    }

    @GET
    @Path("/{id}/price")
    public String price(@PathParam("id") long id) {
        throw new WebApplicationException("El servicio de precios de Acme no responde", 503);
    }

    @GET
    @Path("/{id}/gateway")
    public String gateway(@PathParam("id") long id) {
        throw new WebApplicationException("Acme respondió con basura", 502);
    }

    @GET
    @Path("/{id}/timeout")
    public String timeout(@PathParam("id") long id) {
        throw new WebApplicationException("Acme tardó 30 segundos", 504);
    }

    @GET
    @Path("/{id}/not-implemented")
    public String notImplemented(@PathParam("id") long id) {
        throw new WebApplicationException("Acme todavía no lo implementa", 501);
    }

    @GET
    @Path("/{id}/reservation")
    public String reservation(@PathParam("id") long id) {
        throw new WebApplicationException("El ítem ya está reservado", 409);
    }

    @GET
    @Path("/{id}/bad-request")
    public String badRequest(@PathParam("id") long id) {
        throw new BadRequestException("el campo interno xyz es inválido");
    }

    @GET
    @Path("/{id}/entity")
    public String entity(@PathParam("id") long id) {
        throw new WebApplicationException(
                Response.status(409).entity("{\"reason\":\"El ítem está en un pedido abierto\"}").build());
    }

    @GET
    @Path("/{id}/method")
    public String method(@PathParam("id") long id) {
        throw new NotAllowedException("GET", new String[] {"POST"});
    }

    @GET
    @Path("/{id}/retry")
    public String retry(@PathParam("id") long id) {
        throw new ServiceUnavailableException(120L);
    }

    @GET
    @Path("/{id}/moved")
    public String moved(@PathParam("id") long id) {
        throw new RedirectionException(Response.Status.SEE_OTHER, URI.create("/items/9/price"));
    }

    @GET
    @Path("/{id}/check")
    public String check(@PathParam("id") long id) {
        throw new IllegalArgumentException("El ítem no es válido");
    }

    @GET
    @Path("/{id}/secret")
    public String secret(@PathParam("id") long id) {
        throw new SecurityException("Acme rechazó la llave");
    }
}
