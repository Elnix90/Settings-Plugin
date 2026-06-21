package io.github.elnix90.core.objects

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.elnix90.core.stores.MapSettingsStore
import io.github.elnix90.core.stores.SettingsStore
import io.github.elnix90.core.util.isNotBlankKey

@ConsistentCopyVisibility
public data class StringListSettingObject internal constructor(
    override val key: String,
    override val default: List<String>,
    override val title: Int?,
    override val description: Int?,
    override var onChanged: (() -> Unit)?,
    override val backupable: Boolean,
    override val settingsStore: SettingsStore<*, *>
) : SettingObject<List<String>, String>() {

    override val preferenceKey: Preferences.Key<String> = stringPreferencesKey(key)
    override fun encode(value: List<String>): String = value.joinToString(",")
    override fun decode(raw: Any?): List<String> = getStringListStrict(raw, default)

}

/**
 * Creates a [StringListSettingObject] with a title and description.
 *
 * This function is used to define a list of strings setting in a type-safe way.
 * The [key] is auto-inferred by the compiler plugin if not provided.
 * If no key is provided, it defaults to the name of the property declared with [io.github.elnix90.annotations.SettingKey].
 * Override the [key] only if you need a custom key.
 *
 * @param title The resource ID for the title of the setting. Can be `null` if no title is needed.
 * @param description The resource ID for the description of the setting. Can be `null` if no description is needed.
 * @param default The default list of strings for the setting.
 * @param key The key for the setting in the preferences store. If empty, the compiler plugin auto-infers it.
 * @param onChanged Optional callback invoked when the setting value changes.
 * @param backupable Whether the setting should be included in backups. Defaults to `true`.
 * @return A [StringListSettingObject] configured with the provided parameters.
 */
public fun MapSettingsStore.stringList(
    default: List<String>,
    title: Int? = null,
    description: Int? = null,
    key: String = "",
    onChanged: (() -> Unit)? = null,
    backupable: Boolean = true
): StringListSettingObject = StringListSettingObject(
    key = key.isNotBlankKey,
    title = title,
    description = description,
    default = default,
    onChanged = onChanged,
    backupable = backupable,
    settingsStore = this
)

private fun getStringListStrict(
    raw: Any?,
    def: List<String>
): List<String> {
    return try {
        with(raw.toString()) {
            val clean = trim()
            if (clean.isBlank()) return emptyList()
            clean.split(",")
                .map { it.trim().trim('"').trim('\'') }
                .filter { it.isNotBlank() }
        }
    } catch (_: Exception) {
        def
    }
}