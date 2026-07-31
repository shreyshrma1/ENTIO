plugins {
    kotlin("jvm")
    application
}

val ktorVersion = "3.1.3"

dependencies {
    implementation(project(":core-types"))
    implementation(project(":semantic-engine"))
    implementation(project(":validation-engine"))
    implementation(project(":graph-diff"))
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sse-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.apache.opennlp:opennlp-tools:2.5.11")
    implementation("org.apache.opennlp:opennlp-models-sentdetect-en:1.3.0")
    implementation("org.apache.opennlp:opennlp-models-tokenizer-en:1.3.0")
    implementation("org.apache.opennlp:opennlp-models-pos-en:1.3.0")
    implementation("org.apache.opennlp:opennlp-models-lemmatizer-en:1.3.0")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-websockets-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
}

application {
    mainClass.set("com.entio.web.ServerMainKt")
}
