import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    // reusable-build-gradle.yml corre `./gradlew checkstyleMain`.
    id("checkstyle")
    // reusable-sbom.yml genera el SBOM con cyclonedxBom; sin el plugin cae a un
    // fallback que falla al escribir build/reports/bom.json.
    id("org.cyclonedx.bom") version "3.2.4"
    // Genera META-INF/jandex.idx en el JAR para que Quarkus descubra
    // @ServerExceptionMapper y @Singleton en apps consumidoras sin necesidad
    // de extension processor ni @BuildStep. Plugin compatible con Gradle 9.x.
    id("org.kordamp.gradle.jandex") version "2.3.0"
    // OWASP dependency-check. NECESARIO porque reusable-owasp-check.yml corre
    // `./gradlew dependencyCheckAnalyze`. Sin este plugin aplicado, el job
    // falla con "Task 'dependencyCheckAnalyze' not found" (verificado en CI
    // run 29425144260). El bloque dependencyCheck { } mas abajo configura
    // autoUpdate=false + data.directory para usar el mirror NVD pre-construido
    // por nova-devops/nvd-mirror-update.yml en lugar de un full sync contra
    // NVD (que tarda 5-15 min CON key y 18+ min SIN key con rate-limit 429).
    // La version DEBE estar sincronizada con la del nvd-updater de nova-devops
    // para garantizar compatibilidad del formato H2/Lucene.
    id("org.owasp.dependencycheck") version "12.2.2"
}

group = findProperty("group") as String
version = findProperty("version") as String

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenLocal()
    mavenCentral()
    // GitHub Packages de nova-java-api-standard (la lib pura que consumimos).
    // NO usamos el repo URL de este extension porque nova-api-standard NO
    // esta publicado aqui — esta en su propio repo, como cualquier lib Maven.
    // NOVA_PACKAGES_READ_TOKEN es el secret que permite cross-repo reads
    // en CI (GITHUB_TOKEN no sirve para leer packages de otro repo).
    // Fallback automatico a GITHUB_TOKEN si NOVA_PACKAGES_READ_TOKEN no esta.
    maven {
        name = "GitHubPackages-Nova-ApiStandard"
        url = uri("https://maven.pkg.github.com/ahincho/nova-java-01-api-standard")
        val token = System.getenv("NOVA_PACKAGES_READ_TOKEN")
            ?: System.getenv("GITHUB_TOKEN")
        if (!token.isNullOrBlank()) {
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "x-access-token"
                password = token
            }
        }
    }
}

val junitVersion = "6.0.0"

dependencies {
    // Quarkus REST (JAX-RS reactivo) - necesario para @Path, @Provider, @ServerExceptionMapper
    implementation("io.quarkus:quarkus-rest:3.33.2.1")
    // Quarkus ARC (CDI) - necesario para @ApplicationScoped, @Singleton, @Inject
    implementation("io.quarkus:quarkus-arc:3.33.2.1")
    // Quarkus Jackson - aporta jackson-databind + la API ObjectMapperCustomizer.
    implementation("io.quarkus:quarkus-jackson:3.33.2.1")

    // Libreria pura Nova - los tipos ApiResponse, ApiError, PageInfo, etc.
    api("pe.edu.nova.java.libs:nova-api-standard:1.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.platform:junit-platform-launcher:$junitVersion")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all", "-quiet")
        encoding = "UTF-8"
        charSet = "UTF-8"
    }
}

tasks.test {
    useJUnitPlatform()
    // @QuarkusTest requiere que el contexto Quarkus arranque por test class.
    // Forks=1 es seguro y suficiente para una libreria sin state global mutable.
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

checkstyle {
    // Misma config que el resto de repos Nova (ver config/checkstyle/checkstyle.xml).
    // Solo lint del main sourceSet; los tests usan wildcards legitimos (Assertions.*,
    // jqwik.*) que AvoidStarImport marcaria como error.
    sourceSets = listOf(project.sourceSets.main.get())
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

dependencyCheck {
    // CRITICO: reusable-owasp-check.yml descarga un mirror NVD pre-construido
    // (~119MB) desde ahincho/nova-shared-02-pipelines (releases/tag/nvd-mirror), reconstruido
    // diario por nvd-mirror-update.yml. Con autoUpdate=true (default), el plugin
    // IGNORA ese mirror y dispara un full sync contra NVD (366k records), que
    // tarda 5-15 min CON key y 18+ min SIN key (rate-limited HTTP 429). El
    // mirror + autoUpdate=false da el mismo nivel de deteccion (<24h staleness)
    // en <1 min. Documentado en doc 07 §8 (Causa raiz del OWASP lento).
    autoUpdate = false
    data.directory = System.getenv("NOVA_OWASP_DATA_DIR")
        ?: "${System.getProperty("user.home")}/.dependency-check-data"
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
    failBuildOnCVSS = (System.getenv("NOVA_OWASP_FAIL_ON_CVSS") ?: "11").toFloat()
    skipConfigurations = listOf("testCompileClasspath", "testRuntimeClasspath")
    formats = listOf("HTML", "JSON")
}

// Quarkus platform block TEMPORALMENTE removido para aislar el publish fantasma.

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ahincho/nova-java-10-api-standard-quarkus-extension")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

signing {
    val gpgKeyId: String? = System.getenv("GPG_SIGNING_KEY_ID")
    val gpgKey: String? = System.getenv("GPG_SIGNING_KEY")
    val gpgPassword: String? = System.getenv("GPG_SIGNING_PASSWORD")

    if (gpgKeyId != null && gpgKey != null) {
        useInMemoryPgpKeys(gpgKeyId, gpgKey, gpgPassword ?: "")
        sign(publishing.publications)
    }
}