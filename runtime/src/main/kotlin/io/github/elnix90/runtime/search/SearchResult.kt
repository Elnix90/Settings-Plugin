package io.github.elnix90.runtime.search

import io.github.elnix90.core.objects.SettingObject

/**
 * A single settings search result produced by [SettingsSearchIndex.search].
 *
 * Two kinds exist, mirroring [SettingUsage]:
 *
 * - [Object], a matched [SettingObject]-based setting;
 * - [Entry], a manually described entry (a `@SettingEntry` action).
 *
 * Both kinds share the same searchable surface ([title], [description], [icon])
 * and the same navigation data ([screenFqName], [screenRoute]), so a search UI
 * can render and route them uniformly without `when`-ing on the subtype. Use
 * [key] as the LazyColumn item key - it is unique per entry and stable across
 * recompositions and app runs.
 *
 * @property title Localized title (resource resolved at search time).
 * @property description Localized description, when the entry has one.
 * @property icon Resource id of the entry icon, when it has one.
 * @property screenFqName FqName of the screen the entry is rendered on;
 * a fallback navigation target.
 * @property screenRoute Navigation route object recorded for the screen by
 * `@SettingScreen`, or `null`. Cast to the app's route type to navigate.
 * @property key Unique, stable identity of this entry for list keys.
 * @property score Relevance of this result for the query, higher is better.
 */
public sealed class SearchResult {
	public abstract val title: String

	public abstract val description: String?

	public abstract val icon: Int?

	public abstract val screenFqName: String

	public abstract val screenRoute: Any?

	public abstract val key: String

	public abstract val score: Float

	/**
	 * A search result backed by a [SettingObject].
	 *
	 * @property setting The matched setting.
	 */
	public data class Object(
		public val setting: SettingObject<*, *>,
		override val title: String,
		override val description: String?,
		override val icon: Int?,
		override val screenFqName: String,
		override val screenRoute: Any?,
		override val key: String,
		override val score: Float
	) : SearchResult()

	/**
	 * A search result for a manually described entry: an action that is not
	 * backed by a [SettingObject] (e.g. a special button), indexed from
	 * its `@SettingEntry` call site.
	 */
	public data class Entry(
		override val title: String,
		override val description: String?,
		override val icon: Int?,
		override val screenFqName: String,
		override val screenRoute: Any?,
		override val key: String,
		override val score: Float
	) : SearchResult()
}
