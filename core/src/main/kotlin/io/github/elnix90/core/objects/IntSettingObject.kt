package io.github.elnix90.core.objects

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.elnix90.core.stores.MapSettingsStore
import io.github.elnix90.core.stores.SettingsStore
import io.github.elnix90.core.util.isNotBlankKey

@ConsistentCopyVisibility
public data class IntSettingObject internal constructor(
    override val key: String,
    override val default: Int,
    override val title: Int?,
    override val description: Int?,
    override var onChanged: (() -> Unit)?,
    override val backupable: Boolean,
    override val settingsStore: SettingsStore<*, *>,
    val allowedRange: IntRange
) : SettingObject<Int, Int>() {

    override val preferenceKey: Preferences.Key<Int> = intPreferencesKey(key)
    override fun encode(value: Int): Int = value
    override fun decode(raw: Any?): Int = getIntStrict(raw, default).coerceIn(allowedRange)
}


/**
 * Creates an [IntSettingObject] with a title and description.
 *
 * This function is used to define an integer setting in a type-safe way.
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
 * @return An [IntSettingObject] configured with the provided parameters.
 */
public fun MapSettingsStore.int(
    default: Int,
    allowedRange: IntRange,
    title: Int? = null,
    description: Int? = null,
    key: String = "",
    onChanged: (() -> Unit)? = null,
    backupable: Boolean = true
): IntSettingObject = IntSettingObject(
    key = key.isNotBlankKey,
    title = title,
    description = description,
    default = default,
    allowedRange = allowedRange,
    onChanged = onChanged,
    backupable = backupable,
    settingsStore = this
)

internal fun getIntStrict(
    raw: Any?,
    def: Int
): Int {
    return when (raw) {
        is Int -> raw
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull()
        else -> null
    } ?: def
}
