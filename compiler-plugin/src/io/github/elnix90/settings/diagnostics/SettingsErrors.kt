package io.github.elnix90.settings.diagnostics

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity.ERROR
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtDeclaration

/**
 * Diagnostic factories reported by the settings compiler plugin checkers.
 *
 * Each factory is created with an explicit [getRendererFactory] so that the message
 * rendering travels with the factory itself. This means no global registration is
 * required: both the CLI and the IDE resolve the human-readable message through
 * [SettingsErrorsDefaultMessages] automatically.
 *
 * The property names are the diagnostic names: they are what appears in the IDE,
 * what `@Suppress("...")` matches, and what tests assert on.
 *
 * **Important - use an `org.jetbrains.kotlin.*` class (e.g. [KtDeclaration]) for
 * `psiType`, never `com.intellij.psi.PsiElement`:** the Gradle and IDE Kotlin
 * daemons run the plugin against `kotlin-compiler-embeddable`, which relocates
 * `com.intellij.*` to `org.jetbrains.kotlin.com.intellij.*`. A `PsiElement::class`
 * literal compiled against the plain `kotlin-compiler` resolves to the
 * non-relocated name and throws `NoClassDefFoundError` inside
 * [SettingsErrors]' `<clinit>` under the embeddable compiler, killing every
 * checker silently (diagnostics never reach the editor). `psiType` is stored
 * metadata that the compiler never reads, so [KtDeclaration] is a safe,
 * semantically accurate pick for diagnostics positioned on a declaration name.
 */
internal object SettingsErrors : KtDiagnosticsContainer() {
	/**
	 * Reported when a property annotated with `@SettingKey` is not a direct member
	 * of a class or object annotated with `@SettingsStore`.
	 *
	 * Covers top-level properties, local properties, properties of plain classes,
	 * and properties nested in classes that are not themselves stores.
	 */
	val SETTING_KEY_OUTSIDE_STORE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTING_KEY_OUTSIDE_STORE",
		severity = ERROR,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	/**
	 * Reported when the initializer of a property annotated with `@SettingKey`
	 * does not resolve to a settings builder call exposing a `key` parameter.
	 *
	 * Without such a call the compiler plugin cannot inject the automatic key,
	 * which would otherwise lead to a compiler crash or a runtime blank-key error.
	 */
	val SETTING_KEY_INVALID_INITIALIZER: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTING_KEY_INVALID_INITIALIZER",
		severity = ERROR,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	/**
	 * Reported when a class or object annotated with `@SettingsStore` does not
	 * extend `io.github.elnix90.core.stores.SettingsStore`.
	 */
	val SETTINGS_STORE_INVALID_SUPERTYPE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
		name = "SETTINGS_STORE_INVALID_SUPERTYPE",
		severity = ERROR,
		defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
		psiType = KtDeclaration::class,
		rendererFactory = getRendererFactory()
	)

	override fun getRendererFactory(): BaseDiagnosticRendererFactory = SettingsErrorsDefaultMessages
}
