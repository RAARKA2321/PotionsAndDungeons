plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.compose") version "2.2.10"
    id("org.jetbrains.compose") version "1.9.3"
    application
}

val desktopTarget = providers.gradleProperty("desktopTarget").orNull
version = "1.6.0"

sourceSets.main { resources.srcDir("../app/src/main/resources") }

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.exclude("**/MainActivity.kt", "**/AndroidPlatform.kt", "**/ui/theme/**")
    }
    sourceSets.test {
        kotlin.srcDir("../app/src/test/java")
    }
}

dependencies {
    implementation(compose.desktop.common)
    runtimeOnly(if (desktopTarget == "windows") compose.desktop.windows_x64 else compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.json:json:20250517")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass = "com.example.potionsanddungeons.DesktopPlatformKt"
    applicationDefaultJvmArgs = listOf("-Dpotions.version=${project.version}")
}
