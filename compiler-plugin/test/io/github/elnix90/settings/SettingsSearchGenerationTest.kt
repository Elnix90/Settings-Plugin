package io.github.elnix90.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests of the settings usage discovery: `@SettingCall` and
 * `@SettingEntry` call sites recorded into the generated `@AllSettingUsages`
 * list, with the screen (`@SettingScreen` route when present) they render on.
 *
 * The fixture sources are compiled by the real CLI compiler and the generated
 * index is executed through reflection, so a regression in any phase (checker
 * validation, IR collection, list generation) fails these tests.
 */
internal class SettingsSearchGenerationTest {
	@Test
	fun `index records static and dynamic usages with their screens`() {
		CompilationTestHarness
			.compile(
				"SearchIndexHolder.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages
					import io.github.elnix90.annotations.SettingCall
					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingScreen
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.objects.SettingObject
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					object TestStore : MapSettingsStore() {
						@SettingKey
						val alpha = boolean()

						@SettingKey
						val beta = boolean()
					}

					object ScreenARoute

					@SettingCall
					fun Wrapper(setting: SettingObject<*, *>) {
						setting.key
					}

					fun alwaysTrue(): Boolean = true

					@SettingScreen(ScreenARoute::class)
					fun ScreenA() {
						Wrapper(TestStore.alpha)
					}

					fun ScreenB() {
						Wrapper(if (alwaysTrue()) TestStore.alpha else TestStore.beta)
					}

					fun ScreenC(flag: Boolean) {
						Wrapper(if (flag) TestStore.alpha else TestStore.beta)
					}

					@AllSettingUsages
					val usages: List<io.github.elnix90.runtime.search.SettingUsage> = emptyList()
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val usages = result
					.loadClass("test.SearchIndexHolderKt")
					.getMethod("getUsages")
					.invoke(null) as List<*>

				// ScreenA (static) and ScreenB (dynamic) are indexed; ScreenC
				// captures a local parameter and is skipped by design.
				assertEquals(2, usages.size)

				val byScreen = usages.associateBy { it!!.invokeMethod("getScreenFqName") }

				// Static usage: the owning store is known at compile time, and
				// the @SettingScreen route object is recorded for navigation.
				val static = byScreen.getValue("test.ScreenA")
				assertEquals("test.TestStore", static.invokeMethod("getStoreFqName"))
				assertEquals("alpha", static.invokeMethod("getSetting").invokeMethod("getKey"))
				assertEquals("test.ScreenARoute", static.invokeMethod("getScreenRoute")!!.javaClass.name)

				// Dynamic usage: resolved lazily through the provider, store
				// unknown, and no route (its screen declares no @SettingScreen).
				val dynamic = byScreen.getValue("test.ScreenB")
				assertEquals("", dynamic.invokeMethod("getStoreFqName"))
				assertEquals(null, dynamic.invokeMethod("getScreenRoute"))
				// The generated provider is a hidden lambda class, so reflect through
				// the public Function0 interface instead of the class itself.
				val resolved = (dynamic.invokeMethod("getSettingProvider") as Function0<*>).invoke()
				assertEquals("alpha", resolved.invokeMethod("getKey"))
			}
	}

	@Test
	fun `index records manual entries with their resources and screen route`() {
		CompilationTestHarness
			.compile(
				"EntryIndexHolder.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages
					import io.github.elnix90.annotations.SettingEntry
					import io.github.elnix90.annotations.SettingScreen

					object Res {
						object string {
							val setWallpaper = 42
							val setWallpaperDesc = 43
						}

						object drawable {
							val wallpaper = 44
						}
					}

					object WallpaperRoute

					@SettingScreen(WallpaperRoute::class)
					fun WallpaperScreen() {
						SetWallpaperButton(
							title = Res.string.setWallpaper,
							description = Res.string.setWallpaperDesc,
							icon = Res.drawable.wallpaper
						)
					}

					@SettingEntry
					fun SetWallpaperButton(title: Int, description: Int = 0, icon: Int = 0) {
					}

					// description/icon omitted here: the wrapper's `= 0` defaults are snapshotted.
					fun ScreenWithoutRoute() {
						SetWallpaperButton(title = Res.string.setWallpaper)
					}

					@AllSettingUsages
					val usages: List<io.github.elnix90.runtime.search.SettingUsage> = emptyList()
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val usages = result
					.loadClass("test.EntryIndexHolderKt")
					.getMethod("getUsages")
					.invoke(null) as List<*>

				// Both call sites are indexed as manual entries.
				assertEquals(2, usages.size)

				val byScreen = usages.associateBy { it!!.invokeMethod("getScreenFqName") }

				// The screen annotated with @SettingScreen contributes its metadata
				// expressions - snapshotted from the call site at compile time - and route.
				val entry = byScreen.getValue("test.WallpaperScreen")
				assertEquals(42, entry.invokeMethod("getTitle"))
				assertEquals(43, entry.invokeMethod("getDescription"))
				assertEquals(44, entry.invokeMethod("getIcon"))
				assertEquals("test.WallpaperRoute", entry.invokeMethod("getScreenRoute")!!.javaClass.name)

				// Without @SettingScreen the entry still works, route omitted.
				val unannotated = byScreen.getValue("test.ScreenWithoutRoute")
				assertEquals(42, unannotated.invokeMethod("getTitle"))
				assertEquals(0, unannotated.invokeMethod("getDescription"))
				assertEquals(0, unannotated.invokeMethod("getIcon"))
				assertEquals(null, unannotated.invokeMethod("getScreenRoute"))
			}
	}

