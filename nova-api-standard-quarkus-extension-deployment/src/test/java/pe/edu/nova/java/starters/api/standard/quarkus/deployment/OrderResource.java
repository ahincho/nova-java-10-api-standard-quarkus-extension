package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.api.standard.error.DomainError;
import pe.edu.nova.java.libs.api.standard.error.FieldError;
import pe.edu.nova.java.libs.api.standard.error.InfrastructureError;

/** Un recurso que lanza un error de Nova de cada capa, como lo haría un caso de uso. */
@Path("/orders")
public class OrderResource {

    /** El proveedor que falla en las pruebas: nunca tiene que llegar al cuerpo. */
    public static final String UPSTREAM = "courier-acme";

    @GET
    @Path("/{id}")
    public String find(@PathParam("id") long id) {
        throw DomainError.notFound("ORDER_NOT_FOUND", "El pedido " + id + " no existe");
    }

    @POST
    @Path("/{id}/confirmations")
    public String confirm(@PathParam("id") long id) {
        throw DomainError.conflict("El pedido está cancelado");
    }

    @POST
    public String place() {
        throw ApplicationError.invalidInput("La solicitud tiene campos inválidos", List.of(
                FieldError.of("email", "El correo no es válido"),
                FieldError.of("quantity", "La cantidad debe ser positiva")));
    }

    @POST
    @Path("/{id}/payments")
    public String pay(@PathParam("id") long id) {
        throw ApplicationError.conflict("La operación sigue en curso", Duration.ofSeconds(1));
    }

    @GET
    @Path("/export")
    public String export() {
        throw ApplicationError.rateLimited("Superaste el límite", Duration.ofSeconds(30));
    }

    @GET
    @Path("/{id}/shipping")
    public String shipping(@PathParam("id") long id) {
        throw InfrastructureError.timeout(UPSTREAM, new SocketTimeoutException(UPSTREAM + ".interno:8443"));
    }

    @GET
    @Path("/{id}/stock")
    public String stock(@PathParam("id") long id) {
        throw InfrastructureError.unavailable("inventario", null);
    }

    @GET
    @Path("/{id}/audit")
    public String audit(@PathParam("id") long id) {
        throw new IllegalStateException("La auditoría de Acme falló");
    }
}
