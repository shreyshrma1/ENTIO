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
}
