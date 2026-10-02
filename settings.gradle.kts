pluginManagement {
    // La versión del plugin de extensiones de Quarkus sale de gradle.properties, junto a la de la plataforma.
    val quarkusPluginVersion: String by settings
    plugins {
        id("io.quarkus.extension") version quarkusPluginVersion
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// La raíz solo agrega los dos módulos de la extensión y no se publica: el runtime lleva el nombre del repositorio
// (ADR-039) y el deployment lo suma al final, como pide Quarkus.
rootProject.name = "nova-api-standard-quarkus-extension"

include("nova-api-standard-quarkus-extension")
include("nova-api-standard-quarkus-extension-deployment")
