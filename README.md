# DragonLauncher Settings Compiler Plugin

A Kotlin compiler plugin for Kotlin K2 that removes boilerplate from settings declarations by generating setting keys and settings store collections automatically at compile time.

## Overview

The plugin provides two features:

### 1. Automatic setting keys

Instead of manually duplicating property names as string keys:

```kotlin
val hideBetaVersionWarning = boolean(
    key = "hideBetaVersionWarning"
)
```

you can write:

```kotlin
@SettingKey
val hideBetaVersionWarning = boolean(...)
```

and the compiler automatically rewrites the initializer to:

```kotlin
boolean(
    key = "hideBetaVersionWarning"
)
```

This keeps keys synchronized with property names and makes refactoring safe.

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

you can write:

```kotlin
@SettingStore
object MySettingsStore : MapSettingsStore(...) {

    @SettingKey
    val settingA = ...

    @SettingKey
    val settingB = ...

    @SettingKey
    val settingC = ...
}
```

and the compiler automatically generates:

```kotlin
override val ALL = listOf(
    settingA,
    settingB,
    settingC
)
```

No placeholder property, no manual list maintenance, and no risk of forgetting to update the collection when adding or removing settings.

---

## How It Works

The plugin uses both FIR and IR compiler extensions.

### FIR phase

The `SettingStoreFirExtension` is responsible for declaration generation.

When the compiler encounters a class or object annotated with:

```kotlin
@SettingStore
```

the FIR extension generates the declaration:

```kotlin
override val ALL: List<BaseSettingObject<*, *>>
```

This makes the property part of the class model so the rest of the compiler can see it.

The FIR phase only creates the declaration. It does not generate the implementation.

---

### IR phase

The IR phase contains two transformers.

#### SettingKeyTransformer

Processes properties annotated with:

```kotlin
@SettingKey
```

and rewrites the initializer call so that the `key` parameter receives the property's name.

Example:

```kotlin
@SettingKey
val showPreview = boolean(...)
```

becomes:

```kotlin
val showPreview = boolean(
    key = "showPreview"
)
```

---

#### SettingStoreTransformer

Processes classes annotated with:

```kotlin
@SettingStore
```

and:

1. Finds the generated `ALL` property.
2. Collects every property annotated with `@SettingKey`.
3. Creates a `listOf(...)` call containing all collected settings.
4. Assigns that call as the initializer of the generated `ALL` property.

Example:

```kotlin
@SettingStore
object UiSettingsStore : MapSettingsStore(...) {

    @SettingKey
    val settingA = ...

    @SettingKey
    val settingB = ...
}
```

becomes effectively:

```kotlin
object UiSettingsStore : MapSettingsStore(...) {

    val settingA = ...
    val settingB = ...

    override val ALL = listOf(
        settingA,
        settingB
    )
}
```

---

## Project Structure

### `:plugin-annotations`

Contains the annotations used by consumers of the plugin:

- `@SettingKey`
- `@SettingStore`

---

### `:compiler-plugin`

Contains the compiler plugin implementation.

#### FIR

- `SettingStoreFirExtension`

Generates the synthetic `ALL` property declaration.

#### IR

- `SettingKeyTransformer`
- `SettingStoreTransformer`

Generates the actual implementation code.

---

### `:gradle-plugin`

Gradle integration module that applies and configures the compiler plugin in user projects.

---

## Generated Code Summary

### `@SettingKey`

Input:

```kotlin
@SettingKey
val setting = boolean(...)
```

Generated:

```kotlin
val setting = boolean(
    key = "setting"
)
```

---

### `@SettingStore`

Input:

```kotlin
@SettingStore
object MyStore : MapSettingsStore(...) {

    @SettingKey
    val first = ...

    @SettingKey
    val second = ...
}
```

Generated:

```kotlin
object MyStore : MapSettingsStore(...) {

    val first = ...
    val second = ...

    override val ALL = listOf(
        first,
        second
    )
}
```
---

## Tests

There's no tests, idk how  do them.