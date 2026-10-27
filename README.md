# DragonLauncher Settings Compiler Plugin

A Android Kotlin Compiler Plugin for those who don't want boilerplate when dealing with user settings

This library is built on top of the [Android Datastore library](https://developer.android.com/topic/libraries/architecture/datastore)  it provides easy front-end APIs to access, mutate and save settings.
## Overview

This plugin works by using what I called **`SettingsStores`**. You define one or more setting stores in you app, and then fille them with all the settings you want.


```kotlin
@SettingsStore
object MySettingsStore : MapSettingsStore() {

    @SettingKey
    val settingA = boolean(
        title = R.string.setting_a_title,
        description = R.string.setting_a_description,
        default = true
    )

    @SettingKey
    val settingB = string("")

    @SettingKey
    val settingC = enum(SomeEnum.A)

    @SettingKey
    val settingD = int(
        default = 3,
        allowedRange = 0..10
    )
}
```


You can then get those values in compose using the state functions present in the `runtime` module:

```kotlin
val settingA by MySettingsStore.settingA.asState()


var settingB by MySettingsStore.settingB.asMutableState()

// settingA is state reactive
AnimatedVisibility(settingA) {
    SomeComposable()
}

// SettingB is a mutable state
if (someCondition) {
    settingB = false
}


// You can also mutate them by using a Coroutine scope and context
val ctx = LocalContext.current
val scope = rememberCoroutineScope()

scope.launch {
    MySettingsStore.settingA.set(ctx, false)
}

```


The plugin provides 4 main features:

### 1. Automatic setting keys

Each setting has a key parameter that is a String, and that is auto-inferred by the compiler as the value use to describe the setting.

for example you write:
```kotlin
@SettingKey
val hideBetaVersionWarning = boolean(true)
```

... and the compiler automatically rewrites the initializer to:

```kotlin
boolean(
    key = "hideBetaVersionWarning"
)
```

This keeps keys synchronized with property names and makes refactoring safe, but you can always override them and the compiler will use yours

---

### 2. Automatic settings store collections

Instead of manually maintaining a list of every setting in a store:
```kotlin
override val ALL = listOf(
    settingA,
    settingB,
    settingC
)
```

The compiler automatically generates this, and provides a safe list of all the settings

---

## 3. Automatic list of all Stores:

You can create a value and **annotate** it with `@AllStores`
```kotlin
@AllStores
val allStores: Set<SettingsStore<*,*>> = emptySet()
```

And the compiler fills it at with all you stores annotated with `@SettingsStore`

---

### 4. Settings search (compile-time index + click-to-navigate)

The plugin scans your whole module at compile time and builds a typed index of every place a setting is rendered, so you can power a settings search screen with **no hand-maintained registry**. You also get click-to-navigate for free:each indexed entry remembers the screen it lives on (and, when that screen declares a navigation route, the route object itself).

The runtime types live in the `runtime` module (`io.github.elnix90.runtime.search`).

#### 4.1 Declare the index holder

Create **one** property in your app module and annotate it with `@AllSettingUsages`:

```kotlin
@AllSettingUsages
val allSettingsUsage: List<SettingUsage> = emptyList()
```

The compiler replaces the `emptyList()` with a generated `listOf(...)` of every `@SettingCall` / `@SettingEntry` usage discovered in the module.

#### 4.2 What gets indexed automatically: `@SettingCall`

Every call site of a function annotated with `@SettingCall` is recorded. The `:ui` library already annotates its wrappers (`Setting(...)`, `SettingsSwitchRow`, ...), so as an app developer **you annotate nothing** for `SettingObject`-backed settings:

```kotlin
DragonSettingsGroup(R.string.behaviour) {
    Setting(BehaviorSettingsStore.patternSize)
    Setting(BehaviorSettingsStore.patternSensitivity)
}
```

Both a plain property reference (`Setting(MyStore.enabled)`) and a dynamic expression (`Setting(if (x) a else b)`) are indexed; dynamic expressions that capture local state are skipped (they can't be re-evaluated from the module-level index) - this never fails the build.

#### 4.3 Make a screen navigable: `@SettingScreen`

Annotate a screen composable with `@SettingScreen`, passing your navigation route object (it must be an `object`/`data object` implementing your `NavKey`):

```kotlin
@Composable
@SettingScreen(NavigationRoute.AngleLineEdit::class)
fun AngleLineTab() {
    DragonSettingsGroup {
        Setting(AngleLineSettingsStore.showLineObjectPreview)
    }
}
```

Now every setting rendered inside `AngleLineTab` - including ones nested in `AnimatedContent`, conditionals, or helper composables - records both the screen's fully qualified name and the `NavigationRoute.AngleLineEdit` object.

#### 4.4 Index an action with no `SettingObject`: `@SettingEntry`

For things that aren't settings but should still be searchable (a "Set wallpaper" button), annotate a composable **function declaration** with `@SettingEntry`. The annotation is argumentless: metadata comes from the function's parameters - a **required non-nullable `title: Int`** plus optional `description` and `icon` (`Int` or `Int?`; `0`, `null` or an absent parameter means none). Call sites pass ordinary arguments, which is what makes the pattern work in library modules too: annotation arguments must be compile-time constants, but library-module `R` fields are not. A function without a usable `title: Int` parameter is a compile error.

**Wrapper discovery**: you don't annotate every level. A plain function whose body renders a `@SettingEntry` function is discovered through its own call sites - `RouteItem(route = NavigationRoute.Colors)` is indexed even though only `SettingsItem` is annotated, because the plugin resolves the inner metadata (`title = route.resId`) against the wrapper's arguments (expanding lowered `tmp` variables along the way). When the inner metadata depends on wrapper parameters the inner call cannot be recorded on its own, so exactly one entry results; anything still not statically evaluable after substitution is skipped as above.

You can declare any setting to be a searchable one by Wrapping its `@Composable` code with `SettingAction`

```kotlin
@Composable
@SettingScreen(NavigationRoute.Wallpaper::class)
fun WallpaperTab() {
	SettingAction(
		title = R.string.set_wallpaper,
		description = R.string.set_wallpaper,
		icon = R.drawable.wallpaper
	) {
		DragonButton(
			onClick = { /* sets wallpaper */}
		) {
			Icon(
				painter = painterResource(icon!!),
				contentDescription = null
			)
			Text(
				text = stringResource(title),
				textAlign = TextAlign.Center
			)
		}
	}
}
```

Indexing something bespoke follows the same contract - declare the metadata as parameters; call sites pass them, or omit them and the wrapper's `= R...` defaults are indexed:

```kotlin
DragonSettingsGroup(R.string.custom_wallpaper) {
    @Composable
    @SettingEntry
    fun SetWallpaperButton(
        @StringRes title: Int = R.string.set_wallpaper,
        @DrawableRes icon: Int? = R.drawable.wallpaper
    ) {
        DragonButton(stringResource(title)) { /* ... */ }
    }

    SetWallpaperButton()
}
```

The annotation must sit on the function **declaration** - annotating a call expression is invisible to the compiler; the local function above keeps the entry right where it is rendered.

At index generation the plugin **bakes** these values statically: constants, `R` field reads and parameterless property getters are copied into the module-level list, so search costs one field read at startup and nothing per query beyond the locale lookup. Metadata that can't be evaluated that way - capturing a local, a branch, a call with arguments - and a `title = 0` are skipped silently; this never fails the build.

#### 4.5 Keep settings out of search: `@SettingHidden`

Exclude settings from the index entirely. It works on **either** a store property (hides that setting everywhere) or **any function** (hides every setting rendered inside it - ideal for a dialog whose settings shouldn't be searchable):

```kotlin
@SettingsStore
object DebugSettingsStore : MapSettingsStore() {
    // Hidden everywhere it is rendered.
    @SettingKey
    @SettingHidden
    val verboseLogging = boolean()
}

// Every setting inside this dialog is excluded from search.
@Composable
@SettingHidden
fun PatternPrompt(onResult: (Boolean) -> Unit) {
    DragonSettingsGroup {
        Setting(BehaviorSettingsStore.patternSize)        // not indexed
        Setting(BehaviorSettingsStore.patternSensitivity) // not indexed
    }
}
```

Hiding takes **priority** over `@SettingEntry` and `@SettingCall`: the hidden check runs before entry/call discovery, so an annotated entry inside a hidden scope is never indexed. It matches on the callee itself, on any enclosing function, and on an enclosing `@SettingScreen` - recovered from source positions when the Compose transform hoists the screen body off the IR ancestor chain.

#### 4.6 Build the index and search at runtime

```kotlin
val index = SettingsSearchIndex(allSettingsUsage)

val results: List<SearchResult> = index.search(context, query)
```

`SearchResult` is a sealed type with two kinds that share the same searchable surface (`title`, `description`, `icon`) and the same navigation data (`screenFqName`, `screenRoute`), so a search UI renders them uniformly:

- `SearchResult.Object` - a matched `SettingObject`-backed setting (`result.setting` is the `SettingObject`);
- `SearchResult.Entry` - a `@SettingEntry` action (carries resolved `title`/`description`/`icon`).

Both expose `key` - unique per entry and stable across recompositions, use it as your `LazyColumn` item key.

#### 4.7 Navigate on click

`screenRoute` is the route object instance recorded by `@SettingScreen` (or `null` when the screen declared none). Cast it to your sealed route and navigate:

```kotlin
items(results, key = { it.key }) { result ->
    SearchResultRow(
        result = result,
        onClick = {
            val screen = result.screenRoute as? NavigationRoute ?: return@SearchResultRow
            navigator.navigate(screen)
        }
    )
}
```

#### Screenshots


<p align="center">
    <img src="https://raw.githubusercontent.com/Elnix90/Settings-Plugin/main/assets/1.jpg" alt="Demo 1" width="40%">
    <img src="https://raw.githubusercontent.com/Elnix90/Settings-Plugin/main/assets/2.jpg" alt="Demo 2" width="40%">
</p>

## Download

![Maven Central Version](https://img.shields.io/maven-central/v/io.github.elnix90.settings/compiler-plugin?style=for-the-badge)

### Version Catalog
Configure the dependency by adding it to your `libs.versions.toml` file as follows:

```toml
[versions]
#...
settings = "1.1.0"

[libraries]
#...
settings-annotations = { group = "io.github.elnix90.settings", name = "annotations", version.ref = "settings" }
settings-core = { group = "io.github.elnix90.settings", name = "core", version.ref = "settings" }
settings-runtime = { group = "io.github.elnix90.settings", name = "runtime", version.ref = "settings" }

[plugins]
#...
settings = { id = "io.github.elnix90.settings", version.ref = "settings" }
```

### Gradle
Add the dependency below to your **module**'s `build.gradle.kts` file:

```gradle
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // ...
    alias(libs.plugins.settings) apply false
}
```

```kotlin

// Module build file
plugins {
    alias(libs.plugins.android.library)
    // ...
    alias(libs.plugins.settings)
}

dependencies {
    // ...
    implementation(libs.settings.core)
    implementation(libs.settings.runtime)
    implementation(libs.settings.annotations)
}
```


Then, in each module that has to use the settings as state:

```kotlin
dependencies {
    // ...
    implementation(libs.settings.runtime)
}
```

## How to clone and add credentials

1. Generate the key
```bash
gpg --full-generate-key
```

- Select **RSA** and then 4096
- fill in the next prompts with what you need

2. Export the key to the `creds/` folder
```bash
gpg --armor --export-secret-key YOUR_KEY_ID > creds/private.asc
```
This will create or overwrite the file `creds/private.asc` in the repo (gitignored)

3. Export password to `creds/private.txt`
```bash
echo YOUR_PASSWORD > creds/passwd.txt
```

4. Generate Maven tokens
- Go to https://central.sonatype.com/usertoken and create a new token.
- Paste the username given to `creds/maven_username.txt`
- Paste the password given to `creds/maven_password.txt`

5. you can now publish to maven by using `./gradlew publish`
