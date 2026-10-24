package io.github.elnix90.settings

import io.github.elnix90.settings.fir.SettingKeyChecker
import io.github.elnix90.settings.fir.SettingsStoreChecker
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

/**
 * Entry point that plugs the settings checkers into the K2 frontend.
 *
 * Registered by [SettingsStoreFirRegistrar], it makes the following diagnostics
 * available both in the CLI build and as real-time lint in the IDE:
 *
 * - [SettingKeyChecker]: `SETTING_KEY_OUTSIDE_STORE`, `SETTING_KEY_INVALID_INITIALIZER`
 * - [SettingsStoreChecker]: `SETTINGS_STORE_INVALID_SUPERTYPE`
 */
internal class SettingsAdditionalCheckers(
	session: FirSession
) : FirAdditionalCheckersExtension(session) {
	override val declarationCheckers =
		object : DeclarationCheckers() {
			override val propertyCheckers =
				setOf(SettingKeyChecker())

			override val classCheckers =
				setOf(SettingsStoreChecker())
		}
}
