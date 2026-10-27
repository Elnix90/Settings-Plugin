package io.github.elnix90.settings

import kotlin.test.Test

/**
 * End-to-end tests for the real-time lint of the settings search annotations
 * (`@SettingCall`, `@SettingEntry`, `@SettingScreen`, `@AllSettingUsages`),
 * mirroring [SettingsLintTest].
 */
internal class SettingsSearchLintTest {
	@Test
	fun `setting call without a setting parameter is reported`() {
		CompilationTestHarness
			.compile(
				"NoSettingParam.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingCall

					@SettingCall
					fun Wrapper(value: Int) = value
					""".trimIndent()
			).use { result ->
				// When a declaration carries an error, the CLI pipeline surfaces only
				// that error; the accompanying composable warning is covered by the
				// error-free fixture below.
				result.assertErrorCount(1)
				result.assertErrorContaining(NO_SETTING_PARAMETER_MESSAGE)
			}
	}

	@Test
	fun `setting call on a non-composable function is reported as a warning`() {
		CompilationTestHarness
			.compile(
				"NonComposable.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingCall
					import io.github.elnix90.core.objects.SettingObject

					@SettingCall
					fun Wrapper(setting: SettingObject<*, *>) = setting
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()
				result.assertWarningCount(1)
				result.assertWarningContaining(NOT_COMPOSABLE_MESSAGE)
			}
	}

	@Test
	fun `setting entry combined with setting call is reported`() {
		CompilationTestHarness
			.compile(
				"MixedEntry.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingCall
					import io.github.elnix90.annotations.SettingEntry
					import io.github.elnix90.core.objects.SettingObject

					@SettingEntry
					@SettingCall
					fun Wrapper(setting: SettingObject<*, *>, title: Int) = setting
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(MIXED_ANNOTATIONS_MESSAGE)
			}
	}

	@Test
	fun `setting entry without a title parameter is reported`() {
		CompilationTestHarness
			.compile(
				"NoTitleParameter.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingEntry

					@SettingEntry
					fun Entry() {
					}
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(MISSING_TITLE_MESSAGE)
			}
	}

	@Test
	fun `setting entry title parameter of the wrong type is reported`() {
		CompilationTestHarness
			.compile(
				"StringTitleParameter.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingEntry

					@SettingEntry
					fun Entry(title: String) {
					}
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(MISSING_TITLE_MESSAGE)
			}
	}

	@Test
	fun `nullable setting entry title parameter is reported`() {
		CompilationTestHarness
			.compile(
				"NullableTitleParameter.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingEntry

					@SettingEntry
					fun Entry(title: Int?) {
					}
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(MISSING_TITLE_MESSAGE)
			}
	}

	@Test
	fun `setting entry on a non-composable function is reported as a warning`() {
		CompilationTestHarness
			.compile(
				"NonComposableEntry.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingEntry

					@SettingEntry
					fun Entry(title: Int) {
					}
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()
				result.assertWarningCount(1)
				result.assertWarningContaining(ENTRY_NOT_COMPOSABLE_MESSAGE)
			}
	}

	@Test
	fun `screen route that is not an object is reported`() {
		CompilationTestHarness
			.compile(
				"NotAnObjectRoute.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingScreen

					class PlainRoute

					@SettingScreen(PlainRoute::class)
					fun Screen() {
					}
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(ROUTE_NOT_OBJECT_MESSAGE)
			}
	}

	@Test
	fun `screen that is not composable is reported as a warning`() {
		CompilationTestHarness
			.compile(
				"NonComposableScreen.kt" to
					"""
					package test

					import io.github.elnix90.annotations.SettingScreen

					object SomeRoute

					@SettingScreen(SomeRoute::class)
					fun Screen() {
					}
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()
				result.assertWarningCount(1)
				result.assertWarningContaining(SCREEN_NOT_COMPOSABLE_MESSAGE)
			}
	}

	@Test
	fun `all setting usages with a wrong type is reported`() {
		CompilationTestHarness
			.compile(
				"WrongType.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages

					@AllSettingUsages
					val usages: List<String> = emptyList()
					""".trimIndent()
			).use { result ->
				result.assertErrorCount(1)
				result.assertErrorContaining(WRONG_TYPE_MESSAGE)
			}
	}

	@Test
	fun `search diagnostics are suppressible by name`() {
		CompilationTestHarness
			.compile(
				"Suppressed.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages
					import io.github.elnix90.annotations.SettingCall
					import io.github.elnix90.annotations.SettingEntry
					import io.github.elnix90.annotations.SettingScreen

					@Suppress("SETTING_CALL_NO_SETTING_PARAMETER", "SETTING_CALL_NOT_COMPOSABLE")
					@SettingCall
					fun Wrapper(value: Int) = value

					@Suppress("SETTING_ENTRY_NOT_COMPOSABLE")
					@SettingEntry
					fun Entry(title: Int) {
					}

					object SomeRoute

					@Suppress("SETTING_SCREEN_NOT_COMPOSABLE")
					@SettingScreen(SomeRoute::class)
					fun Screen() {
					}

					@Suppress("ALL_SETTING_USAGES_WRONG_TYPE")
					@AllSettingUsages
					val usages: List<String> = emptyList()
					""".trimIndent()
			).use { result ->
				// The suppressed wrong-typed index is skipped by the IR phase
				// instead of being filled with mismatched data.
				result.assertSucceeded()
			}
	}

	private companion object {
		private const val NO_SETTING_PARAMETER_MESSAGE =
			"'@SettingCall' function must declare a parameter whose type is 'SettingObject'"

		private const val NOT_COMPOSABLE_MESSAGE =
			"'@SettingCall' should be applied to a '@Composable' function"

		private const val MIXED_ANNOTATIONS_MESSAGE =
			"'@SettingEntry' cannot be combined with '@SettingCall'"

		private const val MISSING_TITLE_MESSAGE =
			"'@SettingEntry' function must declare a non-nullable 'title: Int' parameter"

		private const val ENTRY_NOT_COMPOSABLE_MESSAGE =
			"'@SettingEntry' should be applied to a '@Composable' function"

		private const val ROUTE_NOT_OBJECT_MESSAGE =
			"'@SettingScreen' route must be an object"

		private const val SCREEN_NOT_COMPOSABLE_MESSAGE =
			"'@SettingScreen' should be applied to a '@Composable' function"

		private const val WRONG_TYPE_MESSAGE =
			"'@AllSettingUsages' property must be declared as 'List<SettingUsage>'"
	}
}
