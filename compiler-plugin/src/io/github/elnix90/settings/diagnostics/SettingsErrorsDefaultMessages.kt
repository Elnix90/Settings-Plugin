package io.github.elnix90.settings.diagnostics

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory

/**
 * Message definitions for every diagnostic declared in [SettingsErrors].
 *
 * The map is created lazily (via [KtDiagnosticFactoryToRendererMap]) to break the
 * initialization cycle between the factories (which reference this renderer factory)
 * and the messages (which reference the factories). This mirrors the structure used
 * by the Kotlin compiler itself (`FirErrorsDefaultMessages`).
 */
internal object SettingsErrorsDefaultMessages : BaseDiagnosticRendererFactory() {
	// The uppercase name mirrors `FirErrorsDefaultMessages.MAP` from the Kotlin compiler itself.
	@Suppress("ktlint:standard:property-naming")
	override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("SETTINGS_PLUGIN") { map ->
		map.put(
			SettingsErrors.SETTING_KEY_OUTSIDE_STORE,
			"'@SettingKey' is only allowed on properties that are direct members of a class or object annotated with '@SettingsStore'"
		)

		map.put(
			SettingsErrors.SETTING_KEY_INVALID_INITIALIZER,
			"'@SettingKey' property must be initialized with a settings builder call that exposes a 'key' parameter, for example boolean(...)"
		)

		map.put(
			SettingsErrors.SETTINGS_STORE_INVALID_SUPERTYPE,
			"'@SettingsStore' can only be applied to a class or object that extends 'SettingsStore'"
		)
	}
}
