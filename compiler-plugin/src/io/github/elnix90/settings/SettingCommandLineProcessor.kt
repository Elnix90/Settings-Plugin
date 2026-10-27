package io.github.elnix90.settings

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@Suppress("unused") // Used via reflection.
internal class SettingCommandLineProcessor : CommandLineProcessor {
	override val pluginId: String
		get() = BuildConfig.KOTLIN_PLUGIN_ID
	override val pluginOptions: Collection<CliOption>
		get() = listOf(DEBUG_OPTION)

	override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
		when (option) {
			DEBUG_OPTION -> configuration.put(DEBUG_KEY, value)
			else -> error("Unexpected config option: '${option.optionName}'")
		}
	}

	internal companion object {
		internal val DEBUG_KEY = CompilerConfigurationKey<String>("debug")

		private val DEBUG_OPTION = CliOption(
			optionName = "debug",
			valueDescription = "true|false",
			description = "Enables settings plugin debug logging (MessageCollector INFO + /tmp/settings-plugin.log).",
			required = false,
			allowMultipleOccurrences = false
		)
	}
}
