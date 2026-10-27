package io.github.elnix90.annotations

import kotlin.reflect.KClass

/**
 * Declares the navigation route of the screen a settings screen composable represents.
 *
 * Place it on the composable function that hosts settings - e.g. the `AppearanceTab` screen -
 * and pass the app's navigation route object, which must be an `object` (typically a
 * `data object` implementing the app's `NavKey`):
 *
 * ```kotlin
 * @Composable
 * @SettingScreen(NavigationRoute.Appearance::class)
 * fun AppearanceTab(navigator: Navigator) { /* ... */ }
 * ```
 *
 * Every setting rendered inside the annotated function - including through nested helper
 * composables - records the route as its screen route. Clicking such a setting in the search
 * results becomes `navigator.navigate(result.screenRoute as NavigationRoute)`: no manual
 * mapping between a screen name string and a route is required.
 *
 * The parameter is typed `KClass<*>` because annotations cannot carry object instances; the
 * plugin emits a reference to the route object instance at each recorded call site. The
 * compiler reports an error when the given class is not an `object`, since only objects can be
 * instantiated this way. When the class is a plain class, the settings of that screen keep
 * working and only the typed route is omitted, falling back to the screen's fully qualified
 * name (`screenFqName`).
 *
 * When no annotated screen is found in the enclosing functions of a call site, the plugin
 * falls back to the innermost enclosing named function as the screen and records no route -
 * the same behavior as before this annotation existed.
 *
 * The compiler warns when the annotated function is not `@Composable`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class SettingScreen(
	val route: KClass<*>
)
