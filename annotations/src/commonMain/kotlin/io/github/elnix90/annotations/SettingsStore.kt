package io.github.elnix90.annotations

/**
 * Marks a settings store whose `ALL` property should be generated automatically
 * by the compiler plugin.
 *
 * When applied to a class or object extending a settings store base type, the
 * plugin generates an override of the `ALL` property containing every property
 * annotated with [SettingKey] declared inside the store.
 *
 * Example:
 *
 * ```
 * @SettingsStore
 * object UiSettingsStore : MapSettingsStore(...) {
 *
 *     @SettingKey
 *     val settingA = ...
 *
 *     @SettingKey
 *     val settingB = ...
 * }
 * ```
 *
 * is transformed into a declaration equivalent to:
 *
 * ```
 * override val ALL = listOf(
 *     settingA,
 *     settingB
 * )
 * ```
 *
 * This removes the need to manually maintain a list of settings and ensures
 * that newly added or removed settings are automatically reflected in the
 * store's collection.
 *
 * The annotation is retained in the compiled class files so it remains
 * available to both the FIR and IR phases of the compiler plugin.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class SettingsStore
