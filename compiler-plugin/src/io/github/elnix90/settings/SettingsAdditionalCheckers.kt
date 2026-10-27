package io.github.elnix90.settings

import io.github.elnix90.settings.fir.AllSettingUsagesChecker
import io.github.elnix90.settings.fir.SettingCallChecker
import io.github.elnix90.settings.fir.SettingEntryChecker
import io.github.elnix90.settings.fir.SettingKeyChecker
import io.github.elnix90.settings.fir.SettingScreenChecker
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
 * - [SettingCallChecker]: `SETTING_CALL_NO_SETTING_PARAMETER`, `SETTING_CALL_NOT_COMPOSABLE`
 * - [SettingEntryChecker]: `SETTING_ENTRY_MIXED_ANNOTATIONS`, `SETTING_ENTRY_MISSING_TITLE`, `SETTING_ENTRY_NOT_COMPOSABLE`
 * - [SettingScreenChecker]: `SETTING_SCREEN_ROUTE_NOT_OBJECT`, `SETTING_SCREEN_NOT_COMPOSABLE`
 * - [AllSettingUsagesChecker]: `ALL_SETTING_USAGES_WRONG_TYPE`
 */
internal class SettingsAdditionalCheckers(
	session: FirSession
) : FirAdditionalCheckersExtension(session) {
	override val declarationCheckers =
		object : DeclarationCheckers() {
			override val propertyCheckers =
				setOf(
					SettingKeyChecker(),
					AllSettingUsagesChecker()
				)

			override val classCheckers =
				setOf(SettingsStoreChecker())

			override val functionCheckers =
				setOf(
					SettingCallChecker(),
					SettingEntryChecker(),
					SettingScreenChecker()
				)
		}
}
