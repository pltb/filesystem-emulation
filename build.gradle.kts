plugins {
    id("java")
    id("com.diffplug.spotless") version "6.21.0"
}

group = "io.github.pltb"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

spotless {
    java {
        importOrder()
        formatAnnotations()
    }
}
