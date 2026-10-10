plugins {
    `java-library`
    application
    checkstyle

    id("com.diffplug.spotless") version "8.10.3"
    id("com.github.spotbugs") version "6.5.11"
}

group = "net.smyler"
version = "0.1.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

repositories {
    mavenCentral()
    maven {
        name = "Smyler Releases"
        url = uri("https://maven.smyler.net/releases/")
    }
}

dependencies {
    api("net.buildtheearth.terraminusminus:terraminusminus-core:2.2.0-1.21.8")
    implementation("com.google.code.gson:gson:2.14.0")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.6")
    compileOnly("com.google.guava:guava:33.3.1-jre")
    compileOnly("org.projectlombok:lombok:1.18.42")
    compileOnly("net.daporkchop.lib:binary:0.5.7-SNAPSHOT")
    compileOnly("org.apache.logging.log4j:log4j-api:2.24.1")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testImplementation("org.apache.logging.log4j:log4j-core:2.24.1")
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

tasks.named<JavaCompile>("compileJava") {
    options.release = 21
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

val webTestDir = layout.projectDirectory.dir("web-test")

tasks.register("browserTest", Exec::class) {
    group = "verification"
    description = "Runs the in-browser tests for the web UI with Mocha and Puppeteer."
    workingDir(webTestDir)
    commandLine("node", "run.mjs")
}

tasks.named("check") {
    dependsOn("browserTest")
}

