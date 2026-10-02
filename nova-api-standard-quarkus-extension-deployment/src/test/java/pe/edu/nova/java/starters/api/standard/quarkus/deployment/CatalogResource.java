package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.fasterxml.jackson.annotation.JsonView;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestResponse;

import pe.edu.nova.java.libs.api.standard.metadata.ApiMetadata;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

/**
 * El recurso del servicio de las pruebas del sobre de éxito: el equivalente en JAX-RS del {@code ItemController} de
 * las pruebas del starter de Spring Boot, con un caso por cada forma en que contesta un recurso real: con su propio
 * status, sin cuerpo, con un cuerpo que no es JSON, con un sobre armado a mano, con una excepción o en un flujo.
 */
@Path("/catalog")
public class CatalogResource {

    /** El único producto que existe. */
    static final Product TABLE = new Product(1, "Mesa", 4);

    /** El reporte binario de un producto. */
    static final byte[] REPORT = {0x4E, 0x6F, 0x76, 0x61, 0x00, (byte) 0xFF};

    /** El manual de un producto, servido como flujo. */
    static final byte[] MANUAL = "%PDF-1.7 manual de la mesa".getBytes(StandardCharsets.UTF_8);

    /** El instante fijo de los sobres armados a mano, para comparar sus bytes. */
    static final Instant MOMENT = Instant.parse("2026-07-14T12:34:56Z");

    /**
     * Un producto.
     *
     * @param id       el identificador
     * @param name     el nombre
     * @param quantity las unidades
     */
    public record Product(long id, String name, int quantity) {}

    /**
     * Un producto por crear.
     *
     * @param name     el nombre
     * @param quantity las unidades
     */
    public record NewProduct(String name, int quantity) {}

    /** Un objeto sin propiedades. */
    public record Nothing() {}

    /** Los casos que contesta bien: 200 con un cuerpo. */
    @GET
    public List<Product> list() {
        return List.of(TABLE);
    }

    /** Crea un producto y contesta 201 con {@code Response}, como un {@code ResponseEntity} de Spring. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(NewProduct product) {
        return Response.status(Response.Status.CREATED)
                .entity(new Product(2, product.name(), product.quantity()))
                .build();
    }

    /** Crea un producto y contesta 201 con {@code RestResponse}, el tipo propio de Quarkus REST. */
    @POST
    @Path("/typed")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestResponse<Product> createTyped(NewProduct product) {
        return RestResponse.status(RestResponse.Status.CREATED, new Product(2, product.name(), product.quantity()));
    }

    /** Copia un producto y contesta 201 con {@code @ResponseStatus}. */
    @POST
    @Path("/{id}/copies")
    @ResponseStatus(201)
    public Product copy(@PathParam("id") long id) {
        return new Product(3, TABLE.name(), TABLE.quantity());
    }

