package io.github.elnix90.settings.ir.collector

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

/**
 * Builds the `SettingUsage` constructor calls of the generated index from the
 * recorded usages: `SettingUsage.Object(MyStore.setting, screen, store, route)`
 * for static references, a provider-lambda overload for dynamic expressions,
 * and `SettingUsage.Entry(title, description, icon, screen, route)` for
 * `@SettingEntry` metadata.
 */
internal class SettingUsageEmitter(
	private val ctx: IrPluginContext
) {
	fun CollectedUsage.toSettingUsageExpression(
		primaryConstructor: IrConstructorSymbol,
		staticConstructor: IrConstructorSymbol?,
		entryConstructor: IrConstructorSymbol?,
		usageType: IrType,
		settingObjectClass: IrClassSymbol,
		parent: IrDeclarationParent
	): IrExpression? =
		when (this) {
			is CollectedUsage.Static -> {
				toSettingUsageExpression(
					primaryConstructor = primaryConstructor,
					staticConstructor = staticConstructor,
					usageType = usageType,
					settingObjectClass = settingObjectClass,
					parent = parent
				)
			}

			is CollectedUsage.Dynamic -> {
				toSettingUsageExpression(
					primaryConstructor = primaryConstructor,
					usageType = usageType,
					settingObjectClass = settingObjectClass,
					parent = parent
				)
			}

			is CollectedUsage.Entry -> {
				// Without the runtime class for manual entries there is nothing
				// to construct; skip rather than emit broken IR.
				entryConstructor?.let { toSettingUsageExpression(it, usageType) }
			}
		}

	private fun CollectedUsage.Static.toSettingUsageExpression(
		primaryConstructor: IrConstructorSymbol,
		staticConstructor: IrConstructorSymbol?,
		usageType: IrType,
		settingObjectClass: IrClassSymbol,
		parent: IrDeclarationParent
	): IrExpression {
		val settingExpression = storeGetterCall()

		// Without the dedicated (setting, screen, store, route) constructor the
		// static entry degrades to a provider lambda - still correct, just lazy.
		if (staticConstructor == null) {
			return primaryConstructor.callWithProvider(
				settingExpression,
				usageType,
				settingObjectClass,
				parent,
				screenFqName,
				storeFqName,
				routeClass
			)
		}

		return staticConstructor.callWithSetting(
			settingExpression,
			usageType,
			screenFqName,
			storeFqName,
			routeClass
		)
	}

	private fun CollectedUsage.Dynamic.toSettingUsageExpression(
		primaryConstructor: IrConstructorSymbol,
		usageType: IrType,
		settingObjectClass: IrClassSymbol,
		parent: IrDeclarationParent
	): IrExpression =
		primaryConstructor.callWithProvider(
			argument,
			usageType,
			settingObjectClass,
			parent,
			screenFqName,
			storeFqName = "",
			routeClass = routeClass
		)

	private fun CollectedUsage.Entry.toSettingUsageExpression(
		constructor: IrConstructorSymbol,
		usageType: IrType
	): IrExpression =
		IrConstructorCallImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = usageType,
			symbol = constructor,
			typeArgumentsCount = 0,
			constructorTypeArgumentsCount = 0
		).apply {
			arguments[0] = title.deepCopyWithSymbols()
			arguments[1] = description.deepCopyWithSymbols()
			arguments[2] = icon.deepCopyWithSymbols()
			arguments[3] = ctx.stringConstant(screenFqName)
			arguments[4] = ctx.routeExpression(routeClass)
		}

	/** `MyStore.setting` as a getter call on the store object. */
	private fun CollectedUsage.Static.storeGetterCall(): IrCall =
		IrCallImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = argumentType,
			origin = null,
			symbol = getter,
			superQualifierSymbol = null
		).apply {
			dispatchReceiver =
				IrGetObjectValueImpl(
					startOffset = UNDEFINED_OFFSET,
					endOffset = UNDEFINED_OFFSET,
					type = storeSymbol.owner.defaultType,
					symbol = storeSymbol
				)
		}

	private fun IrConstructorSymbol.callWithSetting(
		setting: IrExpression,
		usageType: IrType,
		screenFqName: String,
		storeFqName: String,
		routeClass: IrClassSymbol?
	): IrExpression =
		IrConstructorCallImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = usageType,
			symbol = this,
			typeArgumentsCount = 0,
			constructorTypeArgumentsCount = 0
		).apply {
			arguments[0] = setting
			arguments[1] = ctx.stringConstant(screenFqName)
			arguments[2] = ctx.stringConstant(storeFqName)
			arguments[3] = ctx.routeExpression(routeClass)
		}

	private fun IrConstructorSymbol.callWithProvider(
		argument: IrExpression,
		usageType: IrType,
		settingObjectClass: IrClassSymbol,
		parent: IrDeclarationParent,
		screenFqName: String,
		storeFqName: String,
		routeClass: IrClassSymbol?
	): IrExpression =
		IrConstructorCallImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = usageType,
			symbol = this,
			typeArgumentsCount = 0,
			constructorTypeArgumentsCount = 0
		).apply {
			arguments[0] = argument.createProviderLambda(settingObjectClass, parent)
			arguments[1] = ctx.stringConstant(screenFqName)
			arguments[2] = ctx.stringConstant(storeFqName)
			arguments[3] = ctx.routeExpression(routeClass)
		}

	/** Wraps the expression in `{ expression }: SettingObject<*, *>`. */
	private fun IrExpression.createProviderLambda(
		settingObjectClass: IrClassSymbol,
		parent: IrDeclarationParent
	): IrExpression {
		val settingObjectType =
			IrSimpleTypeImpl(
				classifier = settingObjectClass,
				hasQuestionMark = false,
				arguments = listOf(IrStarProjectionImpl, IrStarProjectionImpl),
				annotations = emptyList()
			)

		val lambdaType =
			IrSimpleTypeImpl(
				classifier = ctx.irBuiltIns.functionN(0).symbol,
				hasQuestionMark = false,
				arguments = listOf(makeTypeProjection(settingObjectType, Variance.INVARIANT)),
				annotations = emptyList()
			)

		val lambda =
			ctx.irFactory.createSimpleFunction(
				startOffset = UNDEFINED_OFFSET,
				endOffset = UNDEFINED_OFFSET,
				origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA,
				name = Name.special("<anonymous>"),
				visibility = DescriptorVisibilities.LOCAL,
				isInline = false,
				isExpect = false,
				returnType = settingObjectType,
				modality = Modality.FINAL,
				symbol = IrSimpleFunctionSymbolImpl(),
				isTailrec = false,
				isSuspend = false,
				isOperator = false,
				isInfix = false
			)

		lambda.parent = parent
		lambda.body = ctx.irFactory.createExpressionBody(deepCopyWithSymbols())

		return IrFunctionExpressionImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = lambdaType,
			function = lambda,
			origin = IrStatementOrigin.LAMBDA
		)
	}
}
