plugins {
    java
    `maven-publish`
    checkstyle
    pmd
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.spotless)
}

group = "dev.cinematics"
val requestedReleaseVersion = providers.gradleProperty("releaseVersion")
val releaseVersion = requestedReleaseVersion.orElse("0.0.0-local")
version = releaseVersion.get()

val mainSourceSet = sourceSets.getByName("main")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

pmd {
    toolVersion = "7.16.0"
    ruleSetFiles = files(rootProject.file("config/pmd/pmd.xml"))
    isConsoleOutput = true
    isIgnoreFailures = false
}

spotless {
    java {
        googleJavaFormat("1.28.0")
        targetExclude("build/**")
    }
}

spotbugs {
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.HIGH)
    ignoreFailures.set(false)
}

val apiJar =
    tasks.register<org.gradle.api.tasks.bundling.Jar>("apiJar") {
        archiveBaseName.set("cinematics-api")
        archiveClassifier.set("")
        dependsOn(tasks.classes)
        from(mainSourceSet.output) {
            include("dev/cinematics/api/**")
        }
    }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly(libs.paper.api)
    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    implementation(libs.packetevents.spigot)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("cinematics")
    archiveClassifier.set("")
    mustRunAfter(tasks.jar)
    relocate("com.github.retrooper.packetevents", "dev.cinematics.libs.packetevents")
    relocate("io.github.retrooper.packetevents", "dev.cinematics.libs.io.packetevents")
    relocate("net.kyori", "dev.cinematics.libs.kyori")
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
    dependsOn(apiJar)
}

tasks.runServer {
    minecraftVersion("1.21.11")
    runDirectory.set(layout.projectDirectory.dir("run"))
}
