package io.github.elnix90.settings.fir

import io.github.elnix90.settings.diagnostics.SearchErrors
import io.github.elnix90.settings.util.ClassIds.composableAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingScreenAnnotationClassId
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Real-time lint for functions annotated with `@SettingScreen`.
 *
 * `@SettingScreen(route)` declares the navigation route object of the screen a
 * settings screen composable represents. The plugin records the route object
 * instance on every usage rendered inside the annotated function (including
 * through nested helpers), so search results are directly navigable without a
 * name-to-route mapping. Two rules keep that recording honest:
 *
 * 1. **Object route** (ERROR) - only an `object` (typically a `data object`
 *    implementing the app's `NavKey`) can be instantiated by the plugin as the
 *    recorded navigation target. When the route is not an object, the settings
 *    of that screen keep working and only the typed route is omitted, falling
 *    back to the screen FqName.
 *
 * 2. **Composable** (WARNING) - the annotation marks the composable screen
 *    hosting settings; on a non-composable function it would never enclose any
 *    recorded call sites.
 *
 * Reported diagnostics can be suppressed with
 * `@Suppress("SETTING_SCREEN_ROUTE_NOT_OBJECT")` or
 * `@Suppress("SETTING_SCREEN_NOT_COMPOSABLE")`.
 */
internal class SettingScreenChecker : FirFunctionChecker(MppCheckerKind.Common) {
	context(
		context: CheckerContext,
		reporter: DiagnosticReporter
	)
	override fun check(
		declaration: FirFunction
	) {
		val annotation = declaration.findAnnotation(settingScreenAnnotationClassId, context.session) ?: return

		val routeType = annotation.getKClassArgument(ROUTE_ARGUMENT)
		val routeClassId = (routeType as? ConeClassLikeType)?.classId
		val routeSymbol = (routeType as? ConeClassLikeType)?.toSymbol(context.session)
		val isObject = (routeSymbol as? FirClassSymbol)?.classKind == ClassKind.OBJECT

		if (routeClassId != null && !isObject) {
			reporter.reportOn(declaration.source, SearchErrors.SETTING_SCREEN_ROUTE_NOT_OBJECT, context)
		}

		if (!declaration.hasAnnotation(composableAnnotationClassId, context.session)) {
			reporter.reportOn(declaration.source, SearchErrors.SETTING_SCREEN_NOT_COMPOSABLE, context)
		}
	}

	/**
	 * Returns the `@SettingScreen` annotation on [this] function, or null when
	 * there is none (or it cannot be resolved).
	 */
	private fun FirFunction.findAnnotation(
		classId: ClassId,
		session: FirSession
	): FirAnnotation? =
		annotations.firstOrNull { it.toAnnotationClassIdSafe(session) == classId }

	private companion object {
		val ROUTE_ARGUMENT: Name = Name.identifier("route")
	}
}
