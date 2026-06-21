package io.github.elnix90.core.objects

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.elnix90.core.stores.MapSettingsStore
import io.github.elnix90.core.stores.SettingsStore
import io.github.elnix90.core.util.isNotBlankKey

@ConsistentCopyVisibility
public data class StringSetSettingObject internal constructor(
    override val key: String,
    override val default: Set<String>,
    override val title: Int?,
    override val description: Int?,
    override var onChanged: (() -> Unit)?,
    override val backupable: Boolean,
    override val settingsStore: SettingsStore<*, *>
) : SettingObject<Set<String>, Set<String>>() {

    override val preferenceKey: Preferences.Key<Set<String>> = stringSetPreferencesKey(key)
    override fun encode(value: Set<String>): Set<String> = value
    override fun decode(raw: Any?): Set<String> = getStringSetStrict(raw, default)
}

/**
 * Creates a [StringSetSettingObject] with a title and description.
 *
 * This function is used to define a set of strings setting in a type-safe way.
 * The [key] is auto-inferred by the compiler plugin if not provided.
 * If no key is provided, it defaults to the name of the property declared with [io.github.elnix90.annotations.SettingKey].
 * Override the [key] only if you need a custom key.
 *
 * @param title The resource ID for the title of the setting. Can be `null` if no title is needed.
 * @param description The resource ID for the description of the setting. Can be `null` if no description is needed.
 * @param default The default set of strings for the setting.
 * @param key The key for the setting in the preferences store. If empty, the compiler plugin auto-infers it.
 * @param onChanged Optional callback invoked when the setting value changes.
 * @param backupable Whether the setting should be included in backups. Defaults to `true`.
 * @return A [StringSetSettingObject] configured with the provided parameters.
 */
public fun MapSettingsStore.stringSet(
    default: Set<String>,
    title: Int? = null,
    description: Int? = null,
    key: String = "",
    onChanged: (() -> Unit)? = null,
    backupable: Boolean = true
): StringSetSettingObject = StringSetSettingObject(
    key = key.isNotBlankKey,
    title = title,
    description = description,
    default = default,
    onChanged = onChanged,
    backupable = backupable,
    settingsStore = this
)

private fun getStringSetStrict(
    raw: Any?,
    def: Set<String>
): Set<String> {
    return when (raw) {
        is Set<*> -> raw.flattenStrings().toSet()
        is List<*> -> raw.flattenStrings().toSet()
        is String -> {
            // Parse "[a,b,c]" → ["a","b","c"]
            try {
                // Extract content between [ ] and split by comma
                val clean = raw.trim().removeSurrounding("[", "]")
                if (clean.isBlank()) return emptySet()

                clean.split(",")
                    .map { it.trim().trim('"').trim('\'') }
                    .filter { it.isNotBlank() }
                    .toSet()
            } catch (_: Exception) {
                setOf(raw)
            }
        }

        else -> null
    } ?: def
}

private fun Collection<*>.flattenStrings(): List<String> = flatMap { item ->
    when (item) {
        is String -> listOf(item)
        is Collection<*> -> item.flattenStrings()
        else -> emptyList()
    }
}.filter { it.isNotBlank() }
