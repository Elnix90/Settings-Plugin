plugins {
	alias(libs.plugins.settings.library)
	alias(libs.plugins.settings.compose)
	alias(libs.plugins.kotlin.serialization)
}

android {
	namespace = "io.github.elnix90.core"
}

dependencies {
	implementation(libs.androidx.core)
	implementation(libs.androidx.datastore.preferences)
	implementation(libs.androidx.ui.graphics)
	implementation(libs.androidx.datastore.core)
	implementation(libs.timber)
	implementation(libs.androidx.compose.runtime)
	implementation(libs.kotlinx.serialization.json)

	api(libs.androidx.datastore.preferences.core)
	api(libs.kotlinx.coroutines.core)

	implementation(libs.dragon.logging)
	implementation(project(":annotations"))
}
