plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
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
