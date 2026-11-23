plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Pinning a toolchain would require a JDK 17 next to this machine's JDK 21/25; compile with the
// running JDK and only lower the bytecode target instead.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// The L1 core lives outside this Gradle tree so the Python / C# / Kotlin ports stay side by side.
// Point the source set at it instead of copying files around.
sourceSets {
    main {
        kotlin.srcDir("../../kotlin/src/main/kotlin")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
