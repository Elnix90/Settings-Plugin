package io.github.elnix90.settings

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import io.github.elnix90.settings.fir.SettingStoreFirExtension

class SettingStoreFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::SettingStoreFirExtension
    }
}