plugins {
	`kotlin-dsl`
	`java-gradle-plugin`
}

group = "io.github.elnix90.buildlogic"

dependencies {
	compileOnly(libs.android.gradle.plugin)
	compileOnly(libs.android.gradle.api)
	compileOnly(libs.kotlin.gradle.plugin.api)
	compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
	plugins {
		register("settingsLibrary") {
			id = "settings.library"
			implementationClass = "settingsplugin.buildlogic.AndroidLibraryPlugin"
		}
		register("settingsCompose") {
			id = "settings.compose"
			implementationClass = "settingsplugin.buildlogic.AndroidComposePlugin"
		}
	}
}
