plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("org.jetbrains.dokka") version "2.0.0"
    id("com.vanniktech.maven.publish") version "0.36.0"
}

repositories {
    mavenCentral()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("io.github.udhay-adithya", "ollama-kotlin", "0.1.0")

    pom {
        name.set("ollama-kotlin")
        description.set("Kotlin client for Ollama")
        inceptionYear.set("2026")
        url.set("https://github.com/Udhay-Adithya/ollama-kotlin")
        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("udhay-adithya")
                name.set("Udhay Adithya")
                url.set("https://github.com/Udhay-Adithya/")
            }
        }
        scm {
            url.set("https://github.com/Udhay-Adithya/ollama-kotlin/")
            connection.set("scm:git:git://github.com/Udhay-Adithya/ollama-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/Udhay-Adithya/ollama-kotlin.git")
        }
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("io.ktor:ktor-client-core:3.2.3")
    implementation("io.ktor:ktor-client-cio:3.2.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.ktor:ktor-client-mock:3.2.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

tasks.test {
    useJUnitPlatform()
}