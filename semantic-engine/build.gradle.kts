plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-types"))
    implementation(project(":shared"))
    implementation("org.apache.jena:jena-arq:5.3.0")
    implementation("org.snakeyaml:snakeyaml-engine:2.9")
}
