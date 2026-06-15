package io.github.elnix90.settings.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

private const val settingKeyFqn = "io.github.elnix90.settings.SettingKey"

private val settingKeyClassId = ClassId.topLevel(
    FqName(settingKeyFqn)
)

fun IrPluginContext.hasSettingKey(
    declaration: IrProperty
): Boolean =
    declaration.hasAnnotation(
        referenceClass(
            settingKeyClassId
        ) ?: return false
    )



private const val settingStoreFqn = "io.github.elnix90.settings.SettingStore"

private val settingStoreClassId = ClassId.topLevel(
    FqName(settingKeyFqn)
)


fun IrPluginContext.isSettingStore(
    declaration: IrProperty
): Boolean =
    declaration.hasAnnotation(
        referenceClass(
            settingStoreClassId
        ) ?: return false
    )
