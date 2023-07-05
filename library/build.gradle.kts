plugins {
    kotlin("jvm") version "1.8.20"
    id("maven-publish")
}

group = "cn.numeron"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api("com.google.code.gson:gson:2.8.5")
    api("com.squareup.okhttp3:okhttp:4.2.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("cn.numeron:http:1.0.7")
}

publishing {
    publications {
        register<MavenPublication>("jitpack") {
            groupId = "cn.numeron"
            artifactId = "signalr-lite"
            artifact(tasks.getByName("kotlinSourcesJar"))
            version = project.version.toString()
            from(components["java"])
        }
    }
}