package io.github.elnix90.settings.fir

import io.github.elnix90.settings.diagnostics.SettingsErrors
import io.github.elnix90.settings.util.ClassIds.settingKeyAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingsStoreAnnotationClassId
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol

/**
 * Real-time lint for properties annotated with `@SettingKey`.
 *
 * Two rules are enforced during frontend analysis, so mistakes are reported in the
 * IDE as you type and abort the build with a proper diagnostic instead of crashing
 * the compiler later in the IR phase:
 *
 * 1. **Placement** - the property must be a direct member of a class or object
 *    annotated with `@SettingsStore`. Only direct members end up in the generated
 *    `ALL` collection, so top-level, local, or nested-class properties would be
 *    silently ignored (or crash key generation) otherwise.
 *
 * 2. **Initializer** - the initializer must resolve to a settings builder call
 *    exposing a `key` parameter (e.g. `boolean(...)`), because that is the call the
 *    IR phase rewrites to inject the automatic key. Anything else (constants,
 *    aliases, unresolved calls, missing initializers) is rejected here with a
 *    readable message instead of failing with an internal compiler error.
 *
 * Both rules mirror the behavior of
 * [io.github.elnix90.settings.ir.transformers.SettingKeyTransformer] and
 * [io.github.elnix90.settings.ir.transformers.SettingsStoreTransformer] exactly,
 * so the checker never rejects code the backend could handle.
 *
 * Reported diagnostics can be suppressed with `@Suppress("SETTING_KEY_OUTSIDE_STORE")`
 * or `@Suppress("SETTING_KEY_INVALID_INITIALIZER")`.
 */
internal class SettingKeyChecker : FirPropertyChecker(MppCheckerKind.Common) {
	context(
		context: CheckerContext,
		reporter: DiagnosticReporter
	)
	override fun check(
		declaration: FirProperty
	) {
		if (!declaration.hasAnnotation(settingKeyAnnotationClassId, context.session)) {
			return
		}

		// `containingDeclarations` is ordered outermost -> innermost and does not
		// contain the property being checked, so the last entry is its immediate container.
		val containingClass = context.containingDeclarations.lastOrNull() as? FirClassSymbol<*>
		val isDirectMemberOfStore =
			containingClass?.hasAnnotation(settingsStoreAnnotationClassId, context.session) == true

		if (!isDirectMemberOfStore) {
			reporter.reportOn(declaration.source, SettingsErrors.SETTING_KEY_OUTSIDE_STORE, context)
			return
		}

		if (!hasSettingsBuilderInitializer(declaration)) {
			reporter.reportOn(declaration.source, SettingsErrors.SETTING_KEY_INVALID_INITIALIZER, context)
		}
	}

	/**
	 * Returns true when the initializer is (or unwraps to) a function call whose callee
	 * declares a value parameter named `key` - exactly the shape
	 * [io.github.elnix90.settings.ir.transformers.SettingKeyTransformer] can rewrite.
	 *
	 * Unwrapping rules (blocks, type casts, safe calls) deliberately mirror the IR
	 * `findCall()` helper so that both phases agree on what a valid initializer is.
	 *
	 * When the callee cannot be resolved (broken code with other reported errors),
	 * this returns true to avoid cascading false positives.
	 */
	private fun hasSettingsBuilderInitializer(declaration: FirProperty): Boolean {
		val initializer = declaration.initializer ?: return false
		val call = initializer.findSettingBuilderCall() ?: return false

		val callee = (call.calleeReference as? FirResolvedNamedReference)?.resolvedSymbol ?: return true

		// Property accesses (aliases such as `val b = a`), constructor-less
		// references and operator-invoke conventions have no `key` parameter.
		return callee is FirFunctionSymbol<*> && callee.valueParameterSymbols.any { it.name.asString() == "key" }
	}

	/**
	 * Recursively unwraps common FIR structures until the actual initializer call
	 * is reached, mirroring `IrExpression.findCall()` from the IR phase.
	 */
	private fun FirExpression.findSettingBuilderCall(): FirFunctionCall? =
		when (this) {
			is FirFunctionCall -> {
				this
			}

			is FirBlock -> {
				statements
					.lastOrNull()
					?.let { it as? FirExpression }
					?.findSettingBuilderCall()
			}

			is FirTypeOperatorCall -> {
				argumentList.arguments.firstOrNull()?.findSettingBuilderCall()
			}

			is FirSafeCallExpression -> {
				(selector as? FirExpression)?.findSettingBuilderCall()
			}

			else -> {
				null
			}
		}
}
