package io.github.elnix90.settings.ir.collector

import io.github.elnix90.settings.util.ClassIds.settingHiddenAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingScreenAnnotationClassId
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.Name

private val ROUTE_ARGUMENT_NAME = Name.identifier("route")

/** The screen a usage is rendered on (FqName + route object from `@SettingScreen`). */
internal data class ScreenInfo(
	val fqName: String,
	val routeClass: IrClassSymbol?
)

/**
 * Source range of a `@SettingScreen` function. The Compose transform hoists
 * composable lambdas out of their enclosing function, so a setting call inside
 * such a lambda no longer sees its screen as an IR ancestor; the call's source
 * position recovers it instead.
 */
internal data class ScreenRange(
	val startOffset: Int,
	val endOffset: Int,
	val fqName: String,
	val routeClass: IrClassSymbol?,
	val isHidden: Boolean
)

/**
 * Resolves the screen of a call site:
 * 1. nearest enclosing function annotated with `@SettingScreen` (helper
 *    composables in between do not skew the result),
 * 2. else the `@SettingScreen` function whose source range contains the call
 *    (recovers screens Compose hoisted off the IR ancestor chain),
 * 3. else the nearest enclosing non-anonymous function.
 */
internal fun screenInfo(
	functionStack: List<IrFunction>,
	file: IrFile?,
	callStartOffset: Int,
	screenRanges: Map<IrFile, List<ScreenRange>>
): ScreenInfo {
	val annotatedScreen =
		functionStack
			.lastOrNull { it.hasAnnotation(settingScreenAnnotationClassId) }

	if (annotatedScreen != null) {
		return ScreenInfo(
			fqName = annotatedScreen.fqNameWhenAvailable?.asString() ?: currentScreen(functionStack),
			routeClass = annotatedScreen.routeClass()
		)
	}

	val byRange = file?.let { screenRanges[it]?.screenContaining(callStartOffset) }
	if (byRange != null) {
		return ScreenInfo(fqName = byRange.fqName, routeClass = byRange.routeClass)
	}

	return ScreenInfo(fqName = currentScreen(functionStack), routeClass = null)
}

/** The nearest enclosing non-anonymous function, as the screen fallback identifier. */
private fun currentScreen(functionStack: List<IrFunction>): String =
	functionStack
		.lastOrNull { !it.name.asString().startsWith("<") }
		?.fqNameWhenAvailable
		?.asString()
		?: functionStack.lastOrNull()?.name?.asString()
		?: "<unknown>"

/** The innermost screen range containing [offset], or null. */
private fun List<ScreenRange>.screenContaining(offset: Int): ScreenRange? =
	if (offset == UNDEFINED_OFFSET) {
		null
	} else {
		asSequence()
			.filter { offset >= it.startOffset && offset <= it.endOffset }
			.minByOrNull { it.endOffset - it.startOffset }
	}

/** Source ranges of every `@SettingScreen` function in the module, keyed by file. */
internal fun IrModuleFragment.collectScreenRanges(): Map<IrFile, List<ScreenRange>> {
	val ranges = mutableMapOf<IrFile, MutableList<ScreenRange>>()

	files.forEach { file ->
		file.accept(
			visitor = object : IrVisitorVoid() {
				override fun visitElement(element: IrElement) {
					element.acceptChildren(this, null)
				}

				override fun visitFunction(declaration: IrFunction) {
					val start = declaration.startOffset
					val end = declaration.endOffset
					val fqName = declaration.fqNameWhenAvailable?.asString()

					if (declaration.hasAnnotation(settingScreenAnnotationClassId) &&
						start != UNDEFINED_OFFSET &&
						end != UNDEFINED_OFFSET &&
						fqName != null
					) {
						ranges.getOrPut(file) { mutableListOf() } +=
							ScreenRange(
								startOffset = start,
								endOffset = end,
								fqName = fqName,
								routeClass = declaration.routeClass(),
								isHidden = declaration.hasAnnotation(settingHiddenAnnotationClassId)
							)
					}

					super.visitFunction(declaration)
				}
			},
			data = null
		)
	}

	return ranges
}

/**
 * Reads the `route` KClass argument of a function's `@SettingScreen` annotation,
 * or null when absent or not an object (the checker reports the latter; the
 * usage keeps working without the typed route).
 */
private fun IrFunction.routeClass(): IrClassSymbol? {
	val annotation = getAnnotation(settingScreenAnnotationClassId.asSingleFqName()) ?: return null

	val routeReference = annotation.argumentMapping[ROUTE_ARGUMENT_NAME] as? IrClassReference ?: return null
	val routeSymbol = routeReference.symbol as? IrClassSymbol ?: return null

	return routeSymbol.takeIf { routeSymbol.owner.kind == ClassKind.OBJECT }
}
