import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
    application
}

group = "cc.ioctl.neoauth2tgbot"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.telegram:telegrambots:6.0.1")
    implementation("org.telegram:telegrambots-abilities:6.0.1")
    testImplementation(kotlin("test"))
}

java {
    targetCompatibility = JavaVersion.VERSION_11
    sourceCompatibility = JavaVersion.VERSION_11
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
    // freeCompilerArgs = listOf("-Xno-param-assertions")
    // Don't generate not-null assertions on parameters of methods accessible from Java
}

application {
    mainClass.set("cc.ioctl.neoauth2tgbot.ServerMain")
}
