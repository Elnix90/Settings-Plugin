package io.github.elnix90.settings.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class SettingsIrGenerationExtension(
) : IrGenerationExtension {

    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        val storeTransformer = SettingStoreTransformer(pluginContext)
        moduleFragment.transform(storeTransformer, null)

        val transformer = SettingKeyTransformer(pluginContext)
        moduleFragment.transform(transformer, null)
    }
}