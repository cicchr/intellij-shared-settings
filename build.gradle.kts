plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "net.cicchiello.intellij.settings"
version = "1.0.5"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2024.1")
    type.set("IC") // Target IDE Platform

    plugins.set(listOf("Git4Idea"))
}

dependencies {
    compileOnly ("org.projectlombok:lombok:1.18.32")

    annotationProcessor ("org.projectlombok:lombok:1.18.32")
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
