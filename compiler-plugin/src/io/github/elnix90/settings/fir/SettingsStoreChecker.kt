package io.github.elnix90.settings.fir

import io.github.elnix90.settings.diagnostics.SettingsErrors
import io.github.elnix90.settings.util.ClassIds.settingsStoreAnnotationClassId
import io.github.elnix90.settings.util.isSettingsStore
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation

/**
 * Real-time lint for classes annotated with `@SettingsStore`.
 *
 * A store only makes sense when it extends
 * `io.github.elnix90.core.stores.SettingsStore`: the plugin generates the synthetic
 * `ALL` collection for it and expects store semantics (reset, backup, name) at
 * runtime. Annotating anything else previously crashed the compiler with an
 * unhandled exception during FIR generation; it now produces this diagnostic
 * instead, while [SettingsStoreFirExtension] skips generation gracefully.
 *
 * Reported diagnostics can be suppressed with `@Suppress("SETTINGS_STORE_INVALID_SUPERTYPE")`.
 */
internal class SettingsStoreChecker : FirClassChecker(MppCheckerKind.Common) {
	context(
		context: CheckerContext,
		reporter: DiagnosticReporter
	)
	override fun check(
		declaration: FirClass
	) {
		if (!declaration.hasAnnotation(settingsStoreAnnotationClassId, context.session)) {
			return
		}

		if (!declaration.symbol.isSettingsStore(context.session)) {
			reporter.reportOn(declaration.source, SettingsErrors.SETTINGS_STORE_INVALID_SUPERTYPE, context)
		}
	}
}