	@Test
	fun `index without usages compiles to an empty list`() {
		CompilationTestHarness
			.compile(
				"EmptyIndexHolder.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages

					@AllSettingUsages
					val usages: List<io.github.elnix90.runtime.search.SettingUsage> = emptyList()
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val usages = result
					.loadClass("test.EmptyIndexHolderKt")
					.getMethod("getUsages")
					.invoke(null) as List<*>

				assertEquals(0, usages.size)
			}
	}

	@Test
	fun `index excludes settings hidden by property or enclosing function`() {
		CompilationTestHarness
			.compile(
				"HiddenIndexHolder.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages
					import io.github.elnix90.annotations.SettingCall
					import io.github.elnix90.annotations.SettingHidden
					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingScreen
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.objects.SettingObject
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					object TestStore : MapSettingsStore() {
						@SettingKey
						val visible = boolean()

						@SettingKey
						@SettingHidden
						val hiddenProperty = boolean()
					}

					object VisibleRoute

					@SettingCall
					fun Wrapper(setting: SettingObject<*, *>) {
						setting.key
					}

					@SettingScreen(VisibleRoute::class)
					fun VisibleScreen() {
						Wrapper(TestStore.visible)
					}

					// The hidden property is skipped wherever it is rendered.
					fun UsesHiddenProperty() {
						Wrapper(TestStore.hiddenProperty)
					}

					// Every setting inside a @SettingHidden function is skipped,
					// including one whose property is otherwise visible.
					@SettingHidden
					fun HiddenDialog() {
						Wrapper(TestStore.visible)
					}

					// Visibility is unaffected outside the hidden function.
					fun AfterHidden() {
						Wrapper(TestStore.visible)
					}

					@AllSettingUsages
					val usages: List<io.github.elnix90.runtime.search.SettingUsage> = emptyList()
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val usages = result
					.loadClass("test.HiddenIndexHolderKt")
					.getMethod("getUsages")
					.invoke(null) as List<*>

				// UsesHiddenProperty and HiddenDialog are excluded; VisibleScreen
				// and AfterHidden remain (two sites of TestStore.visible).
				assertEquals(2, usages.size)

				val screens = usages.map { it!!.invokeMethod("getScreenFqName") as String }.sorted()
				assertEquals(listOf("test.AfterHidden", "test.VisibleScreen"), screens)

				for (u in usages) {
					assertEquals("visible", u.invokeMethod("getSetting").invokeMethod("getKey"))
				}
			}
	}

	@Test
	fun `settings nested in lambdas keep their screen via source range`() {
		CompilationTestHarness
			.compile(
				"DeepNestingHolder.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages
					import io.github.elnix90.annotations.SettingCall
					import io.github.elnix90.annotations.SettingKey
					import io.github.elnix90.annotations.SettingScreen
					import io.github.elnix90.annotations.SettingsStore
					import io.github.elnix90.core.objects.SettingObject
					import io.github.elnix90.core.stores.MapSettingsStore

					@SettingsStore
					object TestStore : MapSettingsStore() {
						@SettingKey
						val alpha = boolean()
					}

					object DeepRoute

					@SettingCall
					fun Wrapper(setting: SettingObject<*, *>) {
						setting.key
					}

					// Mimics Compose lambdas: BoxWithConstraints { if (x) { ... } else { ... } }
					fun boxWithConstraints(content: () -> Unit) = content()
					fun column(content: () -> Unit) = content()
					fun animatedContent(state: Int, content: (Int) -> Unit) = content(state)
					fun group(content: () -> Unit) = content()

					@SettingScreen(DeepRoute::class)
					fun DeepScreen() {
						boxWithConstraints {
							if (true) {
								column {
									animatedContent(0) { s ->
										when (s) {
											0 -> {
												group { Wrapper(TestStore.alpha) }
											}
										}
									}
								}
							} else {
								column {
									group { Wrapper(TestStore.alpha) }
								}
							}
						}
					}

					@AllSettingUsages
					val usages: List<io.github.elnix90.runtime.search.SettingUsage> = emptyList()
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val usages = result
					.loadClass("test.DeepNestingHolderKt")
					.getMethod("getUsages")
					.invoke(null) as List<*>

				assertEquals(2, usages.size)
				val screens = usages.map { it!!.invokeMethod("getScreenFqName") }
				assertEquals(listOf("test.DeepScreen", "test.DeepScreen"), screens)
				for (u in usages) {
					assertEquals("test.DeepRoute", u.invokeMethod("getScreenRoute")!!.javaClass.name)
				}
			}
	}

