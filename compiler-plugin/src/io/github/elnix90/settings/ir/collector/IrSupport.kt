package io.github.elnix90.settings.ir.collector

import io.github.elnix90.settings.util.ClassIds.kotlinListClassId
import io.github.elnix90.settings.util.ClassIds.settingObjectClassId
import io.github.elnix90.settings.util.ClassIds.settingUsageClassId
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.SimpleTypeNullability
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal val TITLE_PARAMETER_NAME = Name.identifier("title")
internal val DESCRIPTION_PARAMETER_NAME = Name.identifier("description")
internal val ICON_PARAMETER_NAME = Name.identifier("icon")

private val INT_CLASS_ID: ClassId = ClassId.topLevel(FqName("kotlin.Int"))

internal fun IrPluginContext.stringConstant(value: String): IrExpression =
	IrConstImpl.string(
		startOffset = UNDEFINED_OFFSET,
		endOffset = UNDEFINED_OFFSET,
		type = irBuiltIns.stringType,
		value = value
	)

internal fun IrPluginContext.intConstant(value: Int): IrExpression =
	IrConstImpl.int(
		startOffset = UNDEFINED_OFFSET,
		endOffset = UNDEFINED_OFFSET,
		type = irBuiltIns.intType,
		value = value
	)

/** `SomeRoute` as an object value, or `null` when the screen declared no route. */
internal fun IrPluginContext.routeExpression(route: IrClassSymbol?): IrExpression =
	if (route == null) {
		IrConstImpl.constNull(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = irBuiltIns.anyNType
		)
	} else {
		IrGetObjectValueImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = route.owner.defaultType,
			symbol = route
		)
	}

/** True when the type is exactly `List<SettingUsage>`. */
internal fun IrType.isSettingUsageList(): Boolean {
	val listType = this as? IrSimpleType ?: return false
	val listClassifier = listType.classifier as? IrClassSymbol ?: return false
	if (listClassifier.owner.classId != kotlinListClassId) return false

	val elementClassifier =
		(listType.arguments.singleOrNull() as? IrSimpleType)
			?.classifier as? IrClassSymbol
			?: return false

	return elementClassifier.owner.classId == settingUsageClassId
}

/** True when the parameter is typed `SettingObject<*, *>`, as enforced by the `@SettingCall` checker. */
internal fun IrValueParameter.isSettingObjectParameter(): Boolean {
	val classifier = (type as? IrSimpleType)?.classifier as? IrClassSymbol ?: return false
	return classifier.owner.classId == settingObjectClassId
}

/** True when the expression reads locals it could not capture at module level. */
internal fun IrExpression.containsLocalCapture(): Boolean {
	var captured = false

	accept(
		object : IrVisitorVoid() {
			override fun visitElement(element: IrElement) {
				element.acceptChildren(this, null)
			}

			override fun visitGetValue(expression: IrGetValue) {
				when (expression.symbol.owner) {
					is IrVariable,
					is IrValueParameter -> captured = true

					else -> Unit
				}
			}
		},
		null
	)

	return captured
}

/**
 * True when the expression produces a non-null `Int` that can be evaluated once
 * at index initialization without changing semantics: constants, `R` field
 * reads and parameterless property getters (receivers included). Local captures,
 * branches and calls with arguments are rejected.
 */
internal fun IrExpression.isStaticIntResource(): Boolean {
	val expressionType = type as? IrSimpleType ?: return false
	if (expressionType.nullability == SimpleTypeNullability.MARKED_NULLABLE) return false
	if ((expressionType.classifier as? IrClassSymbol)?.owner?.classId != INT_CLASS_ID) return false

	var staticallyEvaluable = true

	accept(
		object : IrVisitorVoid() {
			override fun visitElement(element: IrElement) {
				val allowed =
					element is IrConst ||
						element is IrGetField ||
						element is IrGetObjectValue ||
						(
							element is IrCall &&
								element.symbol.owner.correspondingPropertySymbol != null
						)

				if (!allowed) {
					staticallyEvaluable = false
					return
				}

				element.acceptChildren(this, null)
			}
		},
		null
	)

	return staticallyEvaluable
}
