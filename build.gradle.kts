import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
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
    implementation("net.dv8tion", "JDA", "5.6.1")
    implementation("com.github.minndevelopment", "jda-ktx","0.12.0")

    //JetBrains Libraries
    implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.9.+")
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.10.+")
    implementation("org.jetbrains.kotlinx", "kotlinx-datetime", "0.7.1")
    implementation("org.apache.commons", "commons-text", "1.10.0")
    //implementation(compose.desktop.linux_x64)

    //Ktor - Web API Library
    implementation("io.ktor", "ktor-client-core-jvm", "3.2.3")
    implementation("io.ktor", "ktor-client-cio", "3.2.3")

    //Logging Libraries
    implementation("ch.qos.logback", "logback-classic", "1.4.14")
    implementation("ch.qos.logback", "logback-core", "1.4.14")

    //Data Storing Libraries (YAML + SQL)
    implementation("org.yaml", "snakeyaml", "1.21")
    implementation("org.mariadb.jdbc", "mariadb-java-client", "3.0.5")
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