import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "1.7.10"
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "cc.ioctl.telebotconsole"
version = "1.0"

val jarMainClassName = "cc.ioctl.telebot.BotStartupMain"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/skija/maven")
}

dependencies {
    api("com.google.code.gson:gson:2.9.0")
    implementation("org.jline:jline:3.21.0")
    api("org.xerial:sqlite-jdbc:3.39.3.0")
    implementation("org.jetbrains:annotations:23.0.0")
    api("com.moandjiezana.toml:toml4j:0.7.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    api(projects.libs.mmkv)
    api(projects.common)
    val skijaArtifact = "skija-linux"
    val skijaVersion = "0.93.1"
    api("org.jetbrains.skija:$skijaArtifact:$skijaVersion")
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

tasks.shadowJar {
    manifest.attributes.apply {
        put("Implementation-Version", archiveVersion)
        put("Main-Class", jarMainClassName)
    }
}

val generateJniHeaders: Task by tasks.creating {
    group = "build"
    dependsOn(tasks.getByName("compileKotlin"))
    dependsOn(tasks.getByName("compileJava"))

    // For caching
    inputs.dir("src/main/kotlin")
    inputs.dir("src/main/java")
    outputs.dir("src/main/cpp/src/jni")

    doLast {
        val javaHome = org.gradle.internal.jvm.Jvm.current().javaHome
        val javap = javaHome.resolve("bin").walk().firstOrNull {
            it.name.startsWith("javap")
        }?.absolutePath ?: error("javap not found")
        val javac = javaHome.resolve("bin").walk().firstOrNull {
            it.name.startsWith("javac")
        }?.absolutePath ?: error("javac not found")

        val buildDirKotlin = file("build/classes/kotlin/main")
        val buildDirJava = file("build/classes/java/main")
        val buildDirs: Array<File> = arrayOf(buildDirKotlin, buildDirJava)
        val tmpDir = file("build/tmp/jvmJni").apply { mkdirs() }

        val bodyExtractingRegex = """^.+\Rpublic \w* ?class ([^\s]+).*\{\R((?s:.+))\}\R$""".toRegex()
        val nativeMethodExtractingRegex = """.*\bnative\b.*""".toRegex()

        buildDirs.forEach WalkDir@{ classDir ->
            classDir.walkTopDown()
                .filter { "META" !in it.absolutePath }
                .forEach WalkFile@{ file ->
                    if (!file.isFile) return@WalkFile

                    val output = ByteArrayOutputStream().use {
                        project.exec {
                            commandLine(
                                javap,
                                "-private",
                                "-cp",
                                buildDirKotlin.absolutePath,
                                "-cp",
                                buildDirJava.absolutePath,
                                file.absolutePath
                            )
                            standardOutput = it
                        }.assertNormalExitValue()
                        it.toString()
                    }

                    val (qualifiedName, methodInfo) = bodyExtractingRegex.find(output)?.destructured ?: return@WalkFile

                    val lastDot = qualifiedName.lastIndexOf('.')
                    val packageName = qualifiedName.substring(0, lastDot)
                    val className = qualifiedName.substring(lastDot + 1, qualifiedName.length)

                    val nativeMethods =
                        nativeMethodExtractingRegex.findAll(methodInfo).map { it.groups }
                            .flatMap { it.asSequence().mapNotNull { group -> group?.value } }.toList()
                    if (nativeMethods.isEmpty()) return@WalkFile

                    val source = buildString {
                        val innerClassUsed = HashSet<String>(1)
                        appendLine("package $packageName;")
                        appendLine("public class $className {")
                        for (method in nativeMethods) {
                            if ("()" in method) {
                                appendLine(method.replace('$', '.'))
                                // check if the method return type is an inner class
                                val start = method.indexOf("$packageName.$className$")
                                if (start != -1) {
                                    val end = method.indexOf(' ', start)
                                    if (end != -1) {
                                        val innerClass =
                                            method.substring(start + "$packageName.$className$".length, end)
                                        innerClassUsed.add(innerClass)
                                        println("Found inner class '$innerClass'")
                                    }
                                }
                            } else {
                                val updatedMethod = StringBuilder(method).apply {
                                    var count = 0
                                    var i = 0
                                    while (i < length) {
                                        if (this[i] == ',' || this[i] == ')') insert(
                                            i,
                                            " arg${count++}".also { i += it.length + 1 })
                                        else i++
                                    }
                                }
                                appendLine(updatedMethod.toString().replace('$', '.'))
                            }
                        }
                        innerClassUsed.forEach {
                            appendLine("  public static class $it {};")
                        }
                        appendLine("}")
                    }
                    val outputFile = tmpDir.resolve(packageName.replace(".", "/")).apply { mkdirs() }
                        .resolve("$className.java").apply { delete() }.apply { createNewFile() }
                    outputFile.writeText(source)

                    println(
                        arrayOf(
                            javac,
                            "-h",
                            "src/main/cpp/src/jni",
                            "-cp",
                            buildDirKotlin.absolutePath,
                            "-cp",
                            buildDirJava.absolutePath,
                            outputFile.absolutePath
                        ).joinToString(" ")
                    )

                    project.exec {
                        commandLine(
                            javac,
                            "-h",
                            "src/main/cpp/src/jni",
                            "-cp",
                            buildDirKotlin.absolutePath,
                            "-cp",
                            buildDirJava.absolutePath,
                            outputFile.absolutePath
                        )
                    }.assertNormalExitValue()
                }
        }
    }
}

application {
    mainClass.set(jarMainClassName)
}
