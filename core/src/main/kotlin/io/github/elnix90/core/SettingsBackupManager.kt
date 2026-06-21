package io.github.elnix90.core

import android.content.Context
import android.net.Uri
import io.github.elnix90.core.stores.JsonArraySettingsStore
import io.github.elnix90.core.stores.JsonObjectSettingsStore
import io.github.elnix90.core.stores.MapSettingsStore
import io.github.elnix90.core.stores.SettingsStore
import io.github.elnix90.core.util.getVersionNameAndCode
import io.github.elnix90.core.util.settingsStoreCase
import io.github.elnix90.logging.BACKUP_TAG
import io.github.elnix90.logging.logD
import io.github.elnix90.logging.logW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream

public object SettingsBackupManager {
    public suspend fun writeJson(ctx: Context, uri: Uri, json: JSONObject) {
        withContext(Dispatchers.IO) {
            ctx.contentResolver.openFileDescriptor(uri, "wt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { fos ->
                    fos.channel.truncate(0) // Ensure file is cleared before writing
                    fos.write(json.toString(2).toByteArray()) // Pretty print with 2 spaces
                    fos.flush()
                }
            } ?: run {
                logW(BACKUP_TAG) { "Failed to open FileDescriptor - URI permission expired!" }
                throw IllegalStateException("Cannot write to URI - permission expired")
            }
        }
    }


    public suspend fun createJsonToExport(
        ctx: Context,
        requestedStores: Set<SettingsStore<*, *>>,
        forceAllKeys: Boolean
    ): JSONObject {
        val json = JSONObject()

        requestedStores.forEach { store ->
            store.exportForBackup(ctx, forceAllKeys)?.let {
                val name = store.name.settingsStoreCase()
                json.put(name, it)
            }
        }

        json.put("app_version", ctx.getVersionNameAndCode())

        return json
    }


    /**
     * Exports only the requested stores.
     * @param requestedStores List of _root_ide_package_.org.elnix.dragonlauncher.settings.bases._root_ide_package_.org.elnix.dragonlauncher.settings.DataStoreName objects
     */
    public suspend fun exportSettings(
        ctx: Context,
        uri: Uri,
        requestedStores: Set<SettingsStore<*, *>>
    ) {
        val json = createJsonToExport(ctx, requestedStores, false)
        writeJson(ctx, uri, json)
    }

    /**
     * Imports app settings from a JSON object directly, without reading a file.
     *
     * This method supports both the current store-based backup system and the legacy
     * "actions" JSON array format. For each requested store, if the JSON contains
     * a corresponding object, it will be passed to the store's `importFromBackup`.
     *
     * @param ctx Context used for accessing DataStores
     * @param json Parsed JSONObject containing backup data
     * @param requestedStores List of _root_ide_package_.org.elnix.dragonlauncher.settings.bases._root_ide_package_.org.elnix.dragonlauncher.settings.DataStoreName objects specifying which stores to restore
     */
    public suspend fun importSettingsFromJson(
        ctx: Context,
        json: JSONObject,
        requestedStores: Set<SettingsStore<*, *>>
    ) {
        logD(BACKUP_TAG) { json.toString() }

        requestedStores.forEach { store ->

            val key = store.name.settingsStoreCase()

            val raw = json.opt(key) ?: return@forEach

            when (store) {
                is JsonArraySettingsStore -> {
                    if (raw is JSONArray) {
                        store.importFromBackup(ctx, raw)
                    }
                }

                is MapSettingsStore -> {
                    if (raw is JSONObject) {
                        store.importFromBackup(ctx, raw)
                    }
                }

                is JsonObjectSettingsStore -> {
                    if (raw is JSONObject) {
                        store.importFromBackup(ctx, raw)
                    }
                }
            }
        }
    }
}
