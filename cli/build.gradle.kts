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
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
}

application {
    mainClass.set("com.entio.cli.EntioCliKt")
}
