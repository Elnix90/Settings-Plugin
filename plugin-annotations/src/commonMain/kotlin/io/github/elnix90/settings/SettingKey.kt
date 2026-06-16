package io.github.elnix90.settings

/**
 * Marks a setting declaration whose key should be generated automatically by
 * the compiler plugin.
 *
 * When applied to a property, the plugin inspects the property's initializer
 * and replaces the value of the `key` parameter with the name of the property
 * itself.
 *
 * Example:
 *
 * ```
 * @SettingKey
 * val hideBetaVersionWarning = boolean(...)
 * ```
 *
 * is transformed into:
 *
 * ```
 * val hideBetaVersionWarning = boolean(
 *     key = "hideBetaVersionWarning"
 * )
 * ```
 *
 * This eliminates duplicated string literals, prevents key mismatches during
 * refactoring, and ensures that setting identifiers always stay synchronized
 * with their corresponding property names.
 *
 * The annotation is retained in the compiled class files so it remains
 * available to the compiler plugin during the IR generation phase.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class SettingKey
