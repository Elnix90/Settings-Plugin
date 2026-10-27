package io.github.elnix90.annotations

/**
 * Marks a composable function as a **standalone searchable setting entry** whose metadata is
 * provided by ordinary parameters, instead of being described by a `SettingObject`.
 *
 * Use this for actions that are not backed by a `SettingObject` - e.g. a "Set wallpaper" button -
 * but that should still show up in the settings search. The plugin records one
 * `SettingUsage.Entry` entry per call site of the annotated function, carrying the resource ids
 * read from its metadata parameters and the screen it is rendered on. At runtime those entries
 * surface as `SearchResult.Entry`.
 *
 * Entries are also discovered **through wrappers**: a plain function whose body renders an
 * annotated function is indexed at its own call sites, with the metadata resolved against the
 * wrapper's arguments (`RouteItem(route = X)` picks up the inner `SettingsItem(title =
 * route.resId)`). When the inner metadata reads the wrapper's parameters the inner call cannot be
 * recorded on its own, so exactly one entry results; metadata still not statically evaluable
 * after substitution is skipped as described below.
 *
 * The annotation takes **no arguments**. Metadata comes from the function's parameters, because
 * annotation arguments must be compile-time constants while Android library-module `R` fields are
 * non-final (since SDK tools r14) and can never be one. Resource references in *ordinary code*
 * (`title = R.string.set_wallpaper`, or a parameter default) are plain runtime expressions and
 * work in every module type; the plugin snapshots them into the generated index at compile time,
 * exactly like static `@SettingCall` usages. Nothing is looked up reflectively at runtime.
 *
 * The wrapper contract:
 * - `title: Int` - **required**, a `@StringRes` resource id used for display and search matching.
 *   The compiler reports an error when the function declares no such parameter.
 * - `description: Int` (`@StringRes`) - optional; `0`, `null` or an absent parameter means the
 *   entry has no description.
 * - `icon: Int` (`@DrawableRes`) - optional; `0`, `null` or an absent parameter means no icon.
 *
 * At each call site the plugin inlines the argument expression (or the callee's default value)
 * when it is statically evaluable - a constant, an `R` field read, or a parameterless property
 * getter. Expressions that capture locals or branch (`if (x) ...`) cannot be evaluated once at
 * index initialization and are skipped: the entry is dropped rather than indexed with wrong
 * metadata, and a `title` resolving to `0` is dropped as it could never be found by search.
 * This is silent by design - a valid annotation never fails the build.
 *
 * Do not combine it with `@SettingCall` on the same function: `@SettingCall` describes
 * `SettingObject`-based wrappers, while this annotation describes manually described entries.
 *
 * Like `@SettingCall`, this annotation alone does not inject any UI. The `:ui` module ships
 * `SettingAction`, a ready-made `@SettingEntry` wrapper, so app code normally annotates nothing;
 * declare your own wrapper only when you need custom rendering.
 *
 * The compiler warns when the annotated function is not `@Composable`, because non-composable
 * call sites cannot be reliably recorded.
 *
 * Because the plugin records entries during IR generation, the annotation must sit on a
 * function **declaration** - IR expressions carry no annotations, so annotating a call site
 * is invisible to the compiler plugin. Two shapes work:
 *
 * a library-style wrapper annotated once and called everywhere:
 *
 * ```
 * @Composable
 * @SettingEntry
 * fun DragonGroupScope.SetWallpaperAction(
 *     @StringRes title: Int,
 *     @StringRes description: Int? = null,
 *     @DrawableRes icon: Int? = null,
 *     onClick: () -> Unit
 * ) { /* row UI */ }
 *
 * // call site - arguments are ordinary code, any module type works:
 * SetWallpaperAction(title = R.string.set_wallpaper, icon = R.drawable.wallpaper) { /* ... */ }
 * ```
 *
 * or a small local function right where the entry is rendered, driven by its defaults:
 *
 * ```
 * @Composable
 * @SettingScreen(NavigationRoute.Wallpaper::class)
 * fun WallpaperTab() {
 *     DragonSettingsGroup(R.string.custom_wallpaper) {
 *         @Composable
 *         @SettingEntry
 *         fun SetWallpaperButton(
 *             @StringRes title: Int = R.string.set_wallpaper,
 *             @DrawableRes icon: Int = R.drawable.wallpaper
 *         ) {
 *             DragonButton(stringResource(title)) { /* ... */ }
 *         }
 *
 *         SetWallpaperButton()
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class SettingEntry
