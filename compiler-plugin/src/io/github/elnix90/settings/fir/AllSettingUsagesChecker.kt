package io.github.elnix90.settings.fir

import io.github.elnix90.settings.diagnostics.SearchErrors
import io.github.elnix90.settings.util.ClassIds.allSettingUsagesAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.kotlinListClassId
import io.github.elnix90.settings.util.ClassIds.settingUsageClassId
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.coneType

/**
 * Real-time lint for properties annotated with `@AllSettingUsages`.
 *
 * The IR phase replaces the initializer of the annotated property with a
 * generated `listOf(...)` of `SettingUsage` entries, so the property must be
 * declared as `List<SettingUsage>`. Annotating a property with any other type
 * would silently hold data of the wrong shape at runtime; it is rejected here
 * with a readable diagnostic instead.
 *
 * Reported diagnostics can be suppressed with
 * `@Suppress("ALL_SETTING_USAGES_WRONG_TYPE")`; in that case the IR phase skips
 * the initializer replacement instead of crashing.
 */
internal class AllSettingUsagesChecker : FirPropertyChecker(MppCheckerKind.Common) {
	context(
		context: CheckerContext,
		reporter: DiagnosticReporter
	)
	override fun check(
		declaration: FirProperty
	) {
		if (!declaration.hasAnnotation(allSettingUsagesAnnotationClassId, context.session)) {
			return
		}

		if (!declaration.isTypedAsSettingUsageList()) {
			reporter.reportOn(declaration.source, SearchErrors.ALL_SETTING_USAGES_WRONG_TYPE, context)
		}
	}

	/**
	 * Returns true when the declared type is exactly `List<SettingUsage>`
	 * (star projections and other element types are rejected).
	 */
	private fun FirProperty.isTypedAsSettingUsageList(): Boolean {
		val coneType = returnTypeRef.coneType as? ConeClassLikeType ?: return false
		if (coneType.lookupTag.classId != kotlinListClassId) {
			return false
		}

		val elementClassId =
			(coneType.typeArguments.singleOrNull() as? ConeKotlinTypeProjection)
				?.type as? ConeClassLikeType
				?: return false

		return elementClassId.lookupTag.classId == settingUsageClassId
	}
}
