import org.gradle.api.publish.maven.MavenPublication
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension

plugins {
    // reusable-sbom.yml genera el SBOM con cyclonedxBom; sin el plugin cae a un
    // fallback que falla al escribir build/reports/bom.json.
    id("org.cyclonedx.bom") version "3.4.1"
    // OWASP dependency-check. NECESARIO porque reusable-owasp-check.yml corre
    // `./gradlew dependencyCheckAnalyze`. Sin este plugin aplicado, el job
    // falla con "Task 'dependencyCheckAnalyze' not found" (verificado en CI
    // run 29425144260). El bloque dependencyCheck { } de más abajo configura
    // autoUpdate=false + data.directory para usar el mirror NVD pre-construido
    // por nova-devops/nvd-mirror-update.yml en lugar de un full sync contra
    // NVD (que tarda 5-15 min CON key y 18+ min SIN key con rate-limit 429).
    // La versión DEBE estar sincronizada con la del nvd-updater de nova-devops
    // para garantizar compatibilidad del formato H2/Lucene.
    // Se aplica en cada módulo, no en la raíz: la raíz no tiene dependencias que analizar.
    id("org.owasp.dependencycheck") version "12.2.2" apply false
}

val junitVersion = "6.0.3"

subprojects {
    // La raíz solo agrega módulos. Los dos publican, así que comparten el build; lo que es de uno solo
    // (el plugin de extensiones de Quarkus, sus dependencias) vive en su propio build.gradle.kts.
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    // reusable-build-gradle.yml corre `./gradlew checkstyleMain`.
    apply(plugin = "checkstyle")
    apply(plugin = "org.cyclonedx.bom")
    apply(plugin = "org.owasp.dependencycheck")

    group = rootProject.findProperty("group") as String
    version = rootProject.findProperty("version") as String

    repositories {
        mavenLocal()
        mavenCentral()
        // GitHub Packages de nova-java-api-standard (la lib pura que consumimos).
        // NO usamos el repo URL de esta extensión porque nova-api-standard NO
        // está publicado aquí: está en su propio repo, como cualquier lib Maven.
        // NOVA_PACKAGES_READ_TOKEN es el secret que permite cross-repo reads
        // en CI (GITHUB_TOKEN no sirve para leer packages de otro repo).
        // Fallback automático a GITHUB_TOKEN si NOVA_PACKAGES_READ_TOKEN no está.
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

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
        "testImplementation"("org.junit.platform:junit-platform-launcher:$junitVersion")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:all", "-quiet")
            encoding = "UTF-8"
            charSet = "UTF-8"
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    extensions.configure<CheckstyleExtension> {
        // Misma config que el resto de repos Nova (ver config/checkstyle/checkstyle.xml).
        // Solo lint del main sourceSet; los tests usan wildcards legítimos (Assertions.*,
        // jqwik.*) que AvoidStarImport marcaría como error.
        sourceSets = listOf(extensions.getByType<SourceSetContainer>()["main"])
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    }

    // Versiones parcheadas de dependencias que el OWASP gate marca con CVSS >= 7. Las cuatro
    // llegan por la herramienta checkstyle; son las mismas que usan los starters de Spring Boot.
    // Verificadas contra la GitHub Advisory Database el 2026-09-27.
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.apache.httpcomponents" && requested.name.startsWith("httpcore")) {
                useVersion("4.4.16")
                because("CVE-2026-54428, CVE-2026-54399 require httpcore 4.4.16+")
            }
            if (requested.group == "org.apache.httpcomponents.core5" && requested.name.startsWith("httpcore5")) {
                useVersion("5.4.3")
                because("CVE-2026-54399 requires httpcore5 5.4.3+")
            }
            if (requested.group == "commons-beanutils" && requested.name == "commons-beanutils") {
                useVersion("1.11.0")
                because("CVE-2025-48734 requires commons-beanutils 1.11.0+")
            }
            if (requested.group == "org.codehaus.plexus" && requested.name == "plexus-utils") {
                useVersion("3.6.1")
                because("CVE-2025-67030 requires plexus-utils 3.6.1+")
            }
        }
    }

    extensions.configure<DependencyCheckExtension> {
        // CRÍTICO: reusable-owasp-check.yml descarga un mirror NVD pre-construido
        // (~119MB) desde ahincho/nova-shared-02-pipelines (releases/tag/nvd-mirror), reconstruido
        // diario por nvd-mirror-update.yml. Con autoUpdate=true (default), el plugin
        // IGNORA ese mirror y dispara un full sync contra NVD (366k records), que
        // tarda 5-15 min CON key y 18+ min SIN key (rate-limited HTTP 429). El
        // mirror + autoUpdate=false da el mismo nivel de detección (<24h staleness)
        // en <1 min. Documentado en doc 07 §8 (Causa raíz del OWASP lento).
        autoUpdate = false
        data.directory = System.getenv("NOVA_OWASP_DATA_DIR")
            ?: "${System.getProperty("user.home")}/.dependency-check-data"
        nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
        failBuildOnCVSS = (System.getenv("NOVA_OWASP_FAIL_ON_CVSS") ?: "11").toFloat()
        skipConfigurations = listOf("testCompileClasspath", "testRuntimeClasspath")
        formats = listOf("HTML", "JSON")
    }

    extensions.configure<PublishingExtension> {
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

    extensions.configure<SigningExtension> {
        val gpgKeyId: String? = System.getenv("GPG_SIGNING_KEY_ID")
        val gpgKey: String? = System.getenv("GPG_SIGNING_KEY")
        val gpgPassword: String? = System.getenv("GPG_SIGNING_PASSWORD")

        if (gpgKeyId != null && gpgKey != null) {
            useInMemoryPgpKeys(gpgKeyId, gpgKey, gpgPassword ?: "")
            sign(extensions.getByType<PublishingExtension>().publications)
        }
    }
}
