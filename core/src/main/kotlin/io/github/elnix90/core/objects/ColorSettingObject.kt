package io.github.elnix90.core.objects

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.elnix90.core.stores.MapSettingsStore
import io.github.elnix90.core.stores.SettingsStore
import io.github.elnix90.core.util.toHexWithAlpha

@ConsistentCopyVisibility
public data class ColorSettingObject internal constructor(
    override val key: String,
    override val default: Color,
    override val title: Int?,
    override val description: Int?,
    override var onChanged: (() -> Unit)?,
    override val backupable: Boolean,
    override val settingsStore: SettingsStore<*, *>
) : SettingObject<Color, String>() {

    override val preferenceKey: Preferences.Key<String> = stringPreferencesKey(key)
    override fun encode(value: Color): String = value.toHexWithAlpha(false)
    override fun decode(raw: Any?): Color = getColorStrict(raw, default)
}

/**
 * Creates a [ColorSettingObject] with a title and description.
 *
 * This function is used to define a color setting in a type-safe way.
 * The [key] is auto-inferred by the compiler plugin if not provided.
 * If no key is provided, it defaults to the name of the property declared with [io.github.elnix90.annotations.SettingKey].
 * Override the [key] only if you need a custom key.
 *
 * @param title The resource ID for the title of the setting. Can be `null` if no title is needed.
 * @param description The resource ID for the description of the setting. Can be `null` if no description is needed.
 * @param default The default value for the setting.
 * @param key The key for the setting in the preferences store. If empty, the compiler plugin auto-infers it.
 * @param onChanged Optional callback invoked when the setting value changes.
 * @param backupable Whether the setting should be included in backups. Defaults to `true`.
 * @return A [ColorSettingObject] configured with the provided parameters.
 */
public fun MapSettingsStore.color(
    default: Color,
    title: Int? = null,
    description: Int? = null,
    key: String = "",
    onChanged: (() -> Unit)? = null,
    backupable: Boolean = true
): ColorSettingObject = ColorSettingObject(
    key = key.takeIf { it.isNotEmpty() } ?: error("Key must not be empty"),
    title = title,
    description = description,
    default = default,
    onChanged = onChanged,
    backupable = backupable,
    settingsStore = this
)

private fun getColorStrict(
    raw: Any?,
    def: Color
): Color {
    return when (raw) {
        null -> null
        // Old storage format
        is Int -> Color(raw)
        is Number -> Color(raw.toInt())
        // New readable format, fallbacks to old format
        is String -> {
            raw.toLongOrNull(16)
                ?.let { Color(it.toInt()) }
        }

        else -> null
    } ?: def
}