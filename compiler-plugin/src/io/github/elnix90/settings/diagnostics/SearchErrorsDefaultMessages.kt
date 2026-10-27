package io.github.elnix90.settings.diagnostics

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory

/**
 * Message definitions for every diagnostic declared in [SearchErrors].
 *
 * The map is created lazily (via [KtDiagnosticFactoryToRendererMap]) to break the
 * initialization cycle between the factories (which reference this renderer factory)
 * and the messages (which reference the factories). This mirrors the structure used
 * by the Kotlin compiler itself (`FirErrorsDefaultMessages`).
 */
internal object SearchErrorsDefaultMessages : BaseDiagnosticRendererFactory() {
	@Suppress("ktlint:standard:property-naming")
	override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("SETTINGS_SEARCH_PLUGIN") { map ->
		map.put(
			SearchErrors.SETTING_CALL_NO_SETTING_PARAMETER,
			"'@SettingCall' function must declare a parameter whose type is 'SettingObject' so its call sites can be recorded as settings usages"
		)

		map.put(
			SearchErrors.SETTING_CALL_NOT_COMPOSABLE,
			"'@SettingCall' should be applied to a '@Composable' function so settings usages are recorded with the screen they are rendered on"
		)

		map.put(
			SearchErrors.ALL_SETTING_USAGES_WRONG_TYPE,
			"'@AllSettingUsages' property must be declared as 'List<SettingUsage>'"
		)

		map.put(
			SearchErrors.SETTING_ENTRY_NOT_COMPOSABLE,
			"'@SettingEntry' should be applied to a '@Composable' function so entry call sites are recorded with the screen they are rendered on"
		)

		map.put(
			SearchErrors.SETTING_ENTRY_MISSING_TITLE,
			"'@SettingEntry' function must declare a non-nullable 'title: Int' parameter so its call sites can be indexed as searchable entries"
		)

		map.put(
			SearchErrors.SETTING_ENTRY_MIXED_ANNOTATIONS,
			"'@SettingEntry' cannot be combined with '@SettingCall': use '@SettingCall' for SettingObject-backed settings and '@SettingEntry' for manually described actions"
		)

		map.put(
			SearchErrors.SETTING_SCREEN_NOT_COMPOSABLE,
			"'@SettingScreen' should be applied to a '@Composable' function so the settings it hosts record it as their screen"
		)

		map.put(
			SearchErrors.SETTING_SCREEN_ROUTE_NOT_OBJECT,
			"'@SettingScreen' route must be an object (e.g. a 'data object' implementing NavKey) so the plugin can record it as the navigation target of its settings"
		)
	}
}
