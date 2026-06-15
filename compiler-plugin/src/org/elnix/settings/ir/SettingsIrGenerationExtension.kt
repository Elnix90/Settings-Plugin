package org.elnix.settings.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class SettingsIrGenerationExtension(
) : IrGenerationExtension {

    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        val transformer = SettingKeyTransformer(
            context = pluginContext,
        )

        moduleFragment.transform(
            transformer = transformer,
            data = null
        )
    }
}