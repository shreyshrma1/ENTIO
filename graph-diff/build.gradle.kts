plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-types"))
    implementation(project(":semantic-engine"))
    implementation(project(":validation-engine"))
    implementation(project(":shared"))
}
