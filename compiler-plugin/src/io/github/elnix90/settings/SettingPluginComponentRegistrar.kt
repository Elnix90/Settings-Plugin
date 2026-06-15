package io.github.elnix90.settings

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import io.github.elnix90.settings.ir.SettingsIrGenerationExtension
import org.jetbrains.kotlin.config.CompilerConfiguration

class SettingPluginComponentRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String
        get() = BuildConfig.KOTLIN_PLUGIN_ID
    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {

        IrGenerationExtension.registerExtension(
            SettingsIrGenerationExtension()
        )
    }
}
