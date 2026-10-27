package io.github.elnix90.annotations

/**
 * Marks a function as a place where a setting is rendered, so the compiler
 * plugin can discover every settings usage in the module at compile time.
 *
 * Applied **once** by the `:ui` library on its wrapper functions (and by any
 * user-written wrapper that renders a setting):
 *
 * ```kotlin
 * @Composable
 * @SettingCall
 * public fun DragonGroupScope.Setting(setting: BooleanSettingObject, ...) { ... }
 * ```
 *
 * App developers annotate **nothing**: every call site of an annotated wrapper
 * is automatically recorded into the list generated for
 * [AllSettingUsages], which powers boilerplate-free settings search.
 *
 * The function must declare at least one parameter whose type is a
 * [io.github.elnix90.core.objects.SettingObject] (enforced by the compiler
 * plugin with the `SETTING_CALL_NO_SETTING_PARAMETER` diagnostic), because that
 * is the argument recorded as the usage. Functions that render settings should
 * be `@Composable` (`SETTING_CALL_NOT_COMPOSABLE` warning otherwise) so their
 * call sites live on screens.
 *
 * Usage sites are classified by the plugin:
 *
 * - a simple property reference (`Setting(MyStore.enabled)`) produces a static
 *   entry that knows the owning store;
 * - any other expression (`Setting(if (x) a else b)`) produces a lazy entry
 *   resolved at runtime.
 *
 * Dynamic expressions that capture local state are not indexed (they cannot be
 * re-evaluated from the generated index); this never fails the compilation.
 *
 * The annotation is retained in the compiled class files so it can be
 * processed by the compiler plugin during IR generation.
 *
 * For actions that are not backed by a `SettingObject` (e.g. a "Set wallpaper"
 * button), use [SettingEntry] instead; to make the settings of a screen
 * directly navigable from search results, annotate the screen with
 * [SettingScreen].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class SettingCall
