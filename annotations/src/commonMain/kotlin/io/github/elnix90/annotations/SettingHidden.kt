package io.github.elnix90.annotations

/**
 * Excludes settings from the compile-time search index generated for
 * [AllSettingUsages].
 *
 * Use this for settings that should not appear in the settings search: internal
 * or debug toggles, values rendered on screens that are not reachable through
 * normal navigation, or anything the user should not find by typing. Annotated
 * settings are simply left out of the generated list; nothing is reported and
 * the compilation never fails.
 *
 * The annotation can be applied in two places, and both are honoured:
 *
 * - **A store property** - hides that one setting everywhere it is rendered:
 *
 *   ```kotlin
 *   @SettingsStore
 *   object DebugSettingsStore : MapSettingsStore() {
 *       @SettingKey
 *       @SettingHidden
 *       val verboseLogging = boolean()
 *   }
 *   ```
 *
 * - **Any function** - hides every setting rendered inside that function's
 *   body, including settings nested in lambdas, helper composables, dialogs and
 *   conditional branches. This is the pragmatic option for a screen or dialog
 *   whose settings should stay out of search entirely, without annotating each
 *   store property:
 *
 *   ```kotlin
 *   @Composable
 *   @SettingHidden
 *   fun PatternPrompt(onResult: (Boolean) -> Unit) {
 *       DragonSettingsGroup {
 *           Setting(BehaviorSettingsStore.patternSize) // not indexed
 *         Setting(BehaviorSettingsStore.patternSensitivity) // not indexed
 *       }
 *   }
 *   ```
 *
 * A setting is hidden when *either* its store property *or* any enclosing
 * function is annotated, so combining the two is redundant but harmless.
 *
 * Hiding takes **priority** over `@SettingEntry` and `@SettingCall`: the hidden
 * check runs before entry/call discovery and covers the callee itself, any
 * enclosing function, and an enclosing `@SettingScreen` - recovered from
 * source positions when the Compose transform hoists the screen body off the
 * IR ancestor chain.
 *
 * Like the other settings annotations, this one alone does not inject any UI:
 * it only tells the compiler plugin to skip the matching usages when building
 * the search index.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class SettingHidden
