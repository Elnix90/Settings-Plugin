package io.github.elnix90.settings.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

private const val settingKeyFqn = "io.github.elnix90.settings.SettingKey"
private val settingKeyClassId = ClassId.topLevel(FqName(settingKeyFqn))

fun IrPluginContext.hasSettingKey(
    declaration: IrProperty
): Boolean =
    declaration.hasAnnotation(
        referenceClass(
            settingKeyClassId
        ) ?: return false
    )


val settingStoreFqnName = FqName("io.github.elnix90.settings.SettingStore")
val settingStoreClassId = ClassId.topLevel(settingStoreFqnName)



fun IrPluginContext.isSettingStore(
    declaration: IrClass
): Boolean =
    declaration.hasAnnotation(
        referenceClass(
            settingStoreClassId
        ) ?: return false
    )

