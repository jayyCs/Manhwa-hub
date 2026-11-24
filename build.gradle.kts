plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
}

kotlin { jvmToolchain(17) }

application {
    mainClass.set("org.manhwahub.mangadex.MangaDexApiKt")
}
