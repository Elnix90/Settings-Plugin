package settingsplugin.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class AndroidComposePlugin : Plugin<Project> {
	override fun apply(target: Project) {
		target.pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

		target.extensions.configure(CommonExtension::class.java) {
			buildFeatures.compose = true
		}
	}
}
