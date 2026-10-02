plugins {
    `java-library`
    // Arma la extensión de Quarkus: su descriptor (META-INF/quarkus-extension.properties), el vínculo con el
    // módulo de deployment y el modelo de la aplicación que necesitan las pruebas de ese módulo.
    id("io.quarkus.extension")
}

description = "Extensión de Quarkus del estándar de API de Nova: responde los errores con el sobre y configura Jackson."

// Una sola fuente para la versión de Quarkus: gradle.properties.
val quarkusVersion = findProperty("quarkusPlatformVersion") as String

quarkusExtension {
    deploymentModule.set("nova-api-standard-quarkus-extension-deployment")
}

dependencies {
    // Quarkus REST (JAX-RS reactivo) - necesario para @Path, @Provider, @ServerExceptionMapper
    implementation("io.quarkus:quarkus-rest:$quarkusVersion")
    // Quarkus ARC (CDI) - necesario para @ApplicationScoped, @Singleton, @Inject
    implementation("io.quarkus:quarkus-arc:$quarkusVersion")
    // Quarkus Jackson - aporta jackson-databind + la API ObjectMapperCustomizer.
    implementation("io.quarkus:quarkus-jackson:$quarkusVersion")

    // Librería pura Nova - los tipos ApiResponse, ApiError, PageInfo, el modelo de errores por capas, etc.
    api("pe.edu.nova.java.libs:nova-api-standard:1.1.0")

    // Opcionales: cada una existe solo en el servicio que trae su extensión (Hibernate Validator, Quarkus
    // Security, Micrometer), y el módulo de deployment registra la clase que la nombra únicamente entonces.
    // Las versiones salen del BOM de Quarkus, que no viaja en el POM publicado.
    compileOnly(platform("io.quarkus:quarkus-bom:$quarkusVersion"))
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("io.quarkus.security:quarkus-security")
    compileOnly("io.micrometer:micrometer-core")

    testImplementation(platform("io.quarkus:quarkus-bom:$quarkusVersion"))
    testImplementation("io.micrometer:micrometer-core")
}

// Las tareas del plugin de Quarkus guardan el proyecto entero, y el configuration cache no lo admite. Hoy está
// apagado en gradle.properties; se declaran fuera del cache para que encenderlo no las rompa.
tasks.matching { it.name in setOf("extensionDescriptor", "validateExtension") }.configureEach {
    notCompatibleWithConfigurationCache("the Quarkus extension plugin stores the project in its tasks")
}

tasks.withType<JavaCompile>().configureEach {
    // El plugin suma el procesador de extensiones, que en Gradle avisa que no puede generar la documentación
    // de configuración; el runtime no tiene configuración propia que documentar.
    options.compilerArgs.add("-AgenerateDoc=false")
}
