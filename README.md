# nova-api-standard-quarkus-extension

> Extensión de Quarkus que conecta [`nova-api-standard`](https://github.com/ahincho/nova-java-01-api-standard),
> la librería pura y sin framework, con Quarkus: envuelve lo que devuelve un recurso en el sobre de Nova y responde
> cada error con ese sobre y el
> [modelo de errores por capas](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-031-modulo-de-errores-por-capas-con-trazabilidad.md).

## Módulos

Desde la 3.0.0 es una extensión de Quarkus completa, con su módulo de deployment
([ADR-050](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-050-errores-por-capas-en-quarkus.md)):

| Módulo | `artifactId` | Qué tiene |
|---|---|---|
| runtime | `nova-api-standard-quarkus-extension` | el filtro del sobre de éxito, los mappers de excepciones, los productores de los puertos, el `TraceIdSource`, el contador `nova.errors` y el customizer de Jackson |
| deployment | `nova-api-standard-quarkus-extension-deployment` | los pasos de build: registrar los beans, el filtro, el `TraceIdSource` y los records del sobre, y activar el contador si hay Micrometer |

Los dos salen con la misma versión y el mismo `groupId`, `pe.edu.nova.java.starters`. **El servicio solo
declara el runtime**: Quarkus resuelve el deployment por su cuenta, con la misma versión.

## Qué hace

Un recurso devuelve el objeto, y la extensión lo entrega en el sobre de Nova con el status real de la respuesta.
Un servicio Quarkus que lanza `DomainError.notFound(...)` responde un 404 con el mismo sobre, el código del
error y un `traceId` que un alumno puede citar, y el log lo registra en `WARN` sin stack trace. Lo mismo que
el starter de Spring Boot y NestJS, con la misma suite de contrato.

| Pieza | Función |
|---|---|
| `ApiResponseFilter` (`@ServerResponseFilter`) | El sobre de éxito: envuelve en `ApiResponse` lo que un recurso devuelve como objeto, con el status real. Es el equivalente del `ApiResponseInterceptor` de Spring. |
| `NovaExceptionMappers` (`@ServerExceptionMapper`) | El núcleo: lee cada excepción por lo que es (ver la tabla de más abajo) y la responde. No se reemplaza. |
| `ValidationExceptionMappers`, `SecurityExceptionMappers` | La validación y la seguridad. Se registran solo si el servicio trae Hibernate Validator o Quarkus Security. |
| `ErrorResponder` | Registra el error en el log una sola vez, lo cuenta y se lo pasa a los puertos sin el proveedor ni la causa. |
| `ErrorPortProducers` (`@DefaultBean`) | Los puertos de Nova: `ErrorStatusMapper`, `ErrorCatalog`, `ErrorSerializer` y `ErrorPorts`. Un servicio los reemplaza con un bean propio. |
| `MdcTraceIdSource` | De dónde sale el `traceId`: la clave `traceId` del MDC de JBoss Logging, que llena `quarkus-opentelemetry`. |
| `ErrorCounter` | El contador `nova.errors` con las etiquetas `layer` y `code`, de Micrometer si el servicio lo tiene; si no, el vacío. |
| `ApiObjectMapperCustomizer` (`@Singleton ObjectMapperCustomizer`) | Registra `JavaTimeModule` y deshabilita `WRITE_DATES_AS_TIMESTAMPS` y `FAIL_ON_EMPTY_BEANS` para serializar correctamente `Instant`/`LocalDateTime` y beans vacíos. |

El módulo de deployment corre al construir la aplicación, nunca al arrancarla:

- **Registra los beans** con `AdditionalBeanBuildItem` y los indexa para Quarkus REST, que es lo que hace que
  el filtro se ejecute y que los mappers entren en la cadena de excepciones. Por eso **el servicio no necesita
  `quarkus.index-dependency`**: con la dependencia alcanza.
- **Registra el `TraceIdSource`** con `ServiceProviderBuildItem`, porque en una imagen nativa `ServiceLoader`
  solo ve lo que se registró al construirla.
- **Registra los records del sobre para la reflexión** (`ApiResponse`, `ApiError`, `ApiMetadata`,
  `ApiLink`, `RateLimitInfo` y `PageInfo`). El sobre se arma dentro de un mapper de excepciones, donde el
  análisis de la imagen nativa no lo ve: sin este paso, Jackson no puede leer sus componentes y cada
  respuesta de error termina en 500.
- **Elige qué activar según lo que el servicio trae**: los mappers de validación solo con Hibernate Validator,
  los de seguridad solo con Quarkus Security, y el contador de Micrometer solo si Micrometer es el sistema de
  métricas; sin él, el contador es el vacío y la extensión no arrastra Micrometer.

## Estado

| Campo | Valor |
|---|---|
| Versión en desarrollo | `3.0.0` |
| Última versión publicada | `2.0.1` |
| Quarkus | `3.33.3.3` LTS (pin Nova workspace) |
| Java | `25` |
| GroupId | `pe.edu.nova.java.starters` |
| ArtifactId | `nova-api-standard-quarkus-extension` y `nova-api-standard-quarkus-extension-deployment` |
| Registry | GitHub Packages (`maven.pkg.github.com/ahincho/nova-java-10-api-standard-quarkus-extension`) |
| Framework | Quarkus (alternativa a Spring Boot) |

> **Sobre el nombre.** El `artifactId` es `nova-` más el nombre del repositorio sin la
> tecnología ni el número, como pide
> [ADR-039](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-039-nombres-de-artefacto-derivados-del-repositorio.md):
> primero la capacidad (`api-standard`), después el framework y el tipo
> (`quarkus-extension`).
>
> Hasta la 1.0.1 se publicó como `nova-quarkus-api-ext`. Ese nombre corto era un rodeo para
> un supuesto límite de longitud de GitHub Packages que la evidencia no sostiene, y su paquete
> ya no existe en el registro. La primera versión con el nombre actual es la **2.0.1**: la 2.0.0
> ya existía desde julio, y una versión publicada no se sobrescribe. Para migrar un consumidor,
> `ops/rename-artifacts.py --phase 1` de `nova-shared-01-docs` reescribe la coordenada.

## Cómo consumirla desde una app Quarkus

### 1. Agregar la dependencia

`build.gradle.kts`:

```kotlin
dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.33.3.3"))
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")

    // Esta extensión: solo el runtime, el deployment lo resuelve Quarkus
    implementation("pe.edu.nova.java.starters:nova-api-standard-quarkus-extension:<versión>")

    // Transitiva: nova-api-standard ya viene incluida

    // Opcionales: la extensión activa lo que corresponde a cada una
    implementation("io.quarkus:quarkus-hibernate-validator")  // errores de validación por campo
    implementation("io.quarkus:quarkus-security")             // 401 y 403 de la seguridad por anotaciones
    implementation("io.quarkus:quarkus-micrometer")           // el contador nova.errors
    implementation("io.quarkus:quarkus-opentelemetry")        // el traceId de la petición
}
```

El sobre viaja como JSON, así que el servicio declara `quarkus-rest-jackson`, como cualquier servicio
Quarkus que responde JSON.

### 2. Devolver el objeto y lanzar errores por capa desde los recursos

```java
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    @GET
    @Path("/{id}")
    public UserDto findById(@PathParam("id") String id) {
        return userService.findById(id)
                .orElseThrow(() -> DomainError.notFound("USER_NOT_FOUND", "El usuario " + id + " no existe"));
    }

    @POST
    public RestResponse<UserDto> create(NewUserDto request) {
        return RestResponse.status(RestResponse.Status.CREATED, userService.create(request));
    }
}
```

El recurso no sabe de HTTP ni del sobre: devuelve el objeto, y el filtro lo entrega como `ApiResponse` con el status
real, así que el `create` de arriba responde `"status": 201` en el cuerpo. Tampoco decide los errores: el
`ErrorStatusMapper` decide que un `NOT_FOUND` de `domain` es un 404, y el mismo caso de uso sirve detrás de un
consumidor de cola. Un recurso que ya arma el `ApiResponse` a mano, como los ejemplos 04 y 06, sigue funcionando
igual: el filtro no lo envuelve de nuevo.

### 3. Beneficios automáticos (sin código extra)

- Lo que devuelve `findById` sale en el sobre de éxito, con el status real y sin `metadata`, como en Spring:
  ```json
  {
    "success": true,
    "status": 200,
    "data": {"id": "42", "name": "Ana"},
    "errors": [],
    "metadata": null,
    "links": [],
    "rateLimitInfo": null,
    "pageInfo": null
  }
  ```
- El `DomainError.notFound` de arriba responde un `404`, con su código propio porque es un 4xx:
  ```json
  {
    "success": false,
    "status": 404,
    "data": null,
    "errors": [{"code": "USER_NOT_FOUND", "message": "El usuario 42 no existe", "field": null, "details": {}}],
    "metadata": {
      "timestamp": "2026-10-01T15:04:05.149116700Z",
      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
      "apiVersion": null,
      "processingTimeMs": null,
      "customFields": {}
    },
    "links": [],
    "rateLimitInfo": null,
    "pageInfo": null
  }
  ```
- Una excepción inesperada (`RuntimeException`, `IllegalArgumentException`...) responde un `500` con el código
  `INTERNAL_SERVER_ERROR` y el mensaje genérico del catálogo: el detalle técnico va solo al log, con su
  `traceId`. Un 5xx nunca revela al proveedor que falló.
- Una ruta que no existe, un método que no se acepta o un cuerpo que no se puede leer responden su propio
  status, no un 500.
- Los campos `Instant` en `ApiMetadata.timestamp` se serializan como ISO-8601
  (`2026-07-14T12:34:56Z`), no como timestamp numérico.

### 4. El sobre de éxito

`ApiResponseFilter` es un `@ServerResponseFilter` de Quarkus REST: corre con la respuesta de cada recurso JAX-RS y
aplica las reglas del `ApiResponseInterceptor` de Spring, cada una con su equivalente en JAX-RS. No tiene un
interruptor para apagarlo, como pide
[ADR-034](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-034-puertos-con-implementacion-por-defecto.md),
y tampoco una anotación para sacar un método del sobre, como Spring: se sale del sobre devolviendo un `String`, un
`byte[]` o un flujo.

| El recurso contesta | Qué sale | En Spring |
|---|---|---|
| un objeto, una lista, un mapa, un `Uni<T>` o un `CompletionStage<T>` | el sobre de éxito con el objeto en `data` y el status real: un 201 dice 201, con `@ResponseStatus`, `Response` o `RestResponse` | igual |
| un 2xx sin cuerpo, como `Response.ok().build()`, `Response.created(uri).build()` o `Response.accepted().build()` | el sobre con `data: null` y el status real | igual (`ResponseEntity.ok().build()`) |
| un `ApiResponse` armado a mano | tal cual, byte por byte | igual |
| un 4xx o 5xx sin lanzar una excepción, como `Response.status(404).build()` | un sobre de error armado con los puertos, con el cuerpo propio del recurso en `data` si lo mandó; no va al log ni al contador | igual |
| un `String`, un `byte[]`, un `InputStream`, un `File` o un `StreamingOutput` | tal cual | `String`, `byte[]` y `Resource` |
| un cuerpo con otro tipo de contenido (texto, CSV, PDF, XML) | tal cual | el conversor elegido no es de JSON |
| un número o un booleano suelto, sin `@Produces(MediaType.APPLICATION_JSON)` | tal cual: Quarkus REST lo escribe como texto | diferencia: Spring lo escribe como JSON y lo envuelve |
| un flujo `Multi` como arreglo JSON, NDJSON o eventos SSE | tal cual, elemento por elemento | no aplica |
| un método que devuelve `null` | un 204 sin cuerpo, como manda JAX-RS | diferencia: Spring contesta 200 con `data: null` |
| un 204, un 205 o un 304 | tal cual, sin cuerpo | igual en el cable: Spring arma el sobre y Tomcat no escribe el cuerpo de estos status |
| un 206 o un 3xx | tal cual | diferencia: Spring escribe el sobre; aquí no, porque un 206 es un fragmento del cuerpo y el de una redirección no lo lee nadie |
| lo que contesta el manejo de excepciones: los mappers de la extensión, los de un servicio o una `WebApplicationException` con cuerpo propio | tal cual: los de la extensión ya traen el sobre que armaron los puertos | diferencia: Spring no toca lo que contesta su manejador, pero sí envuelve el `@ControllerAdvice` del servicio; aquí un mapper del servicio no se distingue del de la extensión sin marcar cada respuesta, y ADR-050 manda cambiar la forma de un error con los puertos |
| SmallRye Health, las métricas, OpenAPI y la Dev UI | su propio formato: son rutas de Vert.x, no recursos JAX-RS, y ni siquiera pasan por el filtro; un health caído sigue diciendo `"status": "DOWN"` | Actuator y el controlador de errores de Spring Boot |
| una petición HEAD u OPTIONS, o un cliente que no acepta el JSON con que se escribiría el sobre | tal cual | igual: Spring no escribe lo que el cliente no acepta |

- **El sobre de éxito no lleva `metadata`**, igual que el de Spring: es `null`. Solo un error lleva
  `metadata.traceId` y `metadata.timestamp`.
- **Un recurso con su propio `/health` o `/metrics` en JAX-RS** es un recurso más y sale en el sobre. Para las
  sondas de salud, usar SmallRye Health.

### 5. Cómo se lee cada excepción

Una excepción del framework se lee por su status. La capa decide el nivel del log: `domain` y `application`
son esperados y van en `WARN` sin stack trace; `infrastructure` y `platform` son incidentes y van en `ERROR`
con la causa.

| Excepción | Status | Capa | Mensaje al cliente |
|---|---|---|---|
| un `NovaError` | el de `ErrorStatusMapper` | la del error | el propio si es 4xx; si no, el del catálogo |
| `WebApplicationException` y sus subclases (`NotFoundException`, `NotAllowedException`, `NotSupportedException`...) | el de su respuesta | 4xx `application`; 502, 503 o 504 `infrastructure`; otro 5xx `platform` | el del catálogo |
| `ConstraintViolationException` sobre la entrada | 400 | `application` | `BAD_REQUEST`, con un error por violación |
| `ConstraintViolationException` sobre el valor de retorno | 500 | `platform` | el del catálogo |
| el cuerpo que Jackson no puede leer | 400 | `application` | «No se pudo leer el cuerpo de la solicitud», el mismo texto fijo que Spring |
| `UnauthorizedException` y `AuthenticationFailedException` de Quarkus Security | 401 | `application` | el del catálogo |
| `ForbiddenException` de Quarkus Security | 403 | `application` | el del catálogo |
| cualquier otra, incluidas `IllegalArgumentException` y `SecurityException` | 500 | `platform` | el del catálogo |

- **El campo de una violación** es el último nodo de su ruta de propiedad: `create.request.name` se escribe
  `name`. Un elemento de una lista se nombra por su lista, y una violación de toda la clase lleva `field`
  vacío.
- **Una `WebApplicationException` conserva sus headers**, como el `Allow` de un 405 o el `Retry-After` de un
  `ServiceUnavailableException`. El cuerpo sí lo escribe el serializador.
- **Una `WebApplicationException` con un cuerpo propio** se devuelve tal cual, como manda JAX-RS: quien arma
  una respuesta con entidad responde por ella. Tampoco se toca una redirección ni ninguna respuesta que no
  sea un error.
- **Quarkus REST arma su propio 405 sin el header `Allow`**, así que no hay nada que conservar en ese caso.
- **Un 401 de Quarkus Security lleva el reto del mecanismo de autenticación**, el header `WWW-Authenticate` que
  exige RFC 9110, sección 15.5.2. Los mappers de la extensión reemplazan a los de Quarkus, que lo añadían, así
  que hacen lo mismo: le piden el reto al autenticador de la petición y suman sus headers a la respuesta, cuyo
  cuerpo sigue siendo el sobre de Nova y cuyo status sigue siendo 401. Si el servicio no tiene mecanismo de
  autenticación, o el reto falla, el 401 sale sin el header; en un servicio sin mecanismo Quarkus contesta un
  403, y aquí sigue siendo 401. Un 403 no lleva reto, y una redirección como la de un mecanismo de formulario
  sigue su camino.

### 6. Reemplazar un puerto

Cada puerto es un `@DefaultBean`: un servicio, o la extensión de una organización como UTP, declara su propio
bean del mismo tipo y la extensión lo usa, sin forkear y sin un mapper más específico. Es el papel de
`@ConditionalOnMissingBean` en Spring Boot.

```java
@ApplicationScoped
public class OrganizationPorts {

    @Produces
    @Singleton
    ErrorCatalog catalog() {
        return failure -> new CatalogEntry("ORG-" + failure.status(), "Texto de la organización");
    }
}
```

El núcleo escribe el log y sanea antes de llamar a los puertos, así que un puerto propio nunca ve al proveedor
ni la causa: recibe un `SanitizedFailure`. Por la misma razón los mappers no se reemplazan.

### 7. La traza, el log y la métrica

- **`traceId`**: lo da la clave `traceId` del MDC de JBoss Logging, que llena `quarkus-opentelemetry`. Un error
  de Nova lo captura al nacer y llega a `metadata.traceId`. Si la petición no tiene uno, el núcleo genera uno,
  lo pone en el MDC mientras responde y escribe el mismo en el log y en el cuerpo.
- **La línea de log** lleva `traceId`, `layer`, `code`, `status` y, si hay, `upstream` como entradas del MDC,
  de modo que `quarkus-logging-json` las escribe como campos, y también en el texto:
  `[Nova Platform] 504 GATEWAY_TIMEOUT layer=infrastructure upstream=courier traceId=... (InfrastructureError): ...`.
- **`Retry-After`**: un error con `retryAfter` lo escribe el serializador como header, en segundos.
- **`nova.errors`**: con Micrometer, cada error respondido suma uno al contador, con las etiquetas `layer` y
  `code`. Un servicio lo reemplaza declarando su propio bean `ErrorCounter`.

### 8. Lo que no cubre

- **La autenticación que Quarkus rechaza antes de llegar a REST.** Con la autenticación proactiva, un token
  inválido se responde en la capa HTTP, sin pasar por ningún mapper, y sale sin cuerpo
  ([ADR-050](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-050-errores-por-capas-en-quarkus.md),
  pregunta abierta 3). Lo que sí cubre son las excepciones de seguridad que llegan a REST, y esas salen con el
  header `WWW-Authenticate` de su mecanismo de autenticación (sección 5).
- **Una `WebApplicationException` con cuerpo**, como la que lanza un cliente REST con la respuesta del
  proveedor, se devuelve tal cual: JAX-RS no consulta a los mappers cuando la respuesta trae entidad, y el filtro
  del sobre de éxito tampoco la toca. Para que cuente como un incidente de `infrastructure`, con el proveedor en el
  log y sin su cuerpo en la respuesta, el servicio la traduce a `InfrastructureError`.
- **El documento de OpenAPI** describe el tipo que devuelve el recurso, no el sobre en que sale: el filtro envuelve
  al responder, no al construir. Pasa igual con el starter de Spring Boot.

## Migrating to 3.0.0

La 3.0.0 es una versión mayor porque cambia lo que un cliente recibe: el éxito sale en el sobre de Nova y cada error
sale por capas
([ADR-050](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-050-errores-por-capas-en-quarkus.md)).
La receta:

| Antes (2.x) | Desde la 3.0.0 | Qué hacer |
|---|---|---|
| un recurso que devolvía un objeto suelto: el cuerpo era el objeto | el cuerpo es el sobre de éxito, con el objeto en `data` y el status real en `status` | los clientes leen el objeto de `data`; el recurso no cambia |
| un recurso que devolvía un `ApiResponse` armado a mano | sin cambio: el filtro no lo envuelve de nuevo, byte por byte | nada; conviene devolver el objeto suelto y dejar que la extensión arme el sobre, que además pone el status real |
| un `Response` 2xx sin cuerpo, como `Response.ok().build()` | el sobre con `data: null` | nada; es aditivo para quien no lee el cuerpo |
| un 4xx o 5xx que el recurso contestaba sin excepción, con o sin cuerpo propio | un sobre de error, con el cuerpo propio en `data` | lanzar el error por capas, o leer el cuerpo de `data` |
| un `String`, un `byte[]`, un flujo, un 204 o un cuerpo que no es JSON | sin cambio: salen tal cual | nada |
| un recurso JAX-RS propio de salud o de métricas, como `@Path("/health")` | sale en el sobre, porque es un recurso más | usar SmallRye Health, que no pasa por el filtro, o devolver un `String` |
| un `@ServerExceptionMapper` del servicio con cuerpo propio | sin cambio: contesta como lo armó | nada; para el sobre, armar el `ApiResponse` en el mapper o usar un puerto |
| `IllegalArgumentException` respondida como 400 con su mensaje | un `PlatformError`, respondido como 500 | lanzar `ApplicationError.invalidInput(...)` ante una entrada inválida |
| `SecurityException` respondida como 403 | 500 | lanzar `ApplicationError.forbidden(...)`, o dejar que Quarkus Security responda |
| `INTERNAL_ERROR` en todo 5xx | el código del status: `INTERNAL_SERVER_ERROR`, `BAD_GATEWAY`, `SERVICE_UNAVAILABLE` o `GATEWAY_TIMEOUT` | comparar contra el código del catálogo |
| mensajes genéricos en inglés, como `Internal server error` | los mensajes del catálogo en español | no comparar contra el texto del mensaje |
| un 404, 405 o 415 de Quarkus REST respondido como 500 | su status, con el código del catálogo | nada |
| un cuerpo sin `metadata` | `metadata.traceId` y `metadata.timestamp` | nada; es aditivo |
| `ApiExceptionMapper` | reemplazado por los mappers del núcleo y los puertos | reemplazar un puerto con un bean propio, en vez de un mapper más específico |
| `quarkus.index-dependency` para la extensión | innecesario, y con él el build falla: Quarkus indexa la extensión entera y encuentra a la vez los dos productores del contador y los mappers de las extensiones opcionales (`Ambiguous dependencies for type ...ErrorCounter`, `When '@ServerExceptionMapper' is used without a value...`) | quitar las dos líneas de `application.properties` |

## Stack tecnológico

| Pieza | Versión | Por qué |
|---|---|---|
| Quarkus | 3.33.3.3 LTS | Pin Nova workspace; soporta Java 25 |
| `quarkus-rest` | (via BOM) | JAX-RS reactivo, `@Path`, `@ServerExceptionMapper` |
| `quarkus-arc` | (via BOM) | CDI: `@Singleton`, `@DefaultBean`, `@Inject` |
| `quarkus-jackson` | (via BOM) | Aporta `ObjectMapperCustomizer` + Jackson al compileClasspath |
| `nova-api-standard` | 1.1.0 | El sobre, el modelo de errores por capas y sus tres puertos — transitivo |
| Hibernate Validator, Quarkus Security, Micrometer | (via BOM) | Opcionales: solo `compileOnly`; la extensión no los arrastra |
| Java | 25 | LTS, coincide con la build matrix de Nova |
| JUnit | 6.0.3 | Mismo que el resto del meta-framework |
| OWASP plugin | 12.2.2 | Fail build on CVSS >= 7 (configurable) |
| CycloneDX plugin | 3.4.1 | SBOM generation |
| Gradle | 9.5.1 | Wrapper |
| Gradle Config Cache | **disabled** | Bug conocido de Quarkus 3.x con config cache; re-habilitar cuando Gradle/Quarkus estabilicen |

## Testing

Cada módulo prueba lo que es suyo:

- **El runtime** tiene pruebas unitarias con JUnit puro: el núcleo (`ErrorResponder`), cómo lee cada
  excepción, la fuente del `traceId`, el contador, la configuración del `ObjectMapper` y cada regla del filtro del
  sobre de éxito.
- **El deployment** tiene una prueba de los pasos de build y pruebas con `QuarkusUnitTest`, que arman una
  aplicación Quarkus mínima con la extensión como única dependencia, sin `quarkus.index-dependency`, y
  comprueban por HTTP:
  - la **suite de contrato de ADR-031**, los mismos nueve casos que corren Spring Boot y NestJS;
  - la línea de log y sus campos del MDC, el `traceId` que llena `quarkus-opentelemetry`, el generado cuando
    falta y los headers de un 405;
  - las excepciones de JAX-RS, la validación y la seguridad, esta última con un mecanismo de autenticación
    Basic de verdad para ver el `WWW-Authenticate` de un 401;
  - el reemplazo de los puertos con beans propios y el contador `nova.errors` con Micrometer;
  - el **sobre de éxito**, con las mismas pruebas que `EnvelopeStatusTest`, `ErrorEnvelopeTest` y
    `UnwrappedResponseTest` del starter de Spring Boot, y los cuerpos de éxito comparados byte a byte con los que
    ese starter escribe sobre un Tomcat real;
  - SmallRye Health, las métricas de Prometheus, OpenAPI y la Dev UI, que contestan con su propio formato, y un
    filtro de JAX-RS que anota las rutas que ve para probar que ni siquiera pasan por la cadena de filtros; la Dev
    UI solo existe en modo dev, así que esa prueba arranca la aplicación en ese modo, en el puerto 18080.

El plugin `io.quarkus.extension` va aplicado al runtime: genera el descriptor de la extensión, la vincula
con su deployment y le da a las pruebas de ese módulo el modelo de la aplicación. Sus tareas se declaran
incompatibles con el configuration cache, que de todos modos está apagado.

Para correr las pruebas localmente:

```bash
./gradlew test
```

## CI/CD

Workflows en `.github/workflows/`:

- `ci.yml` — pull request: ejecuta build, matrix build (Java 21 y 25), OWASP, SBOM, SonarCloud.
- `release-please.yml` — push a `main`: abre PR de release automático cuando detecta commits convencionales.
- `publish-on-tag.yml` — push de tag `vX.Y.Z`: publica los dos módulos a GitHub Packages y comprueba que
  se pueden descargar.

El paquete se publica como `public` porque el repo es `public` y
`NOVA_PACKAGE_VISIBILITY` no está configurada (default `public`).

## Desarrollo local

**Prerrequisito:** Para compilar localmente necesitas `nova-api-standard:1.1.0`
disponible. Como GitHub Packages requiere auth incluso para paquetes public,
tienes dos opciones:

**Opción A (recomendada):** Publicar `nova-api-standard` a Maven Local primero:

```bash
# Desde el repo de nova-java-01-api-standard, con la versión 1.1.0 (gradle.properties trae un SNAPSHOT):
cd ../nova-java-01-api-standard
./gradlew publishToMavenLocal -Pversion=1.1.0

# Volver a este repo y compilar normalmente (Gradle busca en Maven Local
# porque este build lo declara):
cd ../nova-java-10-api-standard-quarkus-extension
./gradlew compileJava
```

**Opción B:** Setear `GITHUB_TOKEN` en el shell:

```bash
export GITHUB_TOKEN=ghp_xxx
export GITHUB_ACTOR=tu-usuario
./gradlew compileJava
```

Una vez que la dep está disponible:

```bash
# Compilar
./gradlew compileJava

# Tests
./gradlew test

# OWASP check (requiere NVD_API_KEY para velocidad)
NVD_API_KEY=xxx ./gradlew dependencyCheckAnalyze

# Publicar a Maven Local (sin subir a GitHub Packages)
./gradlew publishToMavenLocal

# Publicar a GitHub Packages (requiere GITHUB_TOKEN)
GITHUB_TOKEN=ghp_xxx ./gradlew publish
```

## Documentación relacionada

- [ADR-050](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-050-errores-por-capas-en-quarkus.md) — los errores por capas en Quarkus: la extensión del estándar de API 3.0.0.
- [ADR-031](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-031-modulo-de-errores-por-capas-con-trazabilidad.md) — el módulo de errores por capas, con trazabilidad, y su suite de contrato.
- [ADR-034](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-034-puertos-con-implementacion-por-defecto.md) — lo duro y lo reemplazable: las reglas en el núcleo y las convenciones detrás de un puerto.
- [ADR-049](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-049-secretos-en-quarkus-y-nestjs.md) — la forma de extensión de Quarkus que sigue este repo, con `nova-java-23-secrets` como primer ejemplo.
- [ADR-045](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-045-imagen-nativa-junto-a-la-jvm.md) — la imagen nativa junto a la JVM.

## License

Eclipse Public License 2.0 — see [LICENSE](LICENSE).

Copyright © 2026 Angel Hincho.
