import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    application
}

group = "de.miraculixx"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
    google()
}

dependencies {
    //JDA - Discord API Wrapper
    implementation("net.dv8tion:JDA:6.4.2")
    implementation("club.minnced:jda-ktx:0.14.2")

    //JetBrains Libraries
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
    implementation("org.jetbrains.kotlinx:kandy-lets-plot:0.8.4")
    implementation("org.jetbrains.kotlinx:dataframe-core:1.0.0-Beta5")
    implementation("org.apache.commons:commons-text:1.15.0")
    //implementation(compose.desktop.linux_x64)

    //Ktor - Web API Library
    implementation("io.ktor:ktor-client-core:3.5.0")
    implementation("io.ktor:ktor-client-cio:3.5.0")

    //Logging Libraries
    runtimeOnly("org.slf4j:slf4j-simple:2.0.18")

    //Data Storing Libraries (YAML + SQL)
    implementation("org.yaml:snakeyaml:2.6")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.8")
}

application {
    mainClass.set("de.miraculixx.mgames.MainKt")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}