    /** Busca un producto: 404 sin cuerpo si no existe. */
    @GET
    @Path("/{id}")
    public Response find(@PathParam("id") long id) {
        return id == TABLE.id() ? Response.ok(TABLE).build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    /** Busca un producto con {@code Uni}. */
    @GET
    @Path("/{id}/reactive")
    public Uni<Product> reactive(@PathParam("id") long id) {
        return Uni.createFrom().item(TABLE);
    }

    /** Busca un producto con {@code CompletionStage}. */
    @GET
    @Path("/{id}/stage")
    public CompletionStage<Product> stage(@PathParam("id") long id) {
        return CompletableFuture.completedFuture(TABLE);
    }

    /** Un mapa, que sale como objeto JSON. */
    @GET
    @Path("/{id}/attributes")
    public Map<String, Object> attributes(@PathParam("id") long id) {
        return Map.of("color", "roble");
    }

    /** Un objeto sin propiedades, que no debe fallar. */
    @GET
    @Path("/{id}/nothing")
    public Nothing nothing(@PathParam("id") long id) {
        return new Nothing();
    }

    /** Un número suelto sin declarar JSON: Quarkus REST lo escribe como texto. */
    @GET
    @Path("/count")
    public Integer count() {
        return 42;
    }

    /** Un número suelto que declara JSON. */
    @GET
    @Path("/count-json")
    @Produces(MediaType.APPLICATION_JSON)
    public Integer countAsJson() {
        return 42;
    }

    /** Un tipo de contenido propio de un servicio, con el sufijo de JSON. */
    @GET
    @Path("/{id}/vendor")
    @Produces("application/vnd.nova.product+json")
    public Product vendor(@PathParam("id") long id) {
        return TABLE;
    }

    /** Un sobre armado a mano con {@code ApiResponse.ok}, como los ejemplos 04 y 06. */
    @GET
    @Path("/envelope/ok")
    public ApiResponse<Product> handBuiltOk() {
        return ApiResponse.ok(TABLE);
    }

    /** Un sobre armado a mano con el status 201. */
    @GET
    @Path("/envelope/created")
    public ApiResponse<Product> handBuiltCreated() {
        return ApiResponse.created(TABLE);
    }

    /** Un sobre armado a mano con su propia metadata. */
    @GET
    @Path("/envelope/described")
    @Produces(MediaType.APPLICATION_JSON)
    public ApiResponse<Product> handBuiltWithMetadata() {
        return ApiResponse.<Product>builder()
                .data(TABLE)
                .status(200)
                .metadata(ApiMetadata.builder().timestamp(MOMENT).traceId("trace-1").apiVersion("v1").build())
                .build();
    }

    /** Un sobre de error armado a mano, contestado sin lanzar una excepción. */
    @GET
    @Path("/envelope/failed")
    public Response handBuiltError() {
        return Response.status(409).entity(ApiResponse.error(409, "El producto está en un pedido abierto")).build();
    }

    /** Confirma un producto: 200 sin cuerpo, como {@code ResponseEntity.ok().build()}. */
    @POST
    @Path("/{id}/confirmations")
    public Response confirm(@PathParam("id") long id) {
        return Response.ok().build();
    }

    /** Encola un producto: 202 sin cuerpo. */
    @POST
    @Path("/{id}/queue")
    public Response enqueue(@PathParam("id") long id) {
        return Response.accepted().build();
    }

    /** Registra un producto: 201 con {@code Location} y sin cuerpo. */
    @POST
    @Path("/registrations")
    public Response register() {
        return Response.created(URI.create("/catalog/1")).build();
    }

    /** Borra un producto: 204 con {@code Response}. */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") long id) {
        return Response.noContent().build();
    }

    /** Borra un producto: 204 con un método {@code void}. */
    @DELETE
    @Path("/{id}/void")
    public void deleteQuietly(@PathParam("id") long id) {
        // Un método void contesta 204
    }

    /** Un recurso que devuelve {@code null}: JAX-RS lo contesta con 204. */
    @GET
    @Path("/{id}/missing")
    public Product missing(@PathParam("id") long id) {
        return null;
    }

    /** Un 205: el cliente reinicia su vista y no hay cuerpo que enviar. */
    @POST
    @Path("/{id}/resets")
    public Response reset(@PathParam("id") long id) {
        return Response.status(205).build();
    }

    /** Un 206 con un cuerpo parcial. */
    @GET
    @Path("/{id}/range")
    public Response range(@PathParam("id") long id) {
        return Response.status(206).entity(TABLE).build();
    }

    /** Una redirección. */
    @GET
    @Path("/{id}/redirection")
    public Response redirect(@PathParam("id") long id) {
        return Response.seeOther(URI.create("/catalog/1")).build();
    }

    /** Un 304 sin cuerpo. */
    @GET
    @Path("/{id}/validation")
    public Response notModified(@PathParam("id") long id) {
        return Response.notModified().build();
    }

    /** Reemplaza un producto: 409 con un cuerpo propio del recurso. */
    @PUT
    @Path("/{id}")
    public Response replace(@PathParam("id") long id) {
        return Response.status(409).entity(Map.of("reason", "El producto está en un pedido abierto")).build();
    }

    /** Consulta el stock: 503 sin cuerpo. */
    @GET
    @Path("/{id}/stock")
    public Response stock(@PathParam("id") long id) {
        return Response.status(503).build();
    }

    /** Un 400 con un texto plano. */
    @GET
    @Path("/{id}/complaint")
    public Response complaint(@PathParam("id") long id) {
        return Response.status(400).entity("texto plano").type(MediaType.TEXT_PLAIN).build();
    }

