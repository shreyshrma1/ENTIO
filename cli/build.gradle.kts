plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core-types"))
    implementation(project(":semantic-engine"))
    implementation(project(":validation-engine"))
    implementation(project(":graph-diff"))
    implementation(project(":shared"))
}

application {
    mainClass.set("com.entio.cli.EntioCliKt")
}
