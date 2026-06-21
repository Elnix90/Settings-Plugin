package io.github.elnix90.core.stores

import android.content.Context
import io.github.elnix90.core.util.putIfNonDefault
import io.github.elnix90.core.util.putIfNotNull
import org.json.JSONObject

/**
 * Settings store backed by multiple independent DataStore keys.
 *
 * `MapSettingsStore` is the standard implementation used for most settings groups,
 * where each setting is stored under its own DataStore preference key and exposed
 * collectively as a `Map<String, Any?>`.
 *
 * Implementation:
 *  - Each setting in the [MapSettingsStore] has to be **annotated** by [io.github.elnix90.annotations.SettingKey] to be able to be detected by the compiler plugin at compile time
 *
 * Characteristics:
 * - The map key corresponds to `BaseSettingObject.key`.
 * - Values are read and written individually, not as a single blob.
 * - Import/export operates on raw values and relies on each `BaseSettingObject` to decode and validate its own type.
 * - Exports data in a [JSONObject] via [getAll]
 *
 * This design enables:
 * - fine-grained persistence (only changed keys are written)
 * - backward-compatible imports (unknown keys are ignored)
 * - safe type coercion during restore via `BaseSettingObject.decode`
 */
public abstract class MapSettingsStore(
    override val backupable: Boolean = true
) : SettingsStore<Map<String, Any?>, JSONObject>(backupable) {

    /**
     * Reads all settings from the store and returns them as a map.
     * When [forceAllKeys] **isn't** enabled, skips the value if the decoded is null
     */
    final override suspend fun getAll(ctx: Context, forceAllKeys: Boolean): Map<String, Any> =
        buildMap {
            ALL.forEach { setting ->
                if (forceAllKeys) {
                    putIfNotNull(ctx, setting)
                } else if (setting.backupable) {
                    putIfNonDefault(ctx, setting)
                }
            }
        }

    /**
     * Writes all provided values to DataStore.
     *
     * Each value is decoded individually using the corresponding
     * `BaseSettingObject.decode` implementation before being persisted.
     *
     * Unknown or missing keys are ignored.
     */
    final override suspend fun setAll(ctx: Context, value: Map<String, Any?>) {
        ALL.forEach { setting ->
            setting.setAny(ctx, setting.decode(value[setting.key]))
        }
    }

    /**
     * Exports all settings into a single [JSONObject] for backup purposes.
     */
    final override suspend fun exportForBackup(ctx: Context, forceAllKeys: Boolean): JSONObject? {

        val map = getAll(ctx, forceAllKeys)
        return if (map.isNotEmpty()) {
            JSONObject(map)
        } else null
    }

    /**
     * Restores settings from a [JSONObject] backup.
     *
     * Only keys present in [ALL] are applied; unknown keys are safely ignored.
     * Each value is decoded and validated by its corresponding `BaseSettingObject`.
     */
    final override suspend fun importFromBackup(ctx: Context, json: JSONObject?) {
        json?.keys()?.forEach { key ->
            ALL.find { it.key == key }?.let { setting ->
                val raw = json.opt(key)
                val typedValue = setting.decode(raw)

                setting.setAny(ctx, typedValue)
            }
        }
    }
}
