package io.github.elnix90.runtime.search

import android.content.Context
import io.github.elnix90.core.objects.SettingObject

/**
 * Runtime index over every settings usage discovered by the compiler plugin.
 *
 * Feed it the list generated for `@AllSettingUsages`:
 *
 * ```kotlin
 * @AllSettingUsages
 * val usages: List<SettingUsage> = emptyList()
 *
 * val index = SettingsSearchIndex(usages)
 * ```
 *
 * and search it from anywhere:
 *
 * ```kotlin
 * val results = index.search(context, query)
 * ```
 *
 * Titles and descriptions are stored as resource ids, so Android resolves them
 * per-locale at search time: localization is free and the plugin never touches
 * strings.
 *
 * Settings without a title (internal or backup-only ones) are excluded by
 * default; pass `includeSettingsWithoutTitle = true` to index them by their key.
 *
 * Manually described entries (`@SettingCall`-free actions recorded as
 * [SettingUsage.Entry], e.g. a "Set wallpaper" button) are searched by their
 * localized title and description; entries whose title resolves to no resource
 * are skipped rather than failing the search.
 *
 * Scoring is delegated to an injectable [SettingScorer]; the default
 * [DefaultSettingScorer] performs normalized fuzzy matching over title,
 * description and key.
 *
 * An empty or blank query returns every indexed entry (score 0) so a search
 * screen can offer the full list before the user types.
 *
 * Multi-module note: IR only sees the current compilation module, so each
 * module generates its own fragment; merge the lists at runtime
 * (`SettingsSearchIndex(usages + libUsages)`) to search across modules.
 *
 * @property usages Every settings usage discovered in the module.
 * @property scorer Scoring strategy, replaceable by apps.
 * @property minimumScore Results scoring below this value are dropped.
 * @property includeSettingsWithoutTitle Whether settings without a title are
 * searchable (by their key) instead of being excluded.
 */
public class SettingsSearchIndex(
	private val usages: List<SettingUsage>,
	private val scorer: SettingScorer = DefaultSettingScorer,
	private val minimumScore: Float = DEFAULT_MINIMUM_SCORE,
	private val includeSettingsWithoutTitle: Boolean = false
) {
	public companion object {
		/** Default value of `minimumScore`: a title substring match (2.1) passes, a key substring match (0.7) does not. */
		public const val DEFAULT_MINIMUM_SCORE: Float = 0.5f
	}

	/**
	 * Searches the indexed entries.
	 *
	 * Results are sorted by descending [SearchResult.score]; ties keep the
	 * discovery order of the generated list.
	 *
	 * @param ctx Android context used to resolve localized titles and descriptions.
	 * @param query Raw user input.
	 */
	public fun search(
		ctx: Context,
		query: String
	): List<SearchResult> {
		val trimmedQuery = query.trim()

		val keys = mutableSetOf<String>()

		val results =
			usages.mapIndexedNotNull { ordinal, usage ->
				when (usage) {
					is SettingUsage.Object -> {
						val setting = usage.settingProvider()

						val key = "${usage.screenFqName}:${setting.key}:$ordinal"
						if (!keys.add(key)) {
							return@mapIndexedNotNull null
						}

						val title = resolveTitle(ctx, setting) ?: return@mapIndexedNotNull null
						val description = setting.description?.let { ctx.resources.getString(it) }
						val score = score(trimmedQuery, title, description, setting.key)

						SearchResult.Object(
							setting = setting,
							title = title,
							description = description,
							icon = setting.icon,
							screenFqName = usage.screenFqName,
							screenRoute = usage.screenRoute,
							key = key,
							score = score
						)
					}

					is SettingUsage.Entry -> {
						if (usage.title == 0) return@mapIndexedNotNull null

						val key = "${usage.screenFqName}:${usage.title}:$ordinal"
						if (!keys.add(key)) {
							return@mapIndexedNotNull null
						}

						val title = ctx.resources.getString(usage.title)
						val description = usage.description.takeIf { it != 0 }?.let { ctx.resources.getString(it) }
						val icon = usage.icon.takeIf { it != 0 }
						val score = score(trimmedQuery, title, description, "")

						SearchResult.Entry(
							title = title,
							description = description,
							icon = icon,
							screenFqName = usage.screenFqName,
							screenRoute = usage.screenRoute,
							key = key,
							score = score
						)
					}
				}
			}

		if (trimmedQuery.isEmpty()) return results

		return results
			.filter { it.score >= minimumScore }
			.sortedWith(compareByDescending { it.score })
	}

	/**
	 * Scores [title]/[description] against [query], or returns `0f` for an
	 * empty query so that the full list is offered before the user types.
	 */
	private fun score(
		query: String,
		title: String,
		description: String?,
		key: String
	): Float =
		if (query.isEmpty()) {
			0f
		} else {
			scorer.score(query, title, description, key)
		}

	/**
	 * Resolves the localized title of [setting], falling back to its key when
	 * untitled settings are included in the index.
	 */
	private fun resolveTitle(
		ctx: Context,
		setting: SettingObject<*, *>
	): String? {
		val titleResource = setting.title
		if (titleResource != null) {
			return ctx.resources.getString(titleResource)
		}

		return setting.key.takeIf { includeSettingsWithoutTitle }
	}
}
