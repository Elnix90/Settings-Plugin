package io.github.elnix90.settings.diagnostics

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity.ERROR
import org.jetbrains.kotlin.diagnostics.Severity.WARNING
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtDeclaration

/**
 * Diagnostic factories reported by the settings search checkers (`@SettingCall`,
 * `@SettingEntry`, `@SettingScreen` and `@AllSettingUsages`).
 *
 * Structurally identical to [SettingsErrors]: each factory carries its renderer
 * factory so message rendering travels with the factory itself, requiring no
 * global registration in either the CLI or the IDE.
 *
 * The property names are the diagnostic names: they are what appears in the IDE,
 * what `@Suppress("...")` matches, and what tests assert on.
 *
 * **Important - use an `org.jetbrains.kotlin.*` class (e.g. [KtDeclaration]) for
 * `psiType`, never `com.intellij.psi.PsiElement`:** the Gradle and IDE Kotlin
 * daemons run the plugin against `kotlin-compiler-embeddable`, which relocates
 * `com.intellij.*` to `org.jetbrains.kotlin.com.intellij.*`. A `PsiElement::class`
 * literal compiled against the plain `kotlin-compiler` resolves to the
 * non-relocated name and throws `NoClassDefFoundError` inside [SearchErrors]'
 * `<clinit>` under the embeddable compiler, killing every checker silently.
 * @see [SettingsErrors]
 */
internal object SearchErrors : KtDiagnosticsContainer() {
	/**
	 * Reported when a function annotated with `@SettingCall` declares no
	 * parameter whose type is `SettingObject<*, *>`: such a call site has no
	 * discoverable setting argument, so the usage could never be recorded into
	 * the generated `@AllSettingUsages` list.
	 */
	val SETTING_CALL_NO_SETTING_PARAMETER: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTING_CALL_NO_SETTING_PARAMETER",
		severity = ERROR,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	/**
	 * Reported when a function annotated with `@SettingCall` is not
	 * `@Composable`. Usages are recorded with the enclosing function as the
	 * screen identifier for navigation; non-composable wrappers break that
	 * assumption. A warning (not an error) because such functions are still
	 * valid and indexable.
	 */
	val SETTING_CALL_NOT_COMPOSABLE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTING_CALL_NOT_COMPOSABLE",
		severity = WARNING,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	/**
	 * Reported when a property annotated with `@AllSettingUsages` is not typed
	 * `List<SettingUsage>`: the IR phase replaces the initializer with a
	 * generated `listOf(...)` of `SettingUsage` entries, so any other type would
	 * silently hold the wrong data.
	 */
	val ALL_SETTING_USAGES_WRONG_TYPE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "ALL_SETTING_USAGES_WRONG_TYPE",
		severity = ERROR,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	/**
	 * Reported when a function annotated with `@SettingEntry` is not
	 * `@Composable`. Entry call sites are recorded with the enclosing function
	 * as the screen identifier for navigation; non-composable entries break
	 * that assumption. A warning (not an error) because such functions are
	 * still valid and indexable.
	 */
	val SETTING_ENTRY_NOT_COMPOSABLE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTING_ENTRY_NOT_COMPOSABLE",
		severity = WARNING,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	/**
	 * Reported when a function annotated with `@SettingEntry` declares no
	 * non-nullable `title: Int` parameter: metadata is read from the wrapper's
	 * parameters (annotation arguments must be compile-time constants, but
	 * library-module `R` fields are not), so without such a parameter no call
	 * site carries a title and the entry could never be found by search.
	 */
	val SETTING_ENTRY_MISSING_TITLE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTING_ENTRY_MISSING_TITLE",
		severity = ERROR,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	/**
	 * Reported when a function is annotated with both `@SettingCall` and
	 * `@SettingEntry`: the two describe mutually exclusive kinds of search
	 * entry (`SettingObject`-based vs manually described), so combining them
	 * makes the recorded usage ambiguous.
	 */
	val SETTING_ENTRY_MIXED_ANNOTATIONS: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTING_ENTRY_MIXED_ANNOTATIONS",
		severity = ERROR,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	/**
	 * Reported when a function annotated with `@SettingScreen` is not
	 * `@Composable`. The annotation marks the composable screen hosting
	 * settings; on a non-composable function it would never enclose any
	 * recorded call sites.
	 */
	val SETTING_SCREEN_NOT_COMPOSABLE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTING_SCREEN_NOT_COMPOSABLE",
		severity = WARNING,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	/**
	 * Reported when the `route` argument of `@SettingScreen` does not
	 * reference an `object`: only objects (typically `data object`s implementing
	 * the app's `NavKey`) can be instantiated by the IR phase to record a
	 * navigable route instance. When this fires, the usage keeps working and
	 * only the typed route is omitted (falling back to the screen FqName).
	 */
	val SETTING_SCREEN_ROUTE_NOT_OBJECT: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTING_SCREEN_ROUTE_NOT_OBJECT",
		severity = ERROR,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	override fun getRendererFactory(): BaseDiagnosticRendererFactory = SearchErrorsDefaultMessages
}
