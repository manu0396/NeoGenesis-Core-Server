import com.google.protobuf.gradle.*
val ktorVersion = "3.0.3"
val grpcVersion = "1.61.0"
val grpcKotlinVersion = "1.4.1"
val protobufVersion = "3.25.2"
val coroutinesVersion = "1.8.1"
val serializationVersion = "1.7.3"
val logbackVersion = "1.4.14"
val micrometerVersion = "1.12.11"
val flywayVersion = "10.17.3"
val hikariVersion = "5.1.0"
val otelVersion = "1.43.0"

plugins {
    kotlin("jvm") version "2.0.21"
    id("io.ktor.plugin") version "3.0.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.neogenesis"
version = "1.0.0"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("io.opentelemetry:opentelemetry-api:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-sdk:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:$otelVersion")

    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-util:$grpcVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("software.amazon.awssdk:sqs:2.25.59")
    implementation("software.amazon.awssdk:eventbridge:2.25.59")
    implementation("software.amazon.awssdk:kms:2.25.59")
    implementation("com.google.cloud:google-cloud-pubsub:1.129.1")
    implementation("io.grpc:grpc-services:$grpcVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    runtimeOnly("org.postgresql:postgresql:42.7.4")
    runtimeOnly("com.h2database:h2:2.2.224")

    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("traceabilityGate") {
    dependsOn(tasks.test)
    doLast {
        val traceabilityFile = file("src/main/resources/iso13485/traceability.csv")
        val evidenceFile = file("src/main/resources/iso13485/requirement_test_evidence.csv")
        if (!traceabilityFile.exists()) {
            throw GradleException("Missing traceability matrix: ${traceabilityFile.path}")
        }
        if (!evidenceFile.exists()) {
            throw GradleException("Missing requirement-test evidence map: ${evidenceFile.path}")
        }

        val requirements = traceabilityFile.readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.substringBefore(',').trim() }
            .toSet()

        val evidenceRows = evidenceFile.readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val tokens = line.split(',').map { it.trim() }
                if (tokens.size < 5) {
                    throw GradleException("Invalid evidence row: $line")
                }
                tokens
            }
        val evidenceByRequirement = evidenceRows.groupBy { it[0] }
        val missingEvidence = requirements.filter { !evidenceByRequirement.containsKey(it) }
        if (missingEvidence.isNotEmpty()) {
            throw GradleException("Traceability gate failed. Missing evidence for requirements: $missingEvidence")
        }

        val missingAutomatedTests = evidenceRows
            .filter { it[1].equals("automated", ignoreCase = true) }
            .mapNotNull { row ->
                val className = row[2]
                val simpleName = className.substringAfterLast('.')
                val exists = fileTree("src/test/kotlin").files.any { it.nameWithoutExtension == simpleName }
                if (exists) null else className
            }
            .distinct()
        if (missingAutomatedTests.isNotEmpty()) {
            throw GradleException(
                "Traceability gate failed. Missing automated test classes referenced in evidence: $missingAutomatedTests"
            )
        }

        val untrackedEvidence = evidenceByRequirement.keys - requirements
        if (untrackedEvidence.isNotEmpty()) {
            throw GradleException("Traceability gate failed. Evidence references unknown requirements: $untrackedEvidence")
        }

        println("Traceability gate passed for ${requirements.size} requirements.")
    }
}

tasks.named("check") {
    dependsOn("traceabilityGate")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}
