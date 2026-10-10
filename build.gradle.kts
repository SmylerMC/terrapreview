plugins {
    java
    application
    checkstyle

    id("com.diffplug.spotless") version "8.10.3"
    id("com.github.spotbugs") version "6.5.11"
}

group = "net.smyler"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "Smyler Releases"
        url = uri("https://maven.smyler.net/releases/")
    }
}

dependencies {
    implementation("net.buildtheearth.terraminusminus:terraminusminus-core:2.2.0-1.21.8")
    compileOnly("com.google.guava:guava:33.3.1-jre")
    compileOnly("org.projectlombok:lombok:1.18.42")
    compileOnly("net.daporkchop.lib:binary:0.5.7-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    checkstyle("com.puppycrawl.tools:checkstyle:14.3.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "net.smyler.terrapreview.TerraPreview"
}

spotless {
    java {
        palantirJavaFormat()
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

