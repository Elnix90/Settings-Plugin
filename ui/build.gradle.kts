plugins {
	alias(libs.plugins.settings.library)
	alias(libs.plugins.settings.compose)
}

android {
	namespace = "io.github.elnix90.ui"
}

dependencies {
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.core)
	implementation(libs.androidx.ui.graphics)
	implementation(libs.androidx.material3)
	implementation(libs.androidx.compose.foundation)
	implementation(libs.androidx.compose.foundation.layout)
	implementation(libs.androidx.compose.animation)
	implementation(libs.androidx.compose.animation.core)
	implementation(libs.androidx.compose.runtime)
	implementation(libs.androidx.compose.runtime.retain)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.text)
	implementation(libs.androidx.compose.ui.unit)
	implementation(libs.androidx.compose.ui.geometry)
	implementation(libs.androidx.compose.material3.window.size.class1)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.graphics.shapes)
	implementation(libs.androidx.compose.ui.tooling)

	api(libs.shared.ui)
	implementation(libs.timber)

	implementation(project(":core"))
	implementation(project(":runtime"))
}
