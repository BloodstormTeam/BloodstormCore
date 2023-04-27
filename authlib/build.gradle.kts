plugins {
    id("java")
}

group = "org.example"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:17.0")
    implementation("org.apache.commons:commons-lang3:3.3.2")
    implementation("com.google.code.gson:gson:2.2.4")
    implementation("commons-io:commons-io:2.4")
    implementation("commons-codec:commons-codec:1.9")
}

tasks.test {
    useJUnitPlatform()
}