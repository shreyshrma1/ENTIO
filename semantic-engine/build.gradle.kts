plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-types"))
    implementation(project(":shared"))
    implementation("org.snakeyaml:snakeyaml-engine:2.9")
}
