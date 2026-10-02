plugins {
    `java-library`
}

description = "Pasos de build de la extensión del estándar de API para Quarkus: registran los beans y los records del sobre."

// Una sola fuente para la versión de Quarkus: gradle.properties.
val quarkusVersion = findProperty("quarkusPlatformVersion") as String

dependencies {
    implementation(project(":nova-api-standard-quarkus-extension"))
    // Por cada extensión de la que depende el runtime, su deployment: lo exige el plugin de extensiones.
    implementation("io.quarkus:quarkus-arc-deployment:$quarkusVersion")
    implementation("io.quarkus:quarkus-rest-deployment:$quarkusVersion")
    implementation("io.quarkus:quarkus-jackson-deployment:$quarkusVersion")
    // Genera la lista de pasos de build que Quarkus lee al construir la aplicación.
    annotationProcessor("io.quarkus:quarkus-extension-processor:$quarkusVersion")

    // Cada prueba arma una aplicación Quarkus mínima con la extensión, como lo hace un servicio.
    testImplementation("io.quarkus:quarkus-junit-internal:$quarkusVersion")
    // QuarkusUnitTest inyecta la instancia de prueba con ArC, el CDI de Quarkus.
    testImplementation("io.quarkus:quarkus-arc-deployment:$quarkusVersion")
    // El sobre viaja como JSON: un servicio real declara quarkus-rest-jackson, y las pruebas también.
    testImplementation("io.quarkus:quarkus-rest-jackson:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-rest-jackson-deployment:$quarkusVersion")
    testImplementation("io.rest-assured:rest-assured:5.5.6")
    // Las extensiones opcionales que activan mappers y el contador: cada prueba las ve como las vería un servicio.
    testImplementation("io.quarkus:quarkus-hibernate-validator:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-hibernate-validator-deployment:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-security:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-security-deployment:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-micrometer:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-micrometer-deployment:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-opentelemetry:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-opentelemetry-deployment:$quarkusVersion")
}

tasks.withType<JavaCompile>().configureEach {
    // La documentación de configuración la genera el procesador solo para builds de Maven; en Gradle avisa
    // que no puede. Esta extensión no tiene configuración propia que documentar.
    options.compilerArgs.add("-AgenerateDoc=false")
}

tasks.named<Test>("test") {
    // El plugin de Quarkus le pasa a esta tarea el modelo de la aplicación desde un doFirst que lee el
    // proyecto, y con el configuration cache ese modelo no llega: QuarkusUnitTest no arranca.
    notCompatibleWithConfigurationCache("the Quarkus extension plugin builds the test application model from the project")
    // Quarkus valida al armar cada aplicación la imagen del builder nativo, que en un servicio llega con
    // las propiedades de la plataforma. El modelo de pruebas de Gradle no las trae, y la prueba no la usa.
    systemProperty("platform.quarkus.native.builder-image", "mandrel")
    // QuarkusUnitTest arranca la aplicación, su servidor HTTP y sus pruebas en un mismo proceso.
    maxHeapSize = "1g"
    // Quarkus ajusta la configuración de módulos de Java para cada extensión y avisa si no puede hacerlo.
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
}
