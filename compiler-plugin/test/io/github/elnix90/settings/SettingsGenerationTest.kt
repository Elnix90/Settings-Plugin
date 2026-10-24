package io.github.elnix90.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests of the plugin's code generation: automatic keys, the generated
 * `ALL` collection of every store, and the `@AllStores` registry.
 *
 * The fixture sources are compiled by the real CLI compiler and the resulting
 * classes are executed through reflection, so a regression in any phase (FIR
 * generation, IR transformation, key injection) fails these tests.
 */
internal class SettingsGenerationTest {
	@Test
	fun `ALL contains every setting with its generated key`() {
		CompilationTestHarness
			.compile(
				"ValidStore.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.objects.SettingObject
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					object TestStore : MapSettingsStore() {
						@SettingKey
						val enabled = boolean(default = true)

						@SettingKey
						val customKey = boolean(key = "custom_key")

						@SettingKey
						val wrapped = wrappedBoolean()

						fun wrappedBoolean(key: String = ""): SettingObject<Boolean, Boolean> = boolean(key = key)
					}
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val keys = result.collectAllKeys("test.TestStore")

				// `enabled` and `wrapped` get their automatic keys, `customKey` keeps its explicit one.
				assertEquals(setOf("enabled", "custom_key", "wrapped"), keys)
			}
	}

	@Test
	fun `AllStores gathers every store of the module`() {
		CompilationTestHarness
			.compile(
				"AllStoresHolder.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllStores
					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					object StoreA : MapSettingsStore() {
						@SettingKey
						val settingInA = boolean()
					}

					@SettingsStore
					object StoreB : MapSettingsStore()

					// The fully qualified name avoids the import clash between the
					// `SettingsStore` annotation and the `SettingsStore` base class.
					@AllStores
					val allStores: Set<io.github.elnix90.core.stores.SettingsStore<*, *>> = emptySet()
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val allStores = result
					.loadClass("test.AllStoresHolderKt")
					.getMethod("getAllStores")
					.invoke(null) as Set<*>

				assertEquals(
					setOf("test.StoreA", "test.StoreB"),
					allStores.map { it!!.javaClass.name }.toSet()
				)
			}
	}
}

/**
 * Instantiates the store object and returns the keys of every setting contained in
 * its generated `ALL` collection.
 */
private fun CompilationResult.collectAllKeys(storeFqName: String): Set<String> {
	val storeClass = loadClass(storeFqName)
	val instance = storeClass.getField("INSTANCE").get(null)

	// The getter of `ALL` located by signature to stay independent of JVM name mangling.
	val allGetter = storeClass.methods.first { it.parameterCount == 0 && it.returnType == Set::class.java }
	val all = allGetter.invoke(instance) as Set<*>

	val keys = mutableSetOf<String>()
	for (setting in all) {
		keys += setting!!.javaClass.getMethod("getKey").invoke(setting) as String
	}
	return keys
}
