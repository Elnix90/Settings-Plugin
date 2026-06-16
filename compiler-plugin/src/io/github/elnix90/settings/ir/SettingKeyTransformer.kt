package io.github.elnix90.settings.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

/**
 * IR transformer responsible for resolving the final key value of properties
 * annotated with `@SettingKey`.
 *
 * Users can declare settings without manually specifying a key:
 *
 * ```
 * @SettingKey
 * val hideBetaVersionWarning = boolean(...)
 * ```
 *
 * During IR generation, this transformer locates the initializer call used to
 * create the setting object and replaces its `key` argument with the name of
 * the property itself.
 *
 * The generated result is equivalent to:
 *
 * ```
 * val hideBetaVersionWarning = boolean(
 *     key = "hideBetaVersionWarning"
 * )
 * ```
 *
 * The transformer works by:
 *
 * 1. Visiting every property in the module.
 * 2. Filtering properties annotated with `@SettingKey`.
 * 3. Inspecting the property's initializer expression.
 * 4. Finding the underlying factory call, even when wrapped inside IR blocks
 *    or type-operator expressions.
 * 5. Locating the parameter named `key`.
 * 6. Replacing the argument with a string constant containing the property's
 *    name.
 *
 * This removes the need for manually duplicated string keys while ensuring that
 * setting identifiers remain synchronized with their corresponding property
 * names during refactoring.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class SettingKeyTransformer(
    private val context: IrPluginContext,
) : IrElementTransformerVoid() {

    override fun visitProperty(
        declaration: IrProperty
    ): IrStatement {

        val result = super.visitProperty(declaration)

        resolveSettingKeyAnnotation(declaration)

        return result
    }

    private fun resolveSettingKeyAnnotation(declaration: IrProperty) {
        if (context.hasSettingKey(declaration)) {
            val expression =
                declaration.backingField
                    ?.initializer
                    ?.expression
                    ?: return

            val call =
                expression
                    .findCall()
                    ?: return

            val parameters =
                call.symbol.owner.parameters

            val keyParameter =
                parameters.firstOrNull {
                    it.name.asString() == "key"
                }

            if (keyParameter != null) {
                val keyValue =
                    IrConstImpl.string(
                        call.startOffset,
                        call.endOffset,
                        context.irBuiltIns.stringType,
                        declaration.name.asString()
                    )

                call.arguments[keyParameter.indexInParameters] = keyValue
            }
        }
    }
}

/**
 *  Recursively unwraps common IR
 *  structures until the actual initializer call is reached.
 */
private fun IrExpression.findCall(): IrCall? =
    when (this) {
        is IrCall -> this

        is IrBlock ->
            statements
                .lastOrNull()
                ?.let { it as? IrExpression }
                ?.findCall()

        is IrTypeOperatorCall ->
            argument.findCall()

        else -> null
    }