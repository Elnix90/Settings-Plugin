package io.github.elnix90.settings.ir.collector

import io.github.elnix90.settings.util.ClassIds.settingEntryAnnotationClassId
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid

private const val MAX_WRAPPER_DEPTH = 4

/**
 * Discovers `@SettingEntry` calls behind wrappers: an unannotated function
 * whose body (directly or through further wrapper levels) calls a
 * `@SettingEntry` composable is itself discoverable - `RouteItem(route)`
 * wrapping `SettingsItem(title = route.resId)`, or a wrapper wrapping such a
 * wrapper, up to [MAX_WRAPPER_DEPTH] levels.
 */
internal class WrapperDiscovery {
	private val chainCache = mutableMapOf<IrSimpleFunctionSymbol, List<IrCall>>()

	/**
	 * Calls from the wrapper's body down to its first `@SettingEntry` call:
	 * `[call to inner wrapper, ..., annotated call]`, or empty when the body
	 * contains no entry call. The last element's callee is the annotated one;
	 * every earlier element is the call providing the next level's arguments.
	 */
	fun entryChain(callee: IrSimpleFunctionSymbol): List<IrCall> {
		chainCache[callee]?.let { return it }
		val chain = findChain(callee, depth = 0)
		chainCache[callee] = chain
		return chain
	}

	private fun findChain(callee: IrSimpleFunctionSymbol, depth: Int): List<IrCall> {
		if (depth >= MAX_WRAPPER_DEPTH) return emptyList()

		var result: List<IrCall> = emptyList()
		callee.owner.body?.accept(
			object : IrVisitorVoid() {
				override fun visitElement(element: IrElement) {
					if (result.isEmpty()) element.acceptChildren(this, null)
				}

				override fun visitCall(expression: IrCall) {
					if (result.isNotEmpty()) return
					val fn = expression.symbol

					if (fn.owner.hasAnnotation(settingEntryAnnotationClassId)) {
						result = listOf(expression)
						return
					}

					// Prefer a direct entry call in the arguments/lambdas...
					expression.acceptChildren(this, null)
					// ...otherwise follow the callee as a further wrapper level.
					if (result.isEmpty()) {
						val nested = findChain(fn, depth + 1)
						if (nested.isNotEmpty()) result = listOf(expression) + nested
					}
				}
			},
			null
		)
		return result
	}
}

/**
 * True when the entry call's `title` argument reads a local (parameter or
 * variable, including lowered `tmp` variables). Such a call cannot be recorded
 * statically at its own site, so the wrapper's outer call site takes over;
 * a statically evaluable title is recorded at the inner call itself and the
 * outer site stays silent - exactly one entry either way.
 */
internal fun IrCall.titleCapturesLocals(): Boolean {
	val index = symbol.owner.parameters.indexOfFirst { it.name == TITLE_PARAMETER_NAME }
	if (index < 0) return false
	val argument = arguments.getOrNull(index) ?: return false
	return argument.containsLocalCapture()
}

/**
 * Replaces reads of [wrapperCallee]'s parameters with the arguments
 * [wrapperCall] passes for them (each read gets its own copy), and expands
 * local variables through their initializers (`val tmp0_title = route.resId`).
 * Applied innermost-wrapper-first across a wrapper chain, this resolves
 * metadata written several levels deep down to the concrete call site.
 * Reads of anything else are left untouched.
 */
internal fun IrExpression.substituted(
	wrapperCall: IrCall,
	wrapperCallee: IrSimpleFunctionSymbol
): IrExpression {
	val bindings =
		wrapperCallee
			.owner
			.parameters
			.mapIndexedNotNull { index, parameter ->
				wrapperCall.arguments.getOrNull(index)?.let { parameter to it }
			}.toMap()
	if (bindings.isEmpty()) return this

	var steps = 0
	val copy = deepCopyWithSymbols()
	return copy.transform(
		object : IrElementTransformerVoid() {
			override fun visitGetValue(expression: IrGetValue): IrExpression {
				if (steps > 64) return super.visitGetValue(expression)
				steps++
				val owner = expression.symbol.owner
				val replacement =
					when (owner) {
						is IrValueParameter -> bindings[owner]
						is IrVariable -> owner.initializer
						else -> null
					} ?: return super.visitGetValue(expression)
				val expanded = replacement.deepCopyWithSymbols()
				// Resolve inside the replacement too: bound arguments may be
				// temps themselves, initializers may read other locals.
				return expanded.transform(this, null)
			}
		},
		null
	)
}