    /** Descarga el reporte como {@code byte[]}. */
    @GET
    @Path("/{id}/report")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] report(@PathParam("id") long id) {
        return REPORT;
    }

    /** Descarga el reporte como {@code byte[]} sin declarar el tipo de contenido. */
    @GET
    @Path("/{id}/raw")
    public byte[] raw(@PathParam("id") long id) {
        return REPORT;
    }

    /** Descarga el manual como flujo de entrada. */
    @GET
    @Path("/{id}/manual")
    @Produces("application/pdf")
    public InputStream manual(@PathParam("id") long id) {
        return new ByteArrayInputStream(MANUAL);
    }

    /** Descarga el manual con {@code StreamingOutput}. */
    @GET
    @Path("/{id}/manual-stream")
    @Produces("application/pdf")
    public StreamingOutput manualStream(@PathParam("id") long id) {
        return output -> output.write(MANUAL);
    }

    /** Devuelve el nombre como texto. */
    @GET
    @Path("/{id}/name")
    @Produces(MediaType.TEXT_PLAIN)
    public String name(@PathParam("id") long id) {
        return TABLE.name();
    }

    /** Devuelve el nombre como texto sin declarar el tipo de contenido. */
    @GET
    @Path("/{id}/label")
    public String label(@PathParam("id") long id) {
        return TABLE.name();
    }

    /** Devuelve un JSON ya escrito, que Quarkus REST entrega sin tocar. */
    @GET
    @Path("/{id}/json")
    @Produces(MediaType.APPLICATION_JSON)
    public String json(@PathParam("id") long id) {
        return "{\"raw\":true}";
    }

    /** Devuelve un CSV. */
    @GET
    @Path("/csv")
    @Produces("text/csv")
    public String csv() {
        return "id,name\n1,Mesa\n";
    }

    /** Un objeto que el recurso declara como texto: sale con el {@code toString()} de Quarkus REST. */
    @GET
    @Path("/{id}/text")
    @Produces(MediaType.TEXT_PLAIN)
    public Product text(@PathParam("id") long id) {
        return TABLE;
    }

    /** Un flujo de objetos como un arreglo JSON. */
    @GET
    @Path("/stream/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<Product> streamAsArray() {
        return Multi.createFrom().items(TABLE, new Product(2, "Silla", 2));
    }

    /** Un flujo de objetos en NDJSON. */
    @GET
    @Path("/stream/ndjson")
    @Produces("application/x-ndjson")
    public Multi<Product> streamAsNdjson() {
        return Multi.createFrom().items(TABLE, new Product(2, "Silla", 2));
    }

    /** Un flujo de objetos como eventos SSE. */
    @GET
    @Path("/stream/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<Product> streamAsEvents() {
        return Multi.createFrom().items(TABLE, new Product(2, "Silla", 2));
    }

    /** Un recurso que falla con una excepción que nadie maneja. */
    @GET
    @Path("/{id}/audit")
    public Product audit(@PathParam("id") long id) {
        throw new IllegalStateException("La auditoría de Acme falló");
    }

    /** Un recurso que falla con una excepción de JAX-RS con el status 503, que la extensión responde con el sobre. */
    @GET
    @Path("/{id}/price")
    public Product price(@PathParam("id") long id) {
        throw new WebApplicationException("El servicio de precios de Acme no responde", 503);
    }

    /** Un recurso que falla con una excepción con la respuesta del proveedor como cuerpo. */
    @GET
    @Path("/{id}/provider")
    public Product provider(@PathParam("id") long id) {
        throw new WebApplicationException(Response.status(502).entity(Map.of("upstream", "acme")).build());
    }

    /** Un recurso que falla con una excepción que maneja el propio servicio. */
    @POST
    @Path("/{id}/locks")
    public Product lock(@PathParam("id") long id) {
        throw new CatalogMappers.ProductLockedException("inventario");
    }

    /** Un resumen que el recurso entrega con una vista de Jackson: solo lo público sale en {@code data}. */
    @GET
    @Path("/{id}/summary")
    @JsonView(Public.class)
    public Summary summary(@PathParam("id") long id) {
        return new Summary(1, "Mesa", "costo interno");
    }

    /** La vista de lo que ve cualquier cliente. */
    public interface Public {}

    /** La vista de lo que solo ve el servicio. */
    public interface Internal {}

    /**
     * Un producto con un campo que no es público.
     *
     * @param id   el identificador, público
     * @param name el nombre, público
     * @param cost el costo, solo de la vista interna
     */
    public record Summary(
            @JsonView(Public.class) long id, @JsonView(Public.class) String name, @JsonView(Internal.class) String cost) {}
}
