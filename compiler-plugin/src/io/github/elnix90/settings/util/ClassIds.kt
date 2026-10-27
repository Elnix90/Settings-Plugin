package io.github.elnix90.settings.util

import io.github.elnix90.annotations.AllSettingUsages
import io.github.elnix90.annotations.AllStores
import io.github.elnix90.annotations.SettingCall
import io.github.elnix90.annotations.SettingEntry
import io.github.elnix90.annotations.SettingHidden
import io.github.elnix90.annotations.SettingKey
import io.github.elnix90.annotations.SettingScreen
import io.github.elnix90.annotations.SettingsStore
import org.jetbrains.kotlin.descriptors.runtime.structure.classId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object ClassIds {
	internal val settingsStoreAnnotationClassId = SettingsStore::class.java.classId
	internal val settingKeyAnnotationClassId = SettingKey::class.java.classId
	internal val allStoresAnnotationClassId = AllStores::class.java.classId
	internal val settingCallAnnotationClassId = SettingCall::class.java.classId
	internal val settingEntryAnnotationClassId = SettingEntry::class.java.classId
	internal val settingHiddenAnnotationClassId = SettingHidden::class.java.classId
	internal val settingScreenAnnotationClassId = SettingScreen::class.java.classId
	internal val allSettingUsagesAnnotationClassId = AllSettingUsages::class.java.classId

	internal val mapSettingsStoreClassId = ClassId.topLevel(FqName("io.github.elnix90.core.stores.MapSettingsStore"))
	internal val settingObjectClassId = ClassId.topLevel(FqName("io.github.elnix90.core.objects.SettingObject"))
	internal val settingsStoreClassId = ClassId.topLevel(FqName("io.github.elnix90.core.stores.SettingsStore"))

	internal val settingUsageClassId = ClassId.topLevel(FqName("io.github.elnix90.runtime.search.SettingUsage"))
	internal val settingUsageObjectClassId = settingUsageClassId.createNestedClassId(Name.identifier("Object"))
	internal val settingUsageOtherClassId = settingUsageClassId.createNestedClassId(Name.identifier("Entry"))
	internal val kotlinListClassId = ClassId.topLevel(FqName("kotlin.collections.List"))
	internal val composableAnnotationClassId = ClassId.topLevel(FqName("androidx.compose.runtime.Composable"))
}
