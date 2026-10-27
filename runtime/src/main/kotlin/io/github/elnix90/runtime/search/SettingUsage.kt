package io.github.elnix90.runtime.search

import io.github.elnix90.core.objects.SettingObject

/**
 * A single settings search entry discovered by the compiler plugin.
 *
 * Instances are generated into the property annotated with `@AllSettingUsages`;
 * app developers never construct them by hand. Two kinds exist:
 *
 * - [Object] - a call site of a `@SettingCall` wrapper, backed by a
 *   [SettingObject];
 * - [Entry] - a call site of a `@SettingEntry` function, described manually
 *   through string and icon resource ids (e.g. a "Set wallpaper" action).
 *
 * Both carry the screen they are rendered on: [screenFqName] always, and
 * [screenRoute] - a navigation route object instance - when the enclosing
 * screen is annotated with `@SettingScreen`.
 *
 * @property screenFqName Fully qualified name of the screen function the entry
 * is rendered on, usable as a fallback navigation target.
 * @property screenRoute Navigation route object recorded for the screen when
 * it is annotated with `@SettingScreen`, or `null` otherwise. Cast to the
 * app's route type to navigate: `navigator.navigate(route as NavigationRoute)`.
 */
public sealed class SettingUsage {
	public abstract val screenFqName: String

	public abstract val screenRoute: Any?

	/**
	 * A usage produced at a `@SettingCall` call site.
	 *
	 * The setting itself is held through a provider so that dynamic usages
	 * (e.g. `Setting(if (x) a else b)`) can be resolved lazily at runtime,
	 * while simple property references (`Setting(MyStore.enabled)`) are wrapped
	 * into a constant provider by the plugin.
	 *
	 * @property settingProvider Supplies the setting rendered at this usage site.
	 * @property storeFqName Fully qualified name of the store owning the setting,
	 * when statically known (empty for dynamic usages).
	 */
	public class Object(
		public val settingProvider: () -> SettingObject<*, *>,
		override val screenFqName: String,
		public val storeFqName: String,
		override val screenRoute: Any?
	) : SettingUsage() {
		public constructor(
			setting: SettingObject<*, *>,
			screenFqName: String,
			storeFqName: String,
			screenRoute: Any?
		) : this(
			settingProvider = { setting },
			screenFqName = screenFqName,
			storeFqName = storeFqName,
			screenRoute = screenRoute
		)

		/**
		 * The setting rendered at this usage site.
		 *
		 * Convenience accessor equivalent to `settingProvider()`; prefer the
		 * provider directly when the setting may be recomputed between searches.
		 */
		public val setting: SettingObject<*, *>
			get() = settingProvider()
	}

	/**
	 * A usage produced at a `@SettingEntry` call site: an action or other entry
	 * that is not backed by a [SettingObject] but should still be searchable.
	 *
	 * The ids are snapshotted by the compiler plugin from the wrapper's
	 * metadata parameters (`title: Int`, optional `description`/`icon`) at each
	 * call site, so they are plain runtime values - usable from any module type,
	 * unlike `R` references in annotation arguments.
	 *
	 * @property title String resource id of the entry title. Never `0` -
	 * the plugin skips such entries.
	 * @property description String resource id of the entry description,
	 * or `0` when there is none.
	 * @property icon Drawable resource id of the entry icon, or `0` when
	 * there is none.
	 */
	public class Entry(
		public val title: Int,
		public val description: Int,
		public val icon: Int,
		override val screenFqName: String,
		override val screenRoute: Any?
	) : SettingUsage()
}
