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