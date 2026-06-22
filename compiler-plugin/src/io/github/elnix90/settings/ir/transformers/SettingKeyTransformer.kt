package io.github.elnix90.settings.ir.transformers

import io.github.elnix90.settings.util.hasSettingKeyAnnotation
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
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
internal class SettingKeyTransformer(
    private val ctx: IrPluginContext,
) : IrElementTransformerVoid() {

    override fun visitProperty(
        declaration: IrProperty
    ): IrStatement {
        resolveSettingKeyAnnotation(declaration)
        return super.visitProperty(declaration)
    }

    private fun resolveSettingKeyAnnotation(declaration: IrProperty) {
        if (ctx.hasSettingKeyAnnotation(declaration)) {
            val expression: IrExpression =
                declaration.backingField
                    ?.initializer
                    ?.expression
                    ?: return

            val call: IrCall =
                expression
                    .findCall()
                    ?: return

            val parameters: List<IrValueParameter> =
                call.symbol.owner.parameters

            val keyParameter: IrValueParameter? =
                parameters.firstOrNull {
                    it.name.asString() == "key"
                }

            val keyIndex = keyParameter?.indexInParameters ?: error("No key parameter found for property ${declaration.name}")
            val currentArgument = call.arguments[keyIndex]

            val shouldGenerateKey = currentArgument == null

            val keyValue = declaration.name.asString()

            if (shouldGenerateKey) {
                val keyValue =
                    IrConstImpl.string(
                        startOffset = call.startOffset,
                        endOffset = call.endOffset,
                        type = ctx.irBuiltIns.stringType,
                        value = keyValue
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