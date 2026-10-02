# nova-api-standard-quarkus-extension

> Extensión de Quarkus que conecta [`nova-api-standard`](https://github.com/ahincho/nova-java-01-api-standard),
> la librería pura y sin framework, con Quarkus (`quarkus-rest` + `quarkus-arc`).

## Módulos

Desde la 3.0.0 es una extensión de Quarkus completa, con su módulo de deployment
([ADR-050](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-050-errores-por-capas-en-quarkus.md)):

| Módulo | `artifactId` | Qué tiene |
|---|---|---|
| runtime | `nova-api-standard-quarkus-extension` | los beans de la extensión: el mapper de excepciones y el customizer de Jackson |
| deployment | `nova-api-standard-quarkus-extension-deployment` | los pasos de build: registran los beans y los records del sobre, y los indexan para Quarkus REST |

Los dos salen con la misma versión y el mismo `groupId`, `pe.edu.nova.java.starters`. **El servicio solo
declara el runtime**: Quarkus resuelve el deployment por su cuenta, con la misma versión.

## Qué hace

| Pieza | Función |
|---|---|
| `ApiExceptionMapper` (`@ServerExceptionMapper`) | Captura cualquier `Throwable` no controlado y lo serializa como `ApiResponse` JSON consistente con el contrato `api-standard`. |
| `ApiObjectMapperCustomizer` (`@Singleton ObjectMapperCustomizer`) | Registra `JavaTimeModule` y deshabilita `WRITE_DATES_AS_TIMESTAMPS` y `FAIL_ON_EMPTY_BEANS` para serializar correctamente `Instant`/`LocalDateTime` y beans vacíos. |

El módulo de deployment corre al construir la aplicación, nunca al arrancarla, y hace tres cosas:

- **Registra los beans** con `AdditionalBeanBuildItem` y los indexa para Quarkus REST. Por eso **el servicio
  ya no necesita `quarkus.index-dependency`**: con la dependencia alcanza. Quien todavía lo declara puede
  quitarlo.
- **Registra los records del sobre para la reflexión** (`ApiResponse`, `ApiError`, `ApiMetadata`,
  `ApiLink`, `RateLimitInfo` y `PageInfo`). El sobre se arma dentro de un mapper de excepciones, donde el
  análisis de la imagen nativa no lo ve: sin este paso, Jackson no puede leer sus componentes y cada
  respuesta de error termina en 500.
- **Lista la extensión** como `nova-api-standard` al arrancar.

## Estado

| Campo | Valor |
|---|---|
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
}
```

El sobre viaja como JSON, así que el servicio declara `quarkus-rest-jackson`, como cualquier servicio
Quarkus que responde JSON.

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

### 3. Beneficios automáticos (sin código extra)

- Si `findById` lanza `IllegalArgumentException`, el `ApiExceptionMapper`
  automáticamente retorna `400 Bad Request` con body:
  ```json
  {
    "success": false,
    "status": 400,
    "data": null,
    "errors": [{"code": "BAD_REQUEST", "message": "user not found: 42"}]
  }
  ```
- Si ocurre una excepción inesperada (`RuntimeException`), retorna
  `500 Internal Server Error` con mensaje genérico (NO se filtra el detalle
  técnico al cliente).
- Los campos `Instant` en `ApiMetadata.timestamp` se serializan como
  ISO-8601 (`2026-07-14T12:34:56Z`), no como timestamp numérico.

### 4. Override con mappers más específicos (opcional)

Si tu app quiere mapear un tipo específico de excepción con un código HTTP
distinto al default del `ApiExceptionMapper`, declara tu propio mapper. JAX-RS
lo elegirá por especificidad:

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

## Stack tecnológico

| Pieza | Versión | Por qué |
|---|---|---|
| Quarkus | 3.33.3.3 LTS | Pin Nova workspace; soporta Java 25 |
| `quarkus-rest` | (via BOM) | JAX-RS reactivo, `@Path`, `@Provider` |
| `quarkus-arc` | (via BOM) | CDI: `@ApplicationScoped`, `@Singleton`, `@Inject` |
| `quarkus-jackson` | (via BOM) | Aporta `ObjectMapperCustomizer` + Jackson al compileClasspath |
| `nova-api-standard` | 1.0.2 | Tipos puros (`ApiResponse`, `ApiError`, etc.) — transitivo |
| Java | 25 | LTS, coincide con la build matrix de Nova |
| JUnit | 6.0.3 | Mismo que el resto del meta-framework |
| OWASP plugin | 12.2.2 | Fail build on CVSS >= 7 (configurable) |
| CycloneDX plugin | 3.4.1 | SBOM generation |
| Gradle | 9.5.1 | Wrapper |
| Gradle Config Cache | **disabled** | Bug conocido de Quarkus 3.x con config cache; re-habilitar cuando Gradle/Quarkus estabilicen |

## Testing

Cada módulo prueba lo que es suyo:

- **El runtime** tiene pruebas unitarias con JUnit puro: los códigos y los mensajes del
  `ApiExceptionMapper` y la configuración que el `ApiObjectMapperCustomizer` le aplica al `ObjectMapper`.
- **El deployment** tiene una prueba de los pasos de build y pruebas con `QuarkusUnitTest`, que arma una
  aplicación Quarkus mínima con la extensión como única dependencia, sin `quarkus.index-dependency`, y
  comprueba por HTTP que el servicio responde como antes de separar la extensión en dos módulos.

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

**Prerrequisito:** Para compilar localmente necesitas `nova-api-standard:1.0.2`
disponible. Como GitHub Packages requiere auth incluso para paquetes public,
tienes dos opciones:

**Opción A (recomendada):** Publicar `nova-api-standard` a Maven Local primero:

```bash
# Desde el repo de nova-java-01-api-standard, con la versión 1.0.2 (gradle.properties trae un SNAPSHOT):
cd ../nova-java-01-api-standard
./gradlew publishToMavenLocal -Pversion=1.0.2

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
./gradlew publishToMavenLocal -Pversion=1.0.2

# Publicar a GitHub Packages (requiere GITHUB_TOKEN)
GITHUB_TOKEN=ghp_xxx ./gradlew publish
```

## Documentación relacionada

- [ADR-050](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-050-errores-por-capas-en-quarkus.md) — la extensión de Quarkus del estándar de API, con su módulo de deployment.
- [ADR-049](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-049-secretos-en-quarkus-y-nestjs.md) — la forma de extensión de Quarkus que sigue este repo, con `nova-java-23-secrets` como primer ejemplo.
- [ADR-045](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-045-imagen-nativa-junto-a-la-jvm.md) — la imagen nativa junto a la JVM.

## License

Eclipse Public License 2.0 — see [LICENSE](LICENSE).

Copyright © 2026 Angel Hincho.
