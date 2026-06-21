package io.github.elnix90.settings.util

import io.github.elnix90.settings.util.ClassIds.allStoresAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingKeyAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingsStoreAnnotationClassId
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.util.hasAnnotation

internal fun IrPluginContext.hasAllStoresAnnotation(
    declaration: IrProperty
): Boolean =
    declaration.hasAnnotation(referenceClass(allStoresAnnotationClassId) ?: return false)


internal fun IrPluginContext.hasSettingKeyAnnotation(
    declaration: IrProperty
): Boolean =
    declaration.hasAnnotation(referenceClass(settingKeyAnnotationClassId) ?: return false)


internal fun IrPluginContext.hasSettingsStoreAnnotation(
    declaration: IrClass
): Boolean =
    declaration.hasAnnotation(referenceClass(settingsStoreAnnotationClassId) ?: return false)
