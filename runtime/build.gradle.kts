plugins {
	alias(libs.plugins.settings.library)
	alias(libs.plugins.settings.compose)
}

android {
	namespace = "io.github.elnix90.runtime"
}

dependencies {
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)

	implementation(libs.androidx.core)
	implementation(libs.androidx.ui.graphics)
	implementation(libs.timber)

	implementation(project(":core"))

	testImplementation(kotlin("test-junit"))
}
