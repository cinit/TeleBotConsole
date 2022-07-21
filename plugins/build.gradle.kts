plugins {
    kotlin("jvm") version "1.7.10"
    application
}

group = "cc.ioctl.tgbotplugins"
version = "unspecified"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/skija/maven")
}

dependencies {
    implementation(projects.core)
}

java {
    targetCompatibility = JavaVersion.VERSION_11
    sourceCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
    // freeCompilerArgs = listOf("-Xno-param-assertions")
    // Don't generate not-null assertions on parameters of methods accessible from Java
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
