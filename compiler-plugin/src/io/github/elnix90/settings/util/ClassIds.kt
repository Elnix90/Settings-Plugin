package io.github.elnix90.settings.util

import io.github.elnix90.annotations.AllStores
import io.github.elnix90.annotations.SettingKey
import io.github.elnix90.annotations.SettingsStore
import org.jetbrains.kotlin.descriptors.runtime.structure.classId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal object ClassIds {
    internal val settingsStoreAnnotationClassId = SettingsStore::class.java.classId
    internal val settingKeyAnnotationClassId = SettingKey::class.java.classId
    internal val allStoresAnnotationClassId = AllStores::class.java.classId

    internal val mapSettingsStoreClassId = ClassId.topLevel(FqName("io.github.elnix90.core.stores.MapSettingsStore"))
    internal val settingObjectClassId = ClassId.topLevel(FqName("io.github.elnix90.core.objects.SettingObject"))
    internal val settingsStoreClassId = ClassId.topLevel(FqName("io.github.elnix90.core.stores.SettingsStore"))
}