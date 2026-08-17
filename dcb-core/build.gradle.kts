plugins {
    kotlin("jvm")
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
    testFixturesImplementation(kotlin("stdlib"))
    testFixturesImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
