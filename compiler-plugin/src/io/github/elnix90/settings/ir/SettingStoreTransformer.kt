//package io.github.elnix90.settings.ir
//
//import com.sun.tools.javac.code.TypeAnnotationPosition.field
//import io.github.elnix90.settings.ir.hasSettingKey
//import io.github.elnix90.settings.ir.isSettingStore
//import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
//import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
//import org.jetbrains.kotlin.ir.IrStatement
//import org.jetbrains.kotlin.ir.declarations.IrClass
//import org.jetbrains.kotlin.ir.declarations.createExpressionBody
//import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
//import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
//import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
//import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
//import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
//import org.jetbrains.kotlin.ir.util.properties
//import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
//import org.jetbrains.kotlin.name.FqName
//
//@OptIn(UnsafeDuringIrConstructionAPI::class)
//class SettingStoreTransformer(
//    private val context: IrPluginContext,
//) : IrElementTransformerVoid() {
//
//    override fun visitClass(declaration: IrClass): IrStatement {
//        if (context.isSettingStore(declaration)) {
//            generateAllPropertyBody(declaration)
//        }
//        return super.visitClass(declaration)
//    }
//
//    @OptIn(FirIncompatiblePluginAPI::class)
//    private fun generateAllPropertyBody(storeClass: IrClass) {
//        val allProperty = storeClass.properties.firstOrNull {
//            it.name.asString() == "ALL"
//        } ?: return
//
//        val settingKeyProperties = storeClass.properties
//            .filter { prop ->
//                prop.name.asString() != "ALL" &&
//                        context.hasSettingKey(prop)
//            }
//            .toList()
//
//        if (settingKeyProperties.isEmpty()) {
//            return
//        }
//
//
//        val listOfSymbol = context.irBuiltIns.listOf
//        // Find listOf symbol
////        @Suppress("DEPRECATION")
////        val listOfSymbol = context.referenceFunctions(FqName("kotlin.collections.listOf"))
////            .firstOrNull()
////            ?: error("Could not find kotlin.collections.listOf")
//
//        // Build property references: this.prop1, this.prop2, ...
//        val propertyRefs = settingKeyProperties.mapNotNull { prop ->
//            val backingField = prop.backingField ?: return@mapNotNull null
//
//            val thisReceiver = IrGetValueImpl(
//                startOffset = storeClass.startOffset,
//                endOffset = storeClass.endOffset,
//                symbol = storeClass.thisReceiver?.symbol
//                    ?: error("No 'this' receiver in ${storeClass.name}"),
//                origin = null
//            )
//
//
//            org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl(
//                startOffset = storeClass.startOffset,
//                endOffset = storeClass.endOffset,
//                symbol = backingField.symbol,
//                receiver = thisReceiver,
//                origin = null,
//                superQualifierSymbol = null
//            )
//
//            org.jetbrains.kotlin.ir.builders.irGetField(
//                receiver = thisReceiver,
//                field = backingField
//            )
//
//            context.irFactory.createField(
//                receiver = thisReceiver,
//                field = backingField,
//                origin = null
//            )
//
//            IrGetFieldImpl(
//                startOffset = storeClass.startOffset,
//                endOffset = storeClass.endOffset,
//                symbol = backingField.symbol,
//                type = prop.type,
//                receiver = thisReceiver,
//                origin = null,
//                superQualifierSymbol = null
//            )
//        }
//
//        // Build listOf(...) call
//        val listOfCall = IrCallImpl(
//            startOffset = storeClass.startOffset,
//            endOffset = storeClass.endOffset,
//            type = context.irBuiltIns.anyType,
//            symbol = listOfSymbol
//        )
//
//        // Add arguments (use .arguments mutable list directly)
//        propertyRefs.forEach { ref ->
//            listOfCall.arguments.add(ref)
//        }
//
//        // Set initializer on backing field
//        allProperty.backingField?.initializer = context.irFactory.createExpressionBody(
//            listOfCall
//        )
//    }
//}