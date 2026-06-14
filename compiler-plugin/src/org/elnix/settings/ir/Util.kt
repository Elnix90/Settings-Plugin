package org.elnix.settings.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

private const val settingKeyFqn = "org.elnix.settings.SettingKey"

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



private const val settingStoreFqn = "org.elnix.settings.SettingStore"

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
