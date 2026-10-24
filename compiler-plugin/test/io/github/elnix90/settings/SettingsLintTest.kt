package io.github.elnix90.settings

import kotlin.test.Test

/**
 * End-to-end tests for the real-time lint (FIR checkers) of the settings compiler
 * plugin. Every test runs the real CLI compiler with the plugin jar, so the
 * assertions cover exactly what the IDE reports while typing.
 */
internal class SettingsLintTest {
	@Test
	fun `setting key outside of any class is reported`() {
		CompilationTestHarness
			.compile(
				"TopLevel.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingKey

					@SettingKey
					val topLevelSetting = true
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(OUTSIDE_STORE_MESSAGE)
			}
	}

	@Test
	fun `setting key in a plain class is reported`() {
		CompilationTestHarness
			.compile(
				"PlainClass.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.core.stores.MapSettingsStore

					class PlainStore : MapSettingsStore() {
						@SettingKey
						val setting = boolean()
					}
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(OUTSIDE_STORE_MESSAGE)
			}
	}

	@Test
	fun `setting key nested below a store is reported`() {
		CompilationTestHarness
			.compile(
				"Nested.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					object TestStore : MapSettingsStore() {
						class Inner : MapSettingsStore() {
							@SettingKey
							val notInStore = boolean()
						}
					}
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(OUTSIDE_STORE_MESSAGE)
			}
	}

	@Test
	fun `setting key with a non-setting initializer is reported`() {
		CompilationTestHarness
			.compile(
				"BadInitializer.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					object TestStore : MapSettingsStore() {
						@SettingKey
						val notASetting = 42
					}
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(INVALID_INITIALIZER_MESSAGE)
			}
	}

	@Test
	fun `setting key aliasing another setting is reported`() {
		CompilationTestHarness
			.compile(
				"Alias.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					object TestStore : MapSettingsStore() {
						@SettingKey
						val first = boolean()

						@SettingKey
						val second = first
					}
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(INVALID_INITIALIZER_MESSAGE)
			}
	}

	@Test
	fun `setting key calling a builder without key parameter is reported`() {
		CompilationTestHarness
			.compile(
				"NoKeyParameter.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.objects.SettingObject
					import io.github.elnix90.core.stores.KeyedSettingObject
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					object TestStore : MapSettingsStore() {
						@SettingKey
						val broken = notASettingBuilder()
					}

					fun notASettingBuilder(): SettingObject<Boolean, Boolean> = KeyedSettingObject("")
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(INVALID_INITIALIZER_MESSAGE)
			}
	}

	@Test
	fun `setting key without initializer is reported`() {
		CompilationTestHarness
			.compile(
				"AbstractSetting.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.objects.SettingObject
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					abstract class AbstractStore : MapSettingsStore() {
						@SettingKey
						abstract val setting: SettingObject<Boolean, Boolean>
					}
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(INVALID_INITIALIZER_MESSAGE)
			}
	}

	@Test
	fun `settings store not extending SettingsStore crashes the compiler`() {
		CompilationTestHarness
			.compile(
				"BadStore.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingsStore

					@SettingsStore
					object NotAStore : Any()
					""".trimIndent()
			).use { result ->
				// Deliberate design (project standing order): the FIR generation
				// extension throws instead of silently skipping an invalid
				// @SettingsStore class. The crash aborts file analysis, so the
				// SETTINGS_STORE_INVALID_SUPERTYPE diagnostic never gets rendered
				// here - the crash itself is the failure mode.
				result.assertErrorCount(1)
				result.assertErrorContaining(INVALID_STORE_CRASH_MESSAGE)
			}
	}

	@Test
	fun `diagnostics are suppressible by name`() {
		CompilationTestHarness
			.compile(
				"Suppressed.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingKey

					@Suppress("SETTING_KEY_OUTSIDE_STORE")
					@SettingKey
					val topLevelSetting = true
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()
			}
	}

	@Test
	fun `valid store with plain and wrapped settings compiles cleanly`() {
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
			}
	}

	private companion object {
		private const val OUTSIDE_STORE_MESSAGE =
			"'@SettingKey' is only allowed on properties that are direct members of a class or object annotated with '@SettingsStore'"

		private const val INVALID_INITIALIZER_MESSAGE =
			"'@SettingKey' property must be initialized with a settings builder call that exposes a 'key' parameter, for example boolean(...)"

		private const val INVALID_STORE_CRASH_MESSAGE =
			"Only SettingsStores can be marked as @SettingsStore"
	}
}
