plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-types"))
    implementation(project(":shared"))
    implementation("org.apache.jena:jena-arq:5.3.0")
    implementation("org.apache.jena:jena-shacl:5.3.0")
    implementation("net.sourceforge.owlapi:owlapi-distribution:5.1.9")
    implementation("net.sourceforge.owlapi:org.semanticweb.hermit:1.4.5.519")
    implementation("org.snakeyaml:snakeyaml-engine:2.9")
    implementation("org.apache.lucene:lucene-core:10.5.0")
    implementation("org.apache.lucene:lucene-analysis-common:10.5.0")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.28.0")
    implementation("ai.djl:api:0.36.0")
    implementation("ai.djl.huggingface:tokenizers:0.36.0")
}

tasks.register<JavaExec>("generateFiboCatalog") {
    group = "entio"
    description = "Verify Phase 5 and generate deterministic Phase 13 FIBO descriptors."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.entio.semantic.Phase13FiboAssetGenerator")
    args(
        rootProject.projectDir.resolve("external-ontologies/fibo").absolutePath,
        rootProject.projectDir.resolve("external-ontologies/domain-search/fibo/master_2026Q2").absolutePath,
    )
}

tasks.register<JavaExec>("verifyFiboCatalog") {
    group = "verification"
    description = "Verify the pinned Phase 5 package and Phase 13 domain assets offline."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.entio.semantic.Phase13FiboAssetVerifier")
    args(
        rootProject.projectDir.resolve("external-ontologies/fibo").absolutePath,
        rootProject.projectDir.resolve("external-ontologies/domain-search/fibo/master_2026Q2").absolutePath,
    )
}

tasks.register<JavaExec>("generateDomainSearchIndex") {
    group = "entio"
    description = "Generate the offline Phase 13 lexical documents and exact-scan vectors."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.entio.semantic.DomainSearchIndexGenerator")
    args(
        rootProject.projectDir.resolve("external-ontologies/domain-search/fibo/master_2026Q2").absolutePath,
        rootProject.projectDir.resolve("external-ontologies/domain-search/models/all-MiniLM-L6-v2").absolutePath,
    )
}

tasks.register<JavaExec>("verifyDomainSearchIndex") {
    group = "verification"
    description = "Verify and reproducibly regenerate the offline Phase 13 search index."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.entio.semantic.DomainSearchIndexVerifier")
    args(
        rootProject.projectDir.resolve("external-ontologies/domain-search/fibo/master_2026Q2").absolutePath,
        rootProject.projectDir.resolve("external-ontologies/domain-search/models/all-MiniLM-L6-v2").absolutePath,
    )
}
