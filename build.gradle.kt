plugins {
    id 'java'
}

repositories {
    mavenCentral()  // For most dependencies
    maven { url 'https://jgroups.org/repository/maven2' }  // JGroup Maven repository
}

dependencies {
    // JGroup Dependency
    implementation 'org.jgroups:jgroups:5.2.3'  // Replace with the desired JGroup version

    // AWS SDK for ECS
    implementation 'software.amazon.awssdk:ecs:2.17.113' // AWS SDK for ECS, replace with latest version

    // Logging (optional but recommended)
    implementation 'org.slf4j:slf4j-api:2.0.0-alpha1'
    implementation 'org.slf4j:slf4j-simple:2.0.0-alpha1' // For simple console logging

    // Jackson (optional, required by AWS SDK for JSON parsing)
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.14.1' // Adjust the version if needed
}

tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
}

test {
    useJUnitPlatform() // Optional if you're using JUnit 5 for tests
}
