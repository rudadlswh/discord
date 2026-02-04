plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "com.example.discord"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("com.example.discord.MainKt")
}

kotlin {
    jvmToolchain(17)
}
