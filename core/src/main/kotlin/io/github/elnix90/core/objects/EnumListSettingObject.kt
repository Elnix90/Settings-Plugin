package io.github.elnix90.core.objects

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.elnix90.core.stores.MapSettingsStore
import io.github.elnix90.core.stores.SettingsStore
import io.github.elnix90.core.util.isNotBlankKey
import io.github.elnix90.logging.ANGLE_LINE_TAG
import io.github.elnix90.logging.logE

public data class EnumListSettingObject<E : Enum<E>>(
    override val key: String,
    override val default: List<E>,
    override val title: Int?,
    override val description: Int?,
    override var onChanged: (() -> Unit)?,
    override val backupable: Boolean,
    override val settingsStore: SettingsStore<*, *>,
    val enumClass: Class<E>
) : SettingObject<List<E>, String>() {
    override val preferenceKey: Preferences.Key<String> = stringPreferencesKey(key)
    override fun encode(value: List<E>): String = value.joinToString(",") { it.name }
    override fun decode(raw: Any?): List<E> = getEnumListStrict(raw, default, enumClass)
}

/**
 * Creates an [EnumListSettingObject] with a title and description.
 *
 * This function is used to define a list of enum values setting in a type-safe way.
 * The [key] is auto-inferred by the compiler plugin if not provided.
 * If no key is provided, it defaults to the name of the property declared with [io.github.elnix90.annotations.SettingKey].
 * Override the [key] only if you need a custom key.
 *
 * @param title The resource ID for the title of the setting. Can be `null` if no title is needed.
 * @param description The resource ID for the description of the setting. Can be `null` if no description is needed.
 * @param default The default list of enum values for the setting.
 * @param key The key for the setting in the preferences store. If empty, the compiler plugin auto-infers it.
 * @param onChanged Optional callback invoked when the setting value changes.
 * @param backupable Whether the setting should be included in backups. Defaults to `true`.
 * @return An [EnumListSettingObject] configured with the provided parameters.
 */
public inline fun <reified E : Enum<E>> MapSettingsStore.enumList(
    default: List<E>,
    title: Int? = null,
    description: Int? = null,
    key: String = "",
    noinline onChanged: (() -> Unit)? = null,
    backupable: Boolean = true
): EnumListSettingObject<E> = EnumListSettingObject(
    key = key.isNotBlankKey,
    title = title,
    description = description,
    default = default,
    enumClass = E::class.java,
    onChanged = onChanged,
    backupable = backupable,
    settingsStore = this
)


/**
 * Decodes a list of enum from a string, comma separated statements
 */
internal fun <E : Enum<E>> getEnumListStrict(
    raw: Any?,
    def: List<E>,
    enumClass: Class<E>
): List<E> = when (raw) {
    is String ->
        try {
            raw
                .takeIf { it.isNotEmpty() }
                ?.split(",")
                ?.mapNotNull { elem ->
                    enumClass.enumConstants
                        ?.firstOrNull { it.name == elem.trim() }
                }.orEmpty()
        } catch (e: Exception) {
            logE(ANGLE_LINE_TAG, e) { "Failed to decode enumClass $enumClass object, using default value" }
            null
        }

    else -> null
} ?: def

