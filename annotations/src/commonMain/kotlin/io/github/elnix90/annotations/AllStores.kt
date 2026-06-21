package io.github.elnix90.annotations

/**
 * Marks a property whose value should be generated as the complete set of
 * settings stores discovered during compilation.
 *
 * Example:
 *
 * ```kotlin
 * @AllStores
 * val allStores: Set<SettingsStore<*, *>> = emptySet()
 * ```
 *
 * is transformed into something equivalent to:
 *
 * ```kotlin
 * val allStores: Set<SettingsStore<*, *>> = setOf(
 *     AppearanceSettingsStore,
 *     ColorSettingsStore,
 *     WellbeingSettingsStore
 * )
 * ```
 *
 * Every object annotated with `@SettingsStore` in the current compilation
 * module is automatically added to the generated set.
 *
 * This allows applications and libraries to access a complete registry of
 * settings stores without maintaining the list manually.
 *
 * The annotation is retained in the compiled class files so it can be
 * processed by the compiler plugin during IR generation.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class AllStores