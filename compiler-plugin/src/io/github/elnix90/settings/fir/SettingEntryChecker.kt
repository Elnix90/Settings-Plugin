package io.github.elnix90.settings.fir

import io.github.elnix90.settings.diagnostics.SearchErrors
import io.github.elnix90.settings.util.ClassIds.composableAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingCallAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingEntryAnnotationClassId
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
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * Real-time lint for functions annotated with `@SettingEntry`.
 *
 * `@SettingEntry` marks a composable whose call sites are indexed into the
 * settings search as manually described entries (`SettingUsage.Entry`) -
 * actions like a "Set wallpaper" button that have no `SettingObject`. The
 * annotation itself is argumentless: metadata is read from the wrapper's
 * parameters, because annotation arguments must be compile-time constants
 * while library-module `R` fields are not. Three rules keep that contract
 * sound:
 *
 * 1. **No mixing** (ERROR) - combining `@SettingEntry` with `@SettingCall` on
 *    the same function describes two mutually exclusive kinds of entry; the
 *    recorded usage would be ambiguous.
 *
 * 2. **Title parameter** (ERROR) - the function must declare a non-nullable
 *    `title: Int` parameter: without it no call site carries a title, so no
 *    entry could ever be found by search. (Arguments at call sites are
 *    ordinary expressions evaluated when the index initializes; only their
 *    shape is checked here.)
 *
 * 3. **Composable** (WARNING) - entry call sites are recorded with the
 *    enclosing function as the navigation target, which only makes sense for
 *    composable screens.
 *
 * Reported diagnostics can be suppressed with
 * `@Suppress("SETTING_ENTRY_MIXED_ANNOTATIONS")`,
 * `@Suppress("SETTING_ENTRY_MISSING_TITLE")` or
 * `@Suppress("SETTING_ENTRY_NOT_COMPOSABLE")`.
 */
internal class SettingEntryChecker : FirFunctionChecker(MppCheckerKind.Common) {
	context(
		context: CheckerContext,
		reporter: DiagnosticReporter
	)
	override fun check(
		declaration: FirFunction
	) {
		if (!declaration.hasAnnotation(settingEntryAnnotationClassId, context.session)) {
			return
		}

		if (declaration.hasAnnotation(settingCallAnnotationClassId, context.session)) {
			reporter.reportOn(declaration.source, SearchErrors.SETTING_ENTRY_MIXED_ANNOTATIONS, context)
		}

		if (!declaration.declaresTitleParameter()) {
			reporter.reportOn(declaration.source, SearchErrors.SETTING_ENTRY_MISSING_TITLE, context)
		}

		if (!declaration.hasAnnotation(composableAnnotationClassId, context.session)) {
			reporter.reportOn(declaration.source, SearchErrors.SETTING_ENTRY_NOT_COMPOSABLE, context)
		}
	}

	/**
	 * Returns true when the function declares a non-nullable `title: Int`
	 * parameter - the metadata parameter every entry wrapper must have so its
	 * call sites carry a searchable title.
	 */
	private fun FirFunction.declaresTitleParameter(): Boolean =
		valueParameters
			.any { parameter ->
				parameter.name.asString() == TITLE_PARAMETER &&
					(parameter.returnTypeRef.coneType as? ConeClassLikeType)
						?.let { it.classId == INT_CLASS_ID && !it.isMarkedNullable } == true
			}

	private companion object {
		val TITLE_PARAMETER: String = "title"
		val INT_CLASS_ID: ClassId = ClassId.topLevel(FqName("kotlin.Int"))
	}
}
