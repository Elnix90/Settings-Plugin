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
val settingA: Boolean by MySettingsStore.settingA.asState()

// settingA is state reactive
AnimatedVisibility(settingA) {
    SomeComposable()
}
```


The plugin provides 3 main features:

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

You can create a value
```kotlin
val allStores: Set<SettingsStore<*,*>> = emptySet()
```

And the compiler fills it at compile time with all you stores annotated with `@SettingsStore`




## Download

![Maven Central Version](https://img.shields.io/maven-central/v/io.github.elnix90.settings/compiler-plugin)

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
