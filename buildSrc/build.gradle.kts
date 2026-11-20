plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")
    // PNGTranscoder discovers its ImageIO write adapter through Batik's codec module.
    // Keep this version aligned with batik-transcoder to avoid a missing WriteAdapter.
    implementation("org.apache.xmlgraphics:batik-codec:1.19")
}
