package io.github.elnix90.settings

import io.github.elnix90.settings.fir.SettingsStoreFirExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

internal class SettingsStoreFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::SettingsStoreFirExtension
//        +::SettingsAdditionalCheckers // Unused for now
    }
}