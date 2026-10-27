package io.github.elnix90.settings.fir

import io.github.elnix90.settings.diagnostics.SearchErrors
import io.github.elnix90.settings.util.ClassIds.composableAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingCallAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingObjectClassId
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType

/**
 * Real-time lint for functions annotated with `@SettingCall`.
 *
 * `@SettingCall` tells the IR phase where settings are rendered: every call site
 * of an annotated function is recorded into the `@AllSettingUsages` list. Two
 * rules keep that discovery meaningful:
 *
 * 1. **Setting parameter** (ERROR) - the function must declare at least one
 *    parameter whose type is `SettingObject<*, *>`: that argument is what gets
 *    recorded as the usage. Without it, every call site would be silently
 *    dropped from the index.
 *
 * 2. **Composable** (WARNING) - the function should be `@Composable`, because
 *    recorded usages point at their enclosing function as the navigation
 *    target, which only makes sense for composable screens.
 *
 * Both rules mirror the behavior of
 * [io.github.elnix90.settings.ir.collector.SettingUsageCollector], so the
 * checker never rejects code the backend could handle.
 *
 * Reported diagnostics can be suppressed with
 * `@Suppress("SETTING_CALL_NO_SETTING_PARAMETER")` or
 * `@Suppress("SETTING_CALL_NOT_COMPOSABLE")`.
 */
internal class SettingCallChecker : FirFunctionChecker(MppCheckerKind.Common) {
	context(
		context: CheckerContext,
		reporter: DiagnosticReporter
	)
	override fun check(
		declaration: FirFunction
	) {
		if (!declaration.hasAnnotation(settingCallAnnotationClassId, context.session)) {
			return
		}

		val hasSettingParameter =
			declaration.valueParameters.any { parameter ->
				(parameter.returnTypeRef.coneType as? ConeClassLikeType)?.classId == settingObjectClassId
			}

		if (!hasSettingParameter) {
			reporter.reportOn(declaration.source, SearchErrors.SETTING_CALL_NO_SETTING_PARAMETER, context)
		}

		if (!declaration.hasAnnotation(composableAnnotationClassId, context.session)) {
			reporter.reportOn(declaration.source, SearchErrors.SETTING_CALL_NOT_COMPOSABLE, context)
		}
	}
}
