package pe.edu.nova.java.starters.api.standard.quarkus.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import io.smallrye.mutiny.Multi;

import io.vertx.core.buffer.Buffer;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.StreamingOutput;

import org.junit.jupiter.api.Test;

import pe.edu.nova.java.libs.api.standard.error.ErrorPorts;
import pe.edu.nova.java.libs.api.standard.error.NovaErrorCatalog;
import pe.edu.nova.java.libs.api.standard.error.NovaErrorStatusMapper;
import pe.edu.nova.java.libs.api.standard.error.SerializedError;
import pe.edu.nova.java.libs.api.standard.metadata.ApiMetadata;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

/**
 * Cada regla del filtro del sobre de éxito, sin levantar Quarkus. Las mismas reglas las prueban de punta a punta las
 * pruebas del módulo de deployment, con HTTP de verdad.
 */
class ApiResponseFilterTest {

    private record Product(long id, String name) {}

    private final ApiResponseFilter filter = new ApiResponseFilter(ErrorPorts.defaults());

    @Test
    void anObjectIsWrappedWithTheStatusOfTheResponse() {
        for (int status : new int[] {200, 201, 202, 203}) {
            Exchange exchange = new Exchange(status, new Product(1, "Mesa"), MediaType.APPLICATION_JSON_TYPE);

            filter.wrap(exchange.request(), exchange.response(), null);

            ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, exchange.entity);
            assertTrue(envelope.success());
            assertEquals(status, envelope.status());
            assertEquals(new Product(1, "Mesa"), envelope.data());
            assertEquals(List.of(), envelope.errors());
        }
    }

    @Test
    void theEnvelopeOfASuccessHasNoMetadataAsInSpring() {
        Exchange exchange = new Exchange(200, List.of(new Product(1, "Mesa")), MediaType.APPLICATION_JSON_TYPE);

        filter.wrap(exchange.request(), exchange.response(), null);

        ApiResponse<?> envelope = (ApiResponse<?>) exchange.entity;
        assertNull(envelope.metadata());
        assertEquals(List.of(), envelope.links());
        assertNull(envelope.rateLimitInfo());
        assertNull(envelope.pageInfo());
        assertEquals(ApiResponse.<Object>builder().data(List.of(new Product(1, "Mesa"))).status(200).build(), envelope);
    }

    @Test
    void aSuccessWithoutBodyIsWrappedWithNullData() {
        for (int status : new int[] {200, 201, 202}) {
            Exchange exchange = new Exchange(status, null, null);

            filter.wrap(exchange.request(), exchange.response(), null);

            ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, exchange.entity);
            assertTrue(envelope.success());
            assertEquals(status, envelope.status());
            assertNull(envelope.data());
        }
    }

    @Test
    void theContentLengthOfTheReplacedBodyIsDropped() {
        Exchange exchange = new Exchange(200, new Product(1, "Mesa"), MediaType.APPLICATION_JSON_TYPE);
        exchange.headers.putSingle("Content-Length", 22);
        exchange.headers.putSingle("X-Other", "kept");

        filter.wrap(exchange.request(), exchange.response(), null);

        assertFalse(exchange.headers.containsKey("Content-Length"));
        assertEquals("kept", exchange.headers.getFirst("X-Other"));
    }

    @Test
    void aResponseWithoutAMediaTypeIsWrittenAsJsonWhateverTheClientNegotiates() {
        for (MediaType undetermined : new MediaType[] {null, MediaType.WILDCARD_TYPE, new MediaType("application", "*")}) {
            Exchange exchange = new Exchange(201, new Product(1, "Mesa"), undetermined);
            exchange.accepted = List.of(new MediaType("application", "*"));

            filter.wrap(exchange.request(), exchange.response(), null);

            assertInstanceOf(ApiResponse.class, exchange.entity);
            assertEquals(MediaType.APPLICATION_JSON_TYPE, exchange.writtenType);
        }
    }

    @Test
    void aResponseWithAJsonMediaTypeKeepsIt() {
        Exchange exchange = new Exchange(200, new Product(1, "Mesa"), new MediaType("application", "vnd.nova+json"));

        filter.wrap(exchange.request(), exchange.response(), null);

        assertInstanceOf(ApiResponse.class, exchange.entity);
        assertNull(exchange.writtenType, "el tipo que declaró el recurso no se cambia");
    }

    @Test
    void anEnvelopeThatIsAlreadyOneIsLeftAsItIs() {
        for (ApiResponse<?> built : List.<ApiResponse<?>>of(
                ApiResponse.ok("x"),
                ApiResponse.created("x"),
                ApiResponse.noContent(),
                ApiResponse.error(409, "choca"),
                ApiResponse.builder().data("x").metadata(ApiMetadata.defaults()).build())) {
            for (int status : new int[] {200, 201, 204, 409, 500}) {
                Exchange exchange = new Exchange(status, built, MediaType.APPLICATION_JSON_TYPE);

                filter.wrap(exchange.request(), exchange.response(), null);

                assertSame(built, exchange.entity);
            }
        }
    }

    @Test
    void rawContentIsLeftAsItIs() {
        List<Object> raw = List.of(
                "texto",
                new StringBuilder("texto"),
                new byte[] {1, 2, 3},
                new char[] {'a'},
                new ByteArrayInputStream(new byte[] {1}),
                new StringReader("texto"),
                new File("reporte.pdf"),
                Path.of("reporte.pdf"),
                (StreamingOutput) output -> output.write(1),
                Buffer.buffer("texto"),
                Multi.createFrom().items(1, 2),
                (Flow.Publisher<String>) subscriber -> { });
        for (Object entity : raw) {
            for (MediaType mediaType : new MediaType[] {null, MediaType.APPLICATION_JSON_TYPE}) {
                Exchange exchange = new Exchange(200, entity, mediaType);

                filter.wrap(exchange.request(), exchange.response(), null);

                assertSame(entity, exchange.entity, entity.getClass().getName());
            }
        }
    }

    @Test
    void aBodyWrittenWithAnotherContentTypeIsLeftAsItIs() {
        for (String type : List.of(
                "text/plain", "text/csv", "text/html", "application/xml", "application/pdf", "application/octet-stream",
                "text/event-stream", "application/x-ndjson", "application/stream+json", "multipart/form-data",
                "application/x-www-form-urlencoded")) {
            Product product = new Product(1, "Mesa");
            Exchange exchange = new Exchange(200, product, MediaType.valueOf(type));

            filter.wrap(exchange.request(), exchange.response(), null);

            assertSame(product, exchange.entity, type);
        }
    }

    @Test
    void everyJsonContentTypeIsWrapped() {
        for (String type : List.of(
                "application/json", "application/json;charset=UTF-8", "application/problem+json",
                "application/vnd.api+json", "application/hal+json", "APPLICATION/JSON")) {
            Exchange exchange = new Exchange(200, new Product(1, "Mesa"), MediaType.valueOf(type));

            filter.wrap(exchange.request(), exchange.response(), null);

            assertInstanceOf(ApiResponse.class, exchange.entity, type);
        }
    }

    @Test
    void aNumberOrABooleanThatQuarkusWritesAsTextIsLeftAsItIs() {
        for (Object value : List.of(42, 42L, 4.2, true, 'x', new BigDecimal("4.2"))) {
            Exchange exchange = new Exchange(200, value, null);

            filter.wrap(exchange.request(), exchange.response(), null);

            assertSame(value, exchange.entity, String.valueOf(value));
        }
    }

    @Test
    void aNumberOrABooleanThatDeclaresJsonIsWrapped() {
        for (Object value : List.of(42, true)) {
            Exchange exchange = new Exchange(200, value, MediaType.APPLICATION_JSON_TYPE);

            filter.wrap(exchange.request(), exchange.response(), null);

            assertEquals(value, ((ApiResponse<?>) exchange.entity).data());
        }
    }

    @Test
    void aNoContentAResetAndAPartialContentAreLeftAsTheyAre() {
        for (int status : new int[] {204, 205, 206}) {
            Product product = new Product(1, "Mesa");
            Exchange withBody = new Exchange(status, product, MediaType.APPLICATION_JSON_TYPE);
            Exchange withoutBody = new Exchange(status, null, null);

            filter.wrap(withBody.request(), withBody.response(), null);
            filter.wrap(withoutBody.request(), withoutBody.response(), null);

            assertSame(product, withBody.entity, "status " + status);
            assertNull(withoutBody.entity, "status " + status);
        }
    }

    @Test
    void aRedirectionAndTheStatusesOutsideSuccessAndErrorAreLeftAsTheyAre() {
        for (int status : new int[] {100, 199, 300, 301, 302, 303, 304, 307, 308, 399, 600, 999}) {
            Product product = new Product(1, "Mesa");
            Exchange withBody = new Exchange(status, product, MediaType.APPLICATION_JSON_TYPE);
            Exchange withoutBody = new Exchange(status, null, null);

            filter.wrap(withBody.request(), withBody.response(), null);
            filter.wrap(withoutBody.request(), withoutBody.response(), null);

            assertSame(product, withBody.entity, "status " + status);
            assertNull(withoutBody.entity, "status " + status);
        }
    }

    @Test
    void whatTheExceptionHandlingAnsweredIsNeverWrapped() {
        Map<String, String> ownBody = Map.of("lockedBy", "inventario");
        for (int status : new int[] {200, 404, 423, 502, 503}) {
            Exchange exchange = new Exchange(status, ownBody, MediaType.APPLICATION_JSON_TYPE);

            filter.wrap(exchange.request(), exchange.response(), new IllegalStateException("fallo"));

            assertSame(ownBody, exchange.entity, "status " + status);
        }
    }

    @Test
    void headAndOptionsAreLeftAsTheyAre() {
        for (String method : new String[] {"HEAD", "OPTIONS"}) {
            Product product = new Product(1, "Mesa");
            Exchange exchange = new Exchange(200, product, MediaType.APPLICATION_JSON_TYPE);
            exchange.method = method;

            filter.wrap(exchange.request(), exchange.response(), null);

            assertSame(product, exchange.entity, method);
        }
    }

    @Test
    void everyOtherMethodIsWrapped() {
        for (String method : new String[] {"GET", "POST", "PUT", "PATCH", "DELETE"}) {
            Exchange exchange = new Exchange(200, new Product(1, "Mesa"), MediaType.APPLICATION_JSON_TYPE);
            exchange.method = method;

            filter.wrap(exchange.request(), exchange.response(), null);

            assertInstanceOf(ApiResponse.class, exchange.entity, method);
        }
    }

    @Test
    void aClientThatDoesNotAcceptJsonGetsTheResponseAsItIs() {
        for (MediaType accepted : new MediaType[] {
            MediaType.TEXT_PLAIN_TYPE, MediaType.APPLICATION_XML_TYPE, new MediaType("application", "vnd.api+json")
        }) {
            Exchange withoutBody = new Exchange(200, null, null);
            withoutBody.accepted = List.of(accepted);
            Exchange withBody = new Exchange(200, new Product(1, "Mesa"), null);
            withBody.accepted = List.of(accepted);

            filter.wrap(withoutBody.request(), withoutBody.response(), null);
            filter.wrap(withBody.request(), withBody.response(), null);

            assertNull(withoutBody.entity, accepted.toString());
            assertEquals(new Product(1, "Mesa"), withBody.entity, accepted.toString());
        }
    }

    @Test
    void aClientThatAcceptsJsonInAnyWayGetsTheEnvelope() {
        for (List<MediaType> accepted : List.of(
                List.<MediaType>of(),
                List.of(MediaType.WILDCARD_TYPE),
                List.of(new MediaType("application", "*")),
                List.of(MediaType.APPLICATION_JSON_TYPE),
                List.of(MediaType.TEXT_HTML_TYPE, MediaType.WILDCARD_TYPE),
                List.of(MediaType.APPLICATION_XML_TYPE, MediaType.APPLICATION_JSON_TYPE))) {
            Exchange exchange = new Exchange(200, null, null);
            exchange.accepted = accepted;

            filter.wrap(exchange.request(), exchange.response(), null);

            assertInstanceOf(ApiResponse.class, exchange.entity, accepted.toString());
        }
    }

    @Test
    void anAcceptHeaderThatCannotBeReadLeavesTheResponseAsItIs() {
        for (int status : new int[] {200, 404, 503}) {
            Exchange withoutBody = new Exchange(status, null, null);
            withoutBody.acceptFailure = new IllegalArgumentException("Accept mal formado");
            Exchange withBody = new Exchange(status, new Product(1, "Mesa"), MediaType.APPLICATION_JSON_TYPE);
            withBody.acceptFailure = new IllegalArgumentException("Accept mal formado");

            filter.wrap(withoutBody.request(), withoutBody.response(), null);
            filter.wrap(withBody.request(), withBody.response(), null);

            assertNull(withoutBody.entity, "status " + status);
            assertEquals(new Product(1, "Mesa"), withBody.entity, "status " + status);
        }
    }

    @Test
    void aClientThatAsksForTheVendorTypeOfTheResourceGetsTheEnvelope() {
        MediaType vendor = new MediaType("application", "vnd.nova.product+json");
        Exchange exchange = new Exchange(200, new Product(1, "Mesa"), vendor);
        exchange.accepted = List.of(vendor);

        filter.wrap(exchange.request(), exchange.response(), null);

        assertInstanceOf(ApiResponse.class, exchange.entity);
    }

    @Test
    void aClientErrorWithoutBodyIsAnErrorEnvelope() {
        Exchange exchange = new Exchange(404, null, null);

        filter.wrap(exchange.request(), exchange.response(), null);

        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, exchange.entity);
        assertFalse(envelope.success());
        assertEquals(404, envelope.status());
        assertNull(envelope.data());
        assertEquals(1, envelope.errors().size());
        assertEquals("NOT_FOUND", envelope.errors().get(0).code());
        assertEquals("El recurso no existe", envelope.errors().get(0).message());
        assertNotNull(envelope.metadata());
        assertNotNull(envelope.metadata().traceId());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, exchange.writtenType);
    }

    @Test
    void aServerErrorWithoutBodyCarriesTheGenericMessage() {
        Exchange exchange = new Exchange(503, null, null);

        filter.wrap(exchange.request(), exchange.response(), null);

        ApiResponse<?> envelope = (ApiResponse<?>) exchange.entity;
        assertEquals(503, envelope.status());
        assertEquals("SERVICE_UNAVAILABLE", envelope.errors().get(0).code());
        assertEquals("El servicio no está disponible en este momento", envelope.errors().get(0).message());
    }

    @Test
    void aClientErrorWithABodyKeepsTheBodyAsData() {
        Map<String, String> ownBody = Map.of("reason", "En un pedido abierto");
        Exchange exchange = new Exchange(409, ownBody, MediaType.APPLICATION_JSON_TYPE);

        filter.wrap(exchange.request(), exchange.response(), null);

        ApiResponse<?> envelope = (ApiResponse<?>) exchange.entity;
        assertFalse(envelope.success());
        assertEquals(409, envelope.status());
        assertSame(ownBody, envelope.data());
        assertEquals("CONFLICT", envelope.errors().get(0).code());
    }

    @Test
    void aTextErrorKeepsItsText() {
        Exchange exchange = new Exchange(400, "texto plano", MediaType.TEXT_PLAIN_TYPE);

        filter.wrap(exchange.request(), exchange.response(), null);

        assertEquals("texto plano", exchange.entity);
    }

    @Test
    void aSerializerThatIsNotAnEnvelopeAnswersWhatItBuilds() {
        Map<String, Object> problem = Map.of("type", "about:blank", "status", 404);
        ApiResponseFilter custom = new ApiResponseFilter(new ErrorPorts(
                new NovaErrorStatusMapper(),
                new NovaErrorCatalog(),
                failure -> new SerializedError(failure.status(), problem, Map.of())));

        Exchange withoutBody = new Exchange(404, null, null);
        Exchange withBody = new Exchange(404, Map.of("ignored", "sí"), MediaType.APPLICATION_JSON_TYPE);
        custom.wrap(withoutBody.request(), withoutBody.response(), null);
        custom.wrap(withBody.request(), withBody.response(), null);

        assertSame(problem, withoutBody.entity);
        assertSame(problem, withBody.entity);
    }

    @Test
    void aSuccessIsNotAnErrorForTheSerializer() {
        ApiResponseFilter custom = new ApiResponseFilter(new ErrorPorts(
                new NovaErrorStatusMapper(),
                new NovaErrorCatalog(),
                failure -> {
                    throw new AssertionError("un éxito no consulta al serializador de errores");
                }));
        Exchange exchange = new Exchange(200, new Product(1, "Mesa"), MediaType.APPLICATION_JSON_TYPE);

        custom.wrap(exchange.request(), exchange.response(), null);

        assertInstanceOf(ApiResponse.class, exchange.entity);
    }

    @Test
    void thePortsAreRequired() {
        try {
            new ApiResponseFilter(null);
        } catch (NullPointerException expected) {
            assertEquals("ports es obligatorio", expected.getMessage());
            return;
        }
        throw new AssertionError("se esperaba un NullPointerException");
    }

    /** Una petición y la respuesta de un recurso: lo que lee el filtro y lo que escribe. */
    private static final class Exchange {

        String method = "GET";
        List<MediaType> accepted = List.of(MediaType.WILDCARD_TYPE);
        RuntimeException acceptFailure;
        final int status;
        Object entity;
        final MediaType mediaType;
        final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        MediaType writtenType;

        Exchange(int status, Object entity, MediaType mediaType) {
            this.status = status;
            this.entity = entity;
            this.mediaType = mediaType;
        }

        ContainerRequestContext request() {
            return (ContainerRequestContext) Proxy.newProxyInstance(
                    ContainerRequestContext.class.getClassLoader(),
                    new Class<?>[] {ContainerRequestContext.class},
                    (proxy, called, args) -> switch (called.getName()) {
                        case "getMethod" -> method;
                        case "getAcceptableMediaTypes" -> {
                            if (acceptFailure != null) {
                                throw acceptFailure;
                            }
                            yield accepted;
                        }
                        default -> throw new UnsupportedOperationException(called.toString());
                    });
        }

        ContainerResponseContext response() {
            return (ContainerResponseContext) Proxy.newProxyInstance(
                    ContainerResponseContext.class.getClassLoader(),
                    new Class<?>[] {ContainerResponseContext.class},
                    (proxy, called, args) -> switch (called.getName()) {
                        case "getStatus" -> status;
                        case "getEntity" -> entity;
                        case "getMediaType" -> mediaType;
                        case "getHeaders" -> headers;
                        case "getEntityAnnotations" -> new Annotation[0];
                        case "setEntity" -> {
                            entity = args[0];
                            writtenType = args.length == 3 ? (MediaType) args[2] : null;
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(called.toString());
                    });
        }
    }
}
