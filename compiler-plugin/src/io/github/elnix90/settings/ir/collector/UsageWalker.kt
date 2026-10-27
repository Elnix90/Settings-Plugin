package io.github.elnix90.settings.ir.collector

import io.github.elnix90.settings.PluginDebug
import io.github.elnix90.settings.util.ClassIds.settingCallAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingEntryAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingHiddenAnnotationClassId
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid

/**
 * Walks every function body of the module and records each call site of a
 * `@SettingCall` or `@SettingEntry` function, together with the screen it is
 * rendered on. `@SettingHidden` wins over both: on the callee itself, on any
 * enclosing function, or on an enclosing `@SettingScreen` recovered from source
 * ranges (Compose hoists screens off the IR ancestor stack).
 */
internal class UsageWalker(
	private val recorder: UsageRecorder,
	private val discovery: WrapperDiscovery
) {
	fun collectUsages(module: IrModuleFragment): List<CollectedUsage> {
		val usages = mutableListOf<CollectedUsage>()
		val functionStack = mutableListOf<IrFunction>()
		val screenRanges = module.collectScreenRanges()

		var currentFile: IrFile? = null
		var callsInFile = 0
		var entryMatchesInFile = 0
		var wrapperCandidatesInFile = 0
		var wrapperGatesInFile = 0

		module.accept(
			object : IrVisitorVoid() {
				override fun visitElement(element: IrElement) {
					element.acceptChildren(this, null)
				}

				override fun visitFile(element: IrFile) {
					currentFile = element
					callsInFile = 0
					entryMatchesInFile = 0
					wrapperCandidatesInFile = 0
					wrapperGatesInFile = 0
					super.visitFile(element)
					PluginDebug.log(
						"file ${element.fileEntry.name}: walked $callsInFile calls, " +
							"$entryMatchesInFile @SettingEntry callee matches, " +
							"wrapperCandidates=$wrapperCandidatesInFile wrapperGates=$wrapperGatesInFile"
					)
					currentFile = null
				}

				override fun visitFunction(declaration: IrFunction) {
					functionStack += declaration
					super.visitFunction(declaration)
					functionStack.removeLast()
				}

				override fun visitCall(expression: IrCall) {
					val callee = expression.symbol.owner
					callsInFile++

					if (isHidden(expression, callee, functionStack, currentFile, screenRanges)) {
						super.visitCall(expression)
						return
					}

					val screen =
						screenInfo(functionStack, currentFile, expression.startOffset, screenRanges)

					when {
						callee.hasAnnotation(settingCallAnnotationClassId) -> {
							recorder
								.recordUsage(expression, expression.symbol, screen)
								?.let { usages += it }
						}

						callee.hasAnnotation(settingEntryAnnotationClassId) -> {
							entryMatchesInFile++
							logEntryCall(expression, currentFile)
							recorder
								.recordEntry(expression, screen, emptyList())
								?.let { usages += it }
						}

						else -> {
							// Wrapper discovery: an unannotated function whose
							// body (possibly through further wrappers) renders
							// a @SettingEntry call - RouteItem -> SettingsItem -
							// is discovered through its own call sites.
							val chain = discovery.entryChain(expression.symbol)
							val inner = chain.lastOrNull() ?: run {
								super.visitCall(expression)
								return
							}
							wrapperCandidatesInFile++
							val gate = inner.titleCapturesLocals()
							if (gate) wrapperGatesInFile++
							if (PluginDebug.isEnabled) {
								PluginDebug.log(
									"wrapper TRY ${callee.fqNameWhenAvailable} -> " +
										"${inner.symbol.fqNameWhenAvailable} depth=${chain.size} gate=$gate " +
										"titleArg=" +
										(
											inner.arguments
												.getOrNull(
													inner.symbol.owner.parameters.indexOfFirst {
														it.name == TITLE_PARAMETER_NAME
													}
												)?.let { it::class.simpleName } ?: "none"
										)
								)
							}
							// The inner call records itself when its title is
							// static; only a local-capturing title moves the
							// entry out to this call site - one entry either way.
							if (gate) {
								entryMatchesInFile++
								logWrapperCall(expression, inner, currentFile)
								recorder
									.recordEntry(expression, screen, chain)
									?.let { usages += it }
							}
						}
					}

					super.visitCall(expression)
				}
			},
			null
		)

		return usages
	}

	/** `@SettingHidden` on the callee, an enclosing function, or the enclosing screen. */
	private fun isHidden(
		expression: IrCall,
		callee: IrFunction,
		functionStack: List<IrFunction>,
		file: IrFile?,
		screenRanges: Map<IrFile, List<ScreenRange>>
	): Boolean =
		callee.hasAnnotation(settingHiddenAnnotationClassId) ||
			functionStack.any { it.hasAnnotation(settingHiddenAnnotationClassId) } ||
			file
				?.let { screenRanges[it] }
				?.any { range ->
					expression.startOffset >= range.startOffset &&
						expression.startOffset <= range.endOffset &&
						range.isHidden
				} == true

	private fun logEntryCall(expression: IrCall, file: IrFile?) {
		if (!PluginDebug.isEnabled) return
		val fileEntry = file?.fileEntry
		val line = fileEntry?.let { it.getLineNumber(expression.startOffset) + 1 } ?: -1
		PluginDebug.log(
			"entry CALL ${expression.symbol.fqNameWhenAvailable} at " +
				"${fileEntry?.name ?: "?"}:$line " +
				"args=${expression.arguments.map { arg -> arg?.let { it::class.simpleName } ?: "null" }}"
		)
	}

	private fun logWrapperCall(outer: IrCall, inner: IrCall, file: IrFile?) {
		if (!PluginDebug.isEnabled) return
		val fileEntry = file?.fileEntry
		val line = fileEntry?.let { it.getLineNumber(outer.startOffset) + 1 } ?: -1
		PluginDebug.log(
			"wrapper CALL ${outer.symbol.fqNameWhenAvailable} -> " +
				"${inner.symbol.fqNameWhenAvailable} at " +
				"${fileEntry?.name ?: "?"}:$line"
		)
	}
}
