package io.github.elnix90.core.objects

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import io.github.elnix90.core.stores.MapSettingsStore
import io.github.elnix90.core.stores.SettingsStore
import io.github.elnix90.core.util.isNotBlankKey

@ConsistentCopyVisibility
public data class DoubleSettingObject internal constructor(
    override val key: String,
    override val default: Double,
    override val title: Int?,
    override val description: Int?,
    override var onChanged: (() -> Unit)?,
    override val backupable: Boolean,
    override val settingsStore: SettingsStore<*, *>,
    val allowedRange: ClosedRange<Double>
) : SettingObject<Double, Double>() {

    override val preferenceKey: Preferences.Key<Double> = doublePreferencesKey(key)
    override fun encode(value: Double): Double = value
    override fun decode(raw: Any?): Double = getDoubleStrict(raw, default).coerceIn(allowedRange)
}

/**
 * Creates a [DoubleSettingObject] with a title and description.
 *
 * This function is used to define a double setting in a type-safe way.
 * The [key] is auto-inferred by the compiler plugin if not provided.
 * If no key is provided, it defaults to the name of the property declared with [io.github.elnix90.annotations.SettingKey].
 * Override the [key] only if you need a custom key.
 *
 * @param title The resource ID for the title of the setting. Can be `null` if no title is needed.
 * @param description The resource ID for the description of the setting. Can be `null` if no description is needed.
 * @param default The default value for the setting.
 * @param allowedRange The allowed range of values for the setting.
 * @param key The key for the setting in the preferences store. If empty, the compiler plugin auto-infers it.
 * @param onChanged Optional callback invoked when the setting value changes.
 * @param backupable Whether the setting should be included in backups. Defaults to `true`.
 * @return A [DoubleSettingObject] configured with the provided parameters.
 */
public fun MapSettingsStore.double(
    default: Double,
    allowedRange: ClosedRange<Double>,
    title: Int? = null,
    description: Int? = null,
    key: String = "",
    onChanged: (() -> Unit)? = null,
    backupable: Boolean = true
): DoubleSettingObject = DoubleSettingObject(
    key = key.isNotBlankKey,
    title = title,
    description = description,
    default = default,
    allowedRange = allowedRange,
    onChanged = onChanged,
    backupable = backupable,
    settingsStore = this
)

private fun getDoubleStrict(
    raw: Any?,
    def: Double
): Double {
    return when (raw) {
        is Double -> raw
        is Number -> raw.toDouble()
        is String -> raw.toDoubleOrNull()
        else -> null
    } ?: def
}
