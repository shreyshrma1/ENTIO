plugins {
    kotlin("jvm")
    application
}

val ktorVersion = "3.1.3"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
}

application {
    mainClass.set("com.entio.web.ServerMainKt")
}
