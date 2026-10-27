package io.github.elnix90.settings.ir.collector

import io.github.elnix90.settings.PluginDebug
import io.github.elnix90.settings.util.ClassIds.allSettingUsagesAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingObjectClassId
import io.github.elnix90.settings.util.ClassIds.settingUsageClassId
import io.github.elnix90.settings.util.ClassIds.settingUsageObjectClassId
import io.github.elnix90.settings.util.ClassIds.settingUsageOtherClassId
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Implements the search index for `@AllSettingUsages`: the annotated property
 * is declared by hand as
 *
 * ```
 * @AllSettingUsages
 * val usages: List<SettingUsage> = emptyList()
 * ```
 *
 * and [generateAllSettingUsagesPropertyBody] replaces its initializer with a
 * generated `listOf(SettingUsage.Object(...), SettingUsage.Entry(...), ...)`
 * built from every call site of a `@SettingCall` or `@SettingEntry` function in
 * the module (see [UsageWalker] for the walk and [UsageRecorder] for how a call
 * site becomes a usage).
 */
internal class SettingUsageCollector(
	private val ctx: IrPluginContext
) {
	private val discovery = WrapperDiscovery()
	private val recorder = UsageRecorder(ctx)
	private val walker = UsageWalker(recorder, discovery)
	private val emitter = SettingUsageEmitter(ctx)

	fun generateAllSettingUsagesPropertyBody(module: IrModuleFragment) {
		val usageHolders =
			module
				.files
				.flatMap { it.declarations }
				.filterIsInstance<IrProperty>()
				.filter { it.hasAnnotation(allSettingUsagesAnnotationClassId) }

		if (usageHolders.isEmpty()) {
			PluginDebug.log("module ${module.name}: no @AllSettingUsages holder, nothing generated")
			return
		}
		if (usageHolders.size > 1) {
			throw IllegalStateException("There must be at most 1 @AllSettingUsages value")
		}

		val holder = usageHolders.first()
		val holderField = holder.backingField ?: return

		// The property may be mistyped when the wrong-type diagnostic was
		// explicitly suppressed; never inject wrong-typed data.
		if (!holderField.type.isSettingUsageList()) return

		val settingUsageClass =
			ctx.finderForBuiltins().findClass(settingUsageClassId)
				?: return

		// Without the runtime search types on the classpath nothing can be
		// generated; degrade to the declared emptyList() instead of crashing.
		val settingObjectClass =
			ctx.finderForBuiltins().findClass(settingObjectClassId)
				?: return

		ctx.finderForBuiltins().findClass(settingUsageObjectClassId) ?: return

		// An older runtime without SettingUsage.Entry still indexes
		// SettingObject-backed usages; manual entries are dropped instead.
		val settingUsageOtherClass = ctx.finderForBuiltins().findClass(settingUsageOtherClassId)

		val usageType =
			IrSimpleTypeImpl(
				classifier = settingUsageClass,
				hasQuestionMark = false,
				arguments = emptyList(),
				annotations = emptyList()
			)

		val parent = holder.parent
		val usages = walker.collectUsages(module)

		val objectConstructors = ctx.finderForBuiltins().findConstructors(settingUsageObjectClassId)
		val primaryConstructor = objectConstructors.firstOrNull { it.owner.isPrimary } ?: return
		val staticConstructor = objectConstructors.firstOrNull { !it.owner.isPrimary }

		val entryConstructor: IrConstructorSymbol? =
			settingUsageOtherClass
				?.let { ctx.finderForBuiltins().findConstructors(settingUsageOtherClassId).firstOrNull { ctor -> ctor.owner.isPrimary } }

		with(emitter) {
			val elements =
				usages.mapNotNull { usage ->
					usage.toSettingUsageExpression(
						primaryConstructor = primaryConstructor,
						staticConstructor = staticConstructor,
						entryConstructor = entryConstructor,
						usageType = usageType,
						settingObjectClass = settingObjectClass,
						parent = parent
					)
				}

			logSummary(module, usages, elements.size, entryConstructor != null)

			val listOfSymbol =
				ctx
					.finderForBuiltins()
					.findFunctions(
						CallableId(
							FqName("kotlin.collections"),
							Name.identifier("listOf")
						)
					).first {
						it.owner.parameters
							.singleOrNull()
							?.isVararg == true
					}

			val call =
				IrCallImpl(
					startOffset = UNDEFINED_OFFSET,
					endOffset = UNDEFINED_OFFSET,
					type = holderField.type,
					origin = null,
					symbol = listOfSymbol,
					superQualifierSymbol = null
				)

			val varargParameter = listOfSymbol.owner.parameters.single()

			val vararg =
				IrVarargImpl(
					startOffset = UNDEFINED_OFFSET,
					endOffset = UNDEFINED_OFFSET,
					type = varargParameter.type,
					varargElementType = usageType,
					elements = elements
				)

			// Without the explicit type argument the resolved type breaks and
			// everything downstream is messed up.
			call.typeArguments[0] = usageType
			call.arguments[0] = vararg

			holderField.initializer = ctx.irFactory.createExpressionBody(call)
		}
	}

	private fun logSummary(
		module: IrModuleFragment,
		usages: List<CollectedUsage>,
		emitted: Int,
		hasEntryConstructor: Boolean
	) {
		if (!PluginDebug.isEnabled) return
		val entryCount = usages.count { it is CollectedUsage.Entry }
		val droppedNote =
			if (entryCount > 0 && !hasEntryConstructor) {
				" [DROPPED all $entryCount @SettingEntry usages: " +
					"SettingUsage.Entry constructor not found on classpath]"
			} else {
				""
			}
		PluginDebug.log(
			"index ${module.name}: collected=${usages.size} " +
				"(settingCalls=${usages.size - entryCount}, entries=$entryCount) " +
				"-> emitted=$emitted$droppedNote"
		)
	}
}