	@Test
	fun `entry annotation on a local function records its screen and route`() {
		CompilationTestHarness
			.compile(
				"LocalEntryHolder.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages
					import io.github.elnix90.annotations.SettingEntry
					import io.github.elnix90.annotations.SettingScreen

					object Res {
						object string {
							val setWallpaper = 42
						}
					}

					object LocalRoute

					fun button() {}

					@SettingScreen(LocalRoute::class)
					fun LocalScreen() {
						@SettingEntry
						fun setWallpaper(title: Int = Res.string.setWallpaper) {
							button()
						}
						setWallpaper()
					}

					@AllSettingUsages
					val usages: List<io.github.elnix90.runtime.search.SettingUsage> = emptyList()
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val usages = result
					.loadClass("test.LocalEntryHolderKt")
					.getMethod("getUsages")
					.invoke(null) as List<*>

				assertEquals(1, usages.size)
				val entry = usages.first()!!
				// The omitted argument falls back to the local function's default.
				assertEquals(42, entry.invokeMethod("getTitle"))
				assertEquals("test.LocalScreen", entry.invokeMethod("getScreenFqName"))
				assertEquals("test.LocalRoute", entry.invokeMethod("getScreenRoute")!!.javaClass.name)
			}
	}

	@Test
	fun `entry metadata is baked statically while dynamic sites are skipped`() {
		CompilationTestHarness
			.compile(
				"EntryMetadataHolder.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages
					import io.github.elnix90.annotations.SettingEntry

					@SettingEntry
					fun Entry(title: Int, description: Int = 0, icon: Int = 0) {
					}

					// A literal title is snapshotted into the generated index...
					fun StaticSite() {
						Entry(title = 7)
					}

					// ...a 0 title could never be found, and a title capturing a local
					// cannot be evaluated at index initialization: both are skipped.
					fun ZeroTitleSite() {
						Entry(title = 0)
					}

					fun CapturingSite(resourceId: Int) {
						Entry(title = resourceId)
					}

					@AllSettingUsages
					val usages: List<io.github.elnix90.runtime.search.SettingUsage> = emptyList()
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val usages = result
					.loadClass("test.EntryMetadataHolderKt")
					.getMethod("getUsages")
					.invoke(null) as List<*>

				// StaticSite only; ZeroTitleSite and CapturingSite are skipped by design.
				assertEquals(1, usages.size)
				val entry = usages.first()!!
				assertEquals(7, entry.invokeMethod("getTitle"))
				assertEquals("test.StaticSite", entry.invokeMethod("getScreenFqName"))
			}
	}

	@Test
	fun `entry defaulted from a static field read is indexed`() {
		CompilationTestHarness
			.compile(
				"FieldDefaultEntryHolder.kt" to
					"""
					package test

					import io.github.elnix90.annotations.AllSettingUsages
					import io.github.elnix90.annotations.SettingEntry

					// Mimics an Android R class: static (non-final) int fields.
					object R {
						@JvmField
						val set_wallpaper: Int = 42

						@JvmField
						val wallpaper: Int = 44
					}

					@SettingEntry
					fun SetWallpaperButton(title: Int = R.set_wallpaper, description: Int = 0, icon: Int = R.wallpaper) {
					}

					// All arguments omitted: every value must come from the defaults.
					fun WallpaperScreen() {
						SetWallpaperButton()
					}

					@AllSettingUsages
					val usages: List<io.github.elnix90.runtime.search.SettingUsage> = emptyList()
					""".trimIndent()
			).use { result ->
				result.assertSucceeded()

				val usages = result
					.loadClass("test.FieldDefaultEntryHolderKt")
					.getMethod("getUsages")
					.invoke(null) as List<*>

				assertEquals(1, usages.size)
				val entry = usages.first()!!
				assertEquals(42, entry.invokeMethod("getTitle"))
				assertEquals(44, entry.invokeMethod("getIcon"))
			}
	}
}

/** Invokes a no-arg method on an arbitrary compiled instance, for readable assertions. */
private fun Any?.invokeMethod(name: String): Any? =
	requireNotNull(this).javaClass.getMethod(name).invoke(this)
