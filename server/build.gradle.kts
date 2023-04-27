import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm") version "1.8.21"
    id("com.bloodstorm.gradle.plugin")
}

group = "cruciblemc"
version = "1.7.10-staging-c1889d22"

repositories {
    mavenCentral()
    maven {
        name = "Minecraft"
        url = uri("https://libraries.minecraft.net/")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("net.minecraft:launchwrapper:1.11")
    implementation("com.google.code.findbugs:jsr305:1.3.9")
    implementation("org.ow2.asm:asm-debug-all:5.0.3")
    implementation( "net.sf.jopt-simple:jopt-simple:4.5")
    implementation("lzma:lzma:0.0.1")
    implementation( "com.mojang:realms:1.3.5")
    implementation( "org.apache.commons:commons-compress:1.8.1")
    implementation( "org.apache.httpcomponents:httpclient:4.3.3")
    implementation( "commons-logging:commons-logging:1.1.3")
    implementation( "org.apache.httpcomponents:httpcore:4.3.2")
    implementation( "java3d:vecmath:1.3.1")
    implementation( "net.sf.trove4j:trove4j:3.0.3")
    implementation( "com.ibm.icu:icu4j-core-mojang:51.2")
    implementation("com.paulscode:codecjorbis:20101023")
    implementation("com.paulscode:codecwav:20101023")
    implementation("com.paulscode:libraryjavasound:20101123")
    implementation("com.paulscode:librarylwjglopenal:20100824")
    implementation("com.paulscode:soundsystem:20120107")
    implementation("io.netty:netty-all:4.0.10.Final")
    implementation("com.google.guava:guava:17.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.4")
    implementation("commons-codec:commons-codec:1.9")
    implementation("net.java.jinput:jinput:2.0.5")
    implementation("net.java.jutils:jutils:1.0.0")
    implementation("com.google.code.gson:gson:2.2.4")
    implementation("com.mojang:authlib:1.5.16")
    implementation("org.apache.logging.log4j:log4j-api:2.3.2")
    implementation("org.apache.logging.log4j:log4j-core:2.3.2")
    implementation("org.lwjgl.lwjgl:lwjgl:2.9.1")
    implementation("org.lwjgl.lwjgl:lwjgl_util:2.9.1")
    implementation("tv.twitch:twitch:5.16")
    implementation("org.yaml:snakeyaml:1.16")
    implementation("com.lmax:disruptor:3.2.1")
    implementation("org.apache.commons:commons-dbcp2:2.1.1")
    implementation("jline:jline:2.13")
    implementation("net.openhft:affinity:3.0.1")
    implementation ("it.unimi.dsi:fastutil:8.5.12")
    implementation ("it.unimi.dsi:dsiutils:2.7.3") {
        exclude(group = "com.google", module = "guava")
    }
    implementation("javax.persistence:javax.persistence-api:2.2")
    implementation("org.avaje:ebean:2.7.3")
    implementation("com.googlecode.json-simple:json-simple:1.1")
    implementation("commons-lang:commons-lang:2.6")
    implementation("net.md-5:SpecialSource:1.7.4")
}

tasks.test {
    useJUnitPlatform()
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}