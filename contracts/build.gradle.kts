import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.google.protobuf") version "0.9.4"
    id("maven-publish")
}

val grpcVersion = "1.62.2"
val grpcKotlinVersion = "1.4.1"
val protobufVersion = "3.25.5"
val coroutinesVersion = "1.8.1"

java {
    toolchain {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-util:$grpcVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
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

tasks.register("checkContracts") {
    doLast {
        val protoDir = file("src/main/proto")
        val generatedDir = file("build/generated/source/proto/main")
        val protoFiles = fileTree(protoDir).files
        if (protoFiles.isEmpty()) {
            throw GradleException("No proto files found in ${protoDir.path}")
        }
        if (!generatedDir.exists()) {
            throw GradleException("Generated contracts missing. Run ./gradlew regenContracts")
        }
        val generatedFiles = fileTree(generatedDir).files
        if (generatedFiles.isEmpty()) {
            throw GradleException("Generated contracts missing. Run ./gradlew regenContracts")
        }
        val newestProto = protoFiles.maxOf { it.lastModified() }
        val oldestGenerated = generatedFiles.minOf { it.lastModified() }
        if (oldestGenerated < newestProto) {
            throw GradleException("Generated contracts are stale. Run ./gradlew regenContracts")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("contracts") {
            artifactId = "neogenesis-contracts"
            from(components["java"])
            pom {
                name.set("NeoGenesis Contracts")
                description.set("Protobuf/gRPC contracts and generated stubs for NeoGenesis.")
            }
        }
    }
    repositories {
        mavenLocal()
    }
}
