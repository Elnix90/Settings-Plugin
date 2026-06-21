package io.github.elnix90.settings.ir

import io.github.elnix90.settings.ir.generators.AllStoresGenerator
import io.github.elnix90.settings.ir.transformers.SettingKeyTransformer
import io.github.elnix90.settings.ir.transformers.SettingsStoreTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

internal class SettingsIrGenerationExtension(
) : IrGenerationExtension {

    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        val storeTransformer = SettingsStoreTransformer(pluginContext)
        moduleFragment.transform(storeTransformer, null)

        val transformer = SettingKeyTransformer(pluginContext)
        moduleFragment.transform(transformer, null)

        AllStoresGenerator(pluginContext).generateAllStorePropertyBody(moduleFragment)
    }
}