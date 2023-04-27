import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm") version "1.8.21"
    id("java-gradle-plugin")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(gradleApi())
    implementation("net.md-5:SpecialSource:1.11.2")
    implementation("commons-io:commons-io:2.7")
}

gradlePlugin {
    plugins {
        create("bloodstorm") {
            id = "com.bloodstorm.gradle.plugin"
            implementationClass = "com.bloodstorm.gradle.BloodStormPlugin"
        }
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}