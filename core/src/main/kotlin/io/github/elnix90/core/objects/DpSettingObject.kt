package io.github.elnix90.core.objects

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.elnix90.core.stores.MapSettingsStore
import io.github.elnix90.core.stores.SettingsStore
import io.github.elnix90.core.util.isNotBlankKey

@ConsistentCopyVisibility
public data class DpSettingObject internal constructor(
    override val key: String,
    override val default: Dp,
    override val title: Int?,
    override val description: Int?,
    override var onChanged: (() -> Unit)?,
    override val backupable: Boolean,
    override val settingsStore: SettingsStore<*, *>,
    val allowedRange: ClosedRange<Dp>
) : SettingObject<Dp, Int>() {

    override val preferenceKey: Preferences.Key<Int> = intPreferencesKey(preferenceKeyName)
    override fun encode(value: Dp): Int = value.value.toInt()
    override fun decode(raw: Any?): Dp = getDpStrict(raw, default).coerceIn(allowedRange)
}

/**
 * Creates a [DpSettingObject] with a title and description.
 *
 * This function is used to define a density-independent pixel (Dp) setting in a type-safe way.
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
 * @return A [DpSettingObject] configured with the provided parameters.
 */
public fun MapSettingsStore.dp(
    default: Dp,
    title: Int? = null,
    description: Int? = null,
    allowedRange: ClosedRange<Dp>,
    key: String = "",
    onChanged: (() -> Unit)? = null,
    backupable: Boolean = true
): DpSettingObject = DpSettingObject(
    key = key.isNotBlankKey,
    title = title,
    description = description,
    default = default,
    allowedRange = allowedRange,
    onChanged = onChanged,
    backupable = backupable,
    settingsStore = this
)

private fun getDpStrict(
    raw: Any?,
    def: Dp
): Dp {
    return when (raw) {
        is Int -> raw.dp
        is Number -> raw.toInt().dp
        is String -> raw.toIntOrNull()?.dp
        else -> null
    } ?: def
}