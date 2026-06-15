@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.buildconfig)
    `maven-publish`
}

project.plugins.apply(D8Plugin::class.java)

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
}


val annotationsRuntimeClasspath by configurations.dependencyScope("annotationsRuntimeClasspath") {
    isTransitive = false
}
val annotationsJvmRuntimeClasspath by configurations.resolvable("annotationsJvmRuntimeClasspath") {
    extendsFrom(annotationsRuntimeClasspath)
}
val annotationsJsRuntimeClasspath by configurations.resolvable("annotationsJsRuntimeClasspath") {
    extendsFrom(annotationsRuntimeClasspath)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
    }
}

dependencies {
    compileOnly(libs.kotlin.compiler)

    annotationsRuntimeClasspath(project(":plugin-annotations"))
}

buildConfig {
    useKotlinOutput {
        internalVisibility = true
    }

    packageName(group.toString())
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")
}


kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.elnix.settings"
            artifactId = "compiler-plugin"
            version = "0.0.1"

            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
    }
}