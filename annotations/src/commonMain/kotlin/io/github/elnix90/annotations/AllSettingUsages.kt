package io.github.elnix90.annotations

/**
 * Marks a property whose value should be generated as the complete list of
 * settings usages discovered during compilation of the current module.
 *
 * Example:
 *
 * ```kotlin
 * @AllSettingUsages
 * val usages: List<SettingUsage> = emptyList()
 * ```
 *
 * is transformed into something equivalent to:
 *
 * ```kotlin
 * val usages: List<SettingUsage> = listOf(
 *     SettingUsage(MyStore.enabled, "com.app.SettingsScreen"),
 *     SettingUsage({ if (isDynamic()) A.x else B.y }, "com.app.OtherScreen")
 * )
 * ```
 *
 * Every call site of a function annotated with [SettingCall] in the current
 * compilation module is discovered and recorded, together with the fully
 * qualified name of the enclosing function (the "screen" identifier used for
 * navigation from search results).
 *
 * Combine the generated list with
 * `io.github.elnix90.runtime.search.SettingsSearchIndex` to offer
 * boilerplate-free settings search: app developers annotate nothing beyond
 * this single property.
 *
 * The property must be typed `List<SettingUsage>` (enforced by the compiler
 * plugin with the `ALL_SETTING_USAGES_WRONG_TYPE` diagnostic).
 *
 * The annotation is retained in the compiled class files so it can be
 * processed by the compiler plugin during IR generation.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class AllSettingUsages
