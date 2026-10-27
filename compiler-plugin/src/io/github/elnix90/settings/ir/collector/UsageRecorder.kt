package io.github.elnix90.settings.ir.collector

import io.github.elnix90.settings.PluginDebug
import io.github.elnix90.settings.util.ClassIds.settingHiddenAnnotationClassId
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.Name

/**
 * Extracts a recorded usage from one call site:
 * - `@SettingCall`: the `SettingObject` argument, classified static/dynamic;
 * - `@SettingEntry`: the `title`/`description`/`icon` metadata, resolved
 *   through a wrapper chain down to the concrete call site.
 */
internal class UsageRecorder(
	private val ctx: IrPluginContext
) {
	/**
	 * The recorded setting argument of a `@SettingCall` invocation, classified
	 * as static or dynamic. Null when absent (default parameter value) or
	 * capturing local state.
	 */
	fun recordUsage(
		expression: IrCall,
		callee: IrSimpleFunctionSymbol,
		screen: ScreenInfo
	): CollectedUsage? {
		val settingIndex =
			callee
				.owner
				.parameters
				.indexOfFirst { it.isSettingObjectParameter() }
				.takeIf { it >= 0 }
				?: callee.owner.parameters.indexOfFirst { it.name.asString() == "setting" }

		if (settingIndex < 0) return null

		val argument = expression.arguments.getOrNull(settingIndex) ?: return null

		if (argument is IrCall) {
			val getter = argument.symbol.owner
			val receiver = argument.dispatchReceiver

			if (getter.correspondingPropertySymbol != null && receiver is IrGetObjectValue) {
				// `@SettingHidden` on the store property hides this setting everywhere.
				val isHidden =
					getter
						.correspondingPropertySymbol
						?.owner
						?.hasAnnotation(settingHiddenAnnotationClassId) == true

				if (isHidden) return null

				return CollectedUsage.Static(
					getter = argument.symbol,
					storeSymbol = receiver.symbol,
					argumentType = argument.type,
					screenFqName = screen.fqName,
					routeClass = screen.routeClass
				)
			}
		}

		if (argument.containsLocalCapture()) return null

		return CollectedUsage.Dynamic(
			argument = argument,
			screenFqName = screen.fqName,
			routeClass = screen.routeClass
		)
	}

	/**
	 * Snapshots the metadata of a `@SettingEntry` call into a manual entry.
	 *
	 * [chain] holds the calls from the wrapper body down to the annotated call
	 * (empty for a direct call). The entry is recorded at [site] - the outer
	 * call site, so the screen is the one rendering the wrapper - with the
	 * metadata taken from the annotated call, each wrapper level's parameters
	 * bound to the arguments its call passes (innermost first).
	 *
	 * Null when no statically evaluable title exists; the entry is then
	 * skipped (the checker already reports a missing `title: Int` at the
	 * declaration).
	 */
	fun recordEntry(
		site: IrCall,
		screen: ScreenInfo,
		chain: List<IrCall>
	): CollectedUsage? {
		val entryCall = chain.lastOrNull() ?: site
		val entryCallee = entryCall.symbol
		val substitutions =
			if (chain.isEmpty()) {
				emptyList()
			} else {
				chain.dropLast(1).reversed().map { it to it.symbol } + (site to site.symbol)
			}

		val title =
			entryCall
				.metadataArgument(entryCallee, TITLE_PARAMETER_NAME, substitutions)
				?.takeUnless { it is IrConst && it.value == 0 }
				?: run {
					PluginDebug.log(
						"entry SKIP ${entryCallee.owner.fqNameWhenAvailable} on ${screen.fqName}: " +
							"no statically evaluable title " +
							"(argOrNull=${entryCall.arguments.getOrNull(0)}, " +
							"hasDefault=${entryCallee.owner.parameters.any { it.defaultValue != null }}, "
					)
					return null
				}

		val description =
			entryCall.metadataArgument(entryCallee, DESCRIPTION_PARAMETER_NAME, substitutions)
				?: ctx.intConstant(0)
		val icon =
			entryCall.metadataArgument(entryCallee, ICON_PARAMETER_NAME, substitutions)
				?: ctx.intConstant(0)

		return CollectedUsage.Entry(
			title = title,
			description = description,
			icon = icon,
			screenFqName = screen.fqName,
			routeClass = screen.routeClass
		)
	}

	/**
	 * The value passed for metadata parameter [name]: the call-site argument
	 * when present, else the callee's default (recovered from the body for
	 * composables, where the Compose transform moved it; an omitted argument of
	 * an external declaration has no readable default and yields null).
	 *
	 * [substitutions] bind each wrapper level's parameters (innermost first) so
	 * metadata written against a wrapper resolves to the concrete call site's
	 * values. The result is only embedded when [isStaticIntResource] accepts
	 * it; local captures, branches and calls with arguments would mean
	 * different values than at the render site, so null is returned and the
	 * caller skips - silent by design: a valid annotation never fails the build.
	 */
	private fun IrCall.metadataArgument(
		callee: IrSimpleFunctionSymbol,
		name: Name,
		substitutions: List<Pair<IrCall, IrSimpleFunctionSymbol>>
	): IrExpression? {
		val index = callee.owner.parameters.indexOfFirst { it.name == name }
		if (index < 0) {
			PluginDebug.log("meta SKIP ${callee.owner.fqNameWhenAvailable}: no parameter named '$name'")
			return null
		}

		val parameter = callee.owner.parameters[index]
		val argument = arguments.getOrNull(index)
		val defaultExpression = parameter.defaultValue?.expression
		// The Compose transform strips `defaultValue` from composable
		// parameters and fills omitted slots with a placeholder composite; the
		// real default is assigned inside the callee body and recovered there.
		val expression =
			when {
				argument is IrComposite && argument.origin == IrStatementOrigin.DEFAULT_VALUE -> {
					callee.bodyDefault(name)
				}

				argument != null -> {
					argument
				}

				else -> {
					defaultExpression ?: callee.bodyDefault(name)
				}
			}
		if (expression == null) {
			if (PluginDebug.isEnabled) {
				PluginDebug.log(
					"meta SKIP ${callee.owner.fqNameWhenAvailable}.$name: no usable value " +
						"(arg=${argument?.let { it::class.simpleName } ?: "none"}, " +
						"paramDefault=${defaultExpression != null})\n" +
						(
							callee.owner.body
								?.dump()
								?.take(2000) ?: "<no body>"
						).prependIndent("    ")
				)
			}
			return null
		}

		val resolved =
			substitutions.fold(expression) { acc, (wrapperCall, wrapperCallee) ->
				acc.substituted(wrapperCall, wrapperCallee)
			}

		if (!resolved.isStaticIntResource()) {
			if (PluginDebug.isEnabled) {
				PluginDebug.log(
					"meta SKIP ${callee.owner.fqNameWhenAvailable}.$name: not statically evaluable, " +
						"source=${if (argument != null) "call-site" else "default"}, " +
						(if (substitutions.isNotEmpty()) "wrapper-resolved, " else "") +
						"expr=${resolved::class.simpleName}\n" +
						resolved.dump().prependIndent("    ").take(3000)
				)
			}
			return null
		}

		return resolved.deepCopyWithSymbols()
	}

	/**
	 * The default expression for [name] recovered from the callee's body: the
	 * Compose transform moves composable defaults into an `IrSetValue` on the
	 * parameter at the top of the body. Null when the body holds no such
	 * assignment; external declarations have no body at all.
	 */
	private fun IrSimpleFunctionSymbol.bodyDefault(name: Name): IrExpression? {
		var recovered: IrExpression? = null
		owner.body?.accept(
			object : IrVisitorVoid() {
				override fun visitElement(element: IrElement) {
					element.acceptChildren(this, null)
				}

				override fun visitSetValue(expression: IrSetValue) {
					val target = expression.symbol.owner
					if (recovered == null && target is IrValueParameter && target.name == name) {
						recovered = expression.value
					}
					super.visitSetValue(expression)
				}
			},
			null
		)
		return recovered
	}
}
