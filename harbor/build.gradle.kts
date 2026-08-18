plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":dcb-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":dcb-core")))
    testImplementation(project(":dcb-postgres"))
}

tasks.test {
    useJUnitPlatform()
    mustRunAfter(":dcb-postgres:test", ":enrolment:test")
}
