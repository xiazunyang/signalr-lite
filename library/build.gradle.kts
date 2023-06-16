plugins {
    kotlin("jvm") version "1.8.20"
}

group = 'cn.numeron'
version = '1.0'

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {

    api("com.google.code.gson:gson:2.8.5")
    api("com.squareup.okhttp3:okhttp:4.2.2")

    testImplementation("cn.numeron:http:1.0.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.11.2")
    testImplementation("org.powermock:powermock-api-mockito2:2.0.9")
    testImplementation("org.powermock:powermock-module-junit4:2.0.9")
}
