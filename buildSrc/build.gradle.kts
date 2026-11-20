plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // Keep the transcoder and its PNG codec adapter on the same Batik release.
    implementation("org.apache.xmlgraphics:batik-transcoder:1.20")
    implementation("org.apache.xmlgraphics:batik-codec:1.20")
}
