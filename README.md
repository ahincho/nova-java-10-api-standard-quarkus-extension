# nova-quarkus-api-ext

> Quarkus extension (coloquial, sin `@BuildStep`) que bridgea
> [`nova-api-standard`](https://github.com/ahincho/nova-java-01-api-standard) —
> libreria pura framework-agnostic — con el mundo Quarkus
> (`quarkus-rest` + `quarkus-arc`).

## Que hace

Esta extension aporta dos piezas framework-specific que permiten usar los
tipos de `nova-api-standard` (`ApiResponse`, `ApiError`, `PageInfo`,
`ApiMetadata`, etc.) en una aplicacion Quarkus sin escribir codigo boilerplate:

| Pieza | Funcion |
|---|---|
| `ApiExceptionMapper` (`@Provider`) | Captura cualquier `Throwable` no controlado y lo serializa como `ApiResponse` JSON consistente con el contrato `api-standard`. |
| `ApiObjectMapperCustomizer` (`@Singleton ObjectMapperCustomizer`) | Registra `JavaTimeModule` y deshabilita `WRITE_DATES_AS_TIMESTAMPS` y `FAIL_ON_EMPTY_BEANS` para serializar correctamente `Instant`/`LocalDateTime` y records vacios. |

Ambas son **extensiones coloquiales** (ver
[`docs/java/07-quarkus-analisis-adopcion.md`](../docs/java/07-quarkus-analisis-adopcion.md)
seccion 4): CDI las descubre automaticamente en build-time via Jandex, sin
requerir `META-INF/services/*` ni `@BuildStep`.

## Estado

| Campo | Valor |
|---|---|
| Version | `1.0.0` |
| Quarkus | `3.33.3.3` LTS (pin Nova workspace) |
| Java | `25` |
| GroupId | `pe.edu.nova.java.starters` |
| ArtifactId | `nova-quarkus-api-ext` |
| Registry | GitHub Packages (`maven.pkg.github.com/ahincho/nova-java-10-api-standard-quarkus-extension`) |
| Framework | Quarkus (alternativa a Spring Boot) |

> **Nota sobre naming:** el repo de GitHub se llama
> `nova-java-api-standard-quarkus-extension` (con prefijo `nova-java-` por
> consistencia organizacional) pero el **artifactId de Maven es
> `nova-quarkus-api-ext`** (corto, siguiendo la convencion Nova: el
> `groupId` ya incluye `java` y `starters` asi que el artifactId se enfoca
> en el rol). Es paralelo a `nova-api-standard-starter` para el starter
> Spring Boot equivalente.
>
> **Por que tan corto?** GitHub Packages Maven rechaza PUTs del plugin
> `maven-publish` de Gradle cuando el artifactId supera cierto limite de
> longitud (~35 chars). El nombre `nova-java-api-standard-quarkus-extension`
> (39 chars) producia "paquetes fantasma": metadata actualizada pero sin
> artifacts descargables. Ver `docs/java/07-quarkus-analisis-adopcion.md`
> seccion de causa raiz.

## Como consumirla desde una app Quarkus

### 1. Agregar la dependencia

`build.gradle.kts`:

```kotlin
dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.37.2"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-arc")

    // Esta extension
    implementation("pe.edu.nova.java.starters:nova-quarkus-api-ext:1.0.0")

    // Transitiva: nova-api-standard ya viene incluida
}
```

### 2. Usar los tipos `api-standard` en tus recursos JAX-RS

```java
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    @GET
    @Path("/{id}")
    public ApiResponse<UserDto> findById(@PathParam("id") String id) {
        UserDto user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + id));
        return ApiResponse.ok(user);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public ApiResponse<UserDto> create(CreateUserRequest request) {
        // ...
        return ApiResponse.created(newUser);
    }
}
```

### 3. Beneficios automaticos (sin codigo extra)

- Si `findById` lanza `IllegalArgumentException`, el `ApiExceptionMapper`
  automaticamente retorna `400 Bad Request` con body:
  ```json
  {
    "success": false,
    "status": 400,
    "data": null,
    "errors": [{"code": "BAD_REQUEST", "message": "user not found: 42"}]
  }
  ```
- Si ocurre una excepcion inesperada (`RuntimeException`), retorna
  `500 Internal Server Error` con mensaje generico (NO se filtra el detalle
  tecnico al cliente).
- Los campos `Instant` en `ApiMetadata.timestamp` se serializan como
  ISO-8601 (`2026-07-14T12:34:56Z`), no como timestamp numerico.

### 4. Override con mappers mas especificos (opcional)

Si tu app quiere mapear un tipo especifico de excepcion con un codigo HTTP
distinto al default del `ApiExceptionMapper`, declara tu propio mapper. JAX-RS
lo elegira por especificidad:

```java
@Provider
public class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException ex) {
        ApiResponse<Object> body = ApiResponse.builder()
                .status(422)
                .error(ApiError.validationError(
                    ex.getPropertyPath().toString(),
                    ex.getMessage()))
                .build();
        return Response.status(422).entity(body).build();
    }
}
```

## Stack tecnologico

| Pieza | Version | Por que |
|---|---|---|
| Quarkus | 3.33.3.3 LTS | Pin Nova workspace; soporta Java 25 |
| `quarkus-rest` | (via BOM) | JAX-RS reactivo, `@Path`, `@Provider` |
| `quarkus-arc` | (via BOM) | CDI: `@ApplicationScoped`, `@Singleton`, `@Inject` |
| `quarkus-jackson` | (via BOM) | Aporta `ObjectMapperCustomizer` + Jackson al compileClasspath |
| `nova-api-standard` | 1.0.0 | Tipos puros (`ApiResponse`, `ApiError`, etc.) — transitivo |
| Java | 25 | LTS, coincide con la build matrix de Nova |
| JUnit | 6.0.0 | Mismo que el resto del meta-framework |
| OWASP plugin | 12.2.2 | Fail build on CVSS >= 7 (configurable) |
| CycloneDX plugin | 3.2.4 | SBOM generation |
| Gradle | 9.5.1 | Wrapper |
| Gradle Config Cache | **disabled** | Bug conocido de Quarkus 3.x con config cache; re-habilitar cuando Gradle/Quarkus estabilicen |

## Testing

**Este repo NO contiene tests `@QuarkusTest`.** Solo tests unitarios (JUnit puro)
del `ApiExceptionMapper` y del `ApiObjectMapperCustomizer`. La justificacion:

1. **El plugin `io.quarkus` no esta aplicado al proyecto.** Aplicarlo haria
   que el extension se vuelva "Quarkus-aware" (genera `quarkus-app/quarkus-run.jar`,
   configura extension metadata, etc.), lo cual rompe la nocion de que esta
   libreria es solo un bundle de CDI beans + JAX-RS providers.

2. **Los tests unitarios validan la logica del mapper** sin requerir Quarkus
   corriendo. Cubren todos los paths: `IllegalArgumentException -> 400`,
   `SecurityException -> 403`, `RuntimeException -> 500`, mensaje vacio ->
   class name, etc.

3. **La validacion end-to-end** (que el mapper se descubre via CDI/Jandex, que
   el customizer se aplica al ObjectMapper global) se hace en
   [`examples/code-with-quarkus/`](../../examples/code-with-quarkus/) — el
   proyecto de ejemplo que SI aplica el plugin `io.quarkus` y por tanto puede
   correr `@QuarkusTest`. Ese proyecto se adaptara en Fase 0 para consumir
   este extension y servir como integration test vivo.

Para correr los tests unitarios localmente:

```bash
./gradlew test
```

10 tests ejecutan en ~5 segundos.

## CI/CD

Workflows en `.github/workflows/`:

- `ci.yml` — pull request: ejecuta build, matrix build (Java 25), OWASP, SBOM, SonarCloud.
- `release-please.yml` — push a `main`: abre PR de release automatico cuando detecta commits convenciones.
- `publish-on-tag.yml` — push de tag `vX.Y.Z`: publica el artefacto a GitHub Packages.

El paquete se publica como `public` porque el repo es `public` y
`NOVA_PACKAGE_VISIBILITY` no esta configurada (default `public`).

## Desarrollo local

**Prerrequisito:** Para compilar localmente necesitas `nova-api-standard:1.0.0`
disponible. Como GitHub Packages requiere auth incluso para paquetes public,
tienes dos opciones:

**Opcion A (recomendada):** Publicar `nova-api-standard` a Maven Local primero:

```bash
# Desde el repo de nova-java-api-standard, con gradle.properties version=1.0.0:
cd ../nova-java-api-standard
./gradlew publishToMavenLocal

# Volver a este repo y compilar normalmente (sin mavenLocal, Gradle busca en Maven Local
# porque esta en el path por default, no requiere declaracion):
cd ../nova-java-api-standard-quarkus-extension
./gradlew compileJava
```

**Opcion B:** Setear `GITHUB_TOKEN` en el shell:

```bash
export GITHUB_TOKEN=ghp_xxx
export GITHUB_ACTOR=tu-usuario
./gradlew compileJava
```

Una vez que la dep esta disponible:

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

## Documentacion relacionada

- [`docs/java/07-quarkus-analisis-adopcion.md`](../docs/java/07-quarkus-analisis-adopcion.md) — analisis de adopcion Quarkus en Nova Platform (seccion 4 explica la diferencia entre extension coloquial / real / BOM / codestart).
- [`docs/java/08-ddd-utils-y-bus-multi-framework.md`](../docs/java/08-ddd-utils-y-bus-multi-framework.md) — siguiente paso: DDD + Bus para Spring Boot y Quarkus.
- [`docs/java/09-scaffolding-quarkus-archetypes-y-codestarts.md`](../docs/java/09-scaffolding-quarkus-archetypes-y-codestarts.md) — estrategia de scaffolding.
- [`docs/java/06-semantic-versioning-en-java.md`](../docs/java/06-semantic-versioning-en-java.md) — semver, release-please, FP registry.

## License

Eclipse Public License 2.0 — see [LICENSE](LICENSE).

Copyright © 2026 Angel Hincho.
