@file:OptIn(ExperimentalWasmDsl::class)
@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.buildconfig)
	alias(libs.plugins.vanniktech.maven.publish)
}

project.plugins.apply(D8Plugin::class.java)

sourceSets {
	main {
		java.setSrcDirs(listOf("src"))
		resources.setSrcDirs(listOf("resources"))
	}
	test {
		java.setSrcDirs(listOf("test"))
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
	implementation(project(":annotations"))
	annotationsRuntimeClasspath(project(":annotations"))

	// Tests run against the *embeddable* compiler on purpose: that is what the Gradle and
	// IDE daemons use in production. It relocates com.intellij.* to org.jetbrains.kotlin.com.intellij.*,
	// so any un-shaded IntelliJ reference in the plugin crashes at runtime here — exactly
	// how a PsiElement class literal in SettingsErrors once silenced every IDE diagnostic.
	testImplementation(libs.kotlin.compiler.embeddable)
	testImplementation(project(":annotations"))
	testImplementation(kotlin("test"))
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
		freeCompilerArgs.add("-Xcontext-parameters")
	}
	explicitApi()
}

// The jar under test: each test compilation loads it through -Xplugin, exactly like a real build.
val compilerPluginJar = tasks.jar.flatMap { it.archiveFile }

tasks.test {
	dependsOn(tasks.jar)
	inputs.file(compilerPluginJar).withPropertyName("settingsCompilerPluginJar")
	systemProperty("settingsCompilerPluginJar", compilerPluginJar.get().asFile.absolutePath)
	// The tests run the Kotlin compiler in-process.
	maxHeapSize = "1g"
}
