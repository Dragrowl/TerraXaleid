plugins {
    kotlin("jvm") version "1.9.20"
    application
}

group = "io.terramap"
version = "0.1.0-dev.1"

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("io.terraxaleid.core.SelfTestKt")
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
    }
}

tasks.register<JavaExec>("selftest") {
    group = "verification"
    description = "Runs the L1 self test (synthetic data only, no game files needed)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.terraxaleid.core.SelfTestKt")
    args = listOf()
}

val selftestWithSamples by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the L1 self test and also checks a real .wld/.map pair"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.terraxaleid.core.SelfTestKt")
    args = listOf("--with-samples", providers.gradleProperty("world").getOrElse(""), providers.gradleProperty("map").getOrElse(""))
}
