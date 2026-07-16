package io.github.elnix90.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.elnix90.core.objects.SettingObject
import kotlinx.coroutines.launch

/**
 * Collects the current value of this setting as a Compose [State], using a default value if none is set.
 *
 * This is useful for observing the setting in Composables and triggering recompositions automatically
 * when the value changes.
 *
 * @param default Optional default value to use before the first emission. If null, the setting's own default is used.
 * @return A [State] holding the current value of the setting.
 *
 */
@Composable
public fun <T, R> SettingObject<T, R>.asState(default: T? = null): State<T> {
    val ctx = LocalContext.current
    return flow(ctx).collectAsStateWithLifecycle(initialValue = default ?: this.default)
}

/**
 * Collects the current value of this setting as a Compose [MutableState], using a default value if none is set.
 *
 * This is useful for observing and updating the setting in Composables, triggering recompositions automatically
 * when the value changes. The returned [MutableState] allows direct assignment to update the setting asynchronously.
 *
 * @param default Optional default value to use before the first emission. If null, the setting's own default is used.
 * @return A [MutableState] holding the current value of the setting, allowing updates via assignment.
 */
@Composable
public fun <T, R> SettingObject<T, R>.asMutableState(default: T? = null): MutableState<T> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by flow(ctx).collectAsStateWithLifecycle(initialValue = default ?: this.default)

    return remember(state) {
        object : MutableState<T> {
            override var value: T
                get() = state
                set(value) {
                    scope.launch {
                       this@asMutableState.set(ctx, value)
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}


/**
 * Collects the current value of this setting as a Compose [State] that allows null values.
 *
 * Unlike [asState], this version always starts with `null` and can represent an unset state explicitly.
 * Useful when `null` has semantic meaning in your UI.
 *
 * @return A [State] holding the current value of the setting, or null if not yet set.
 */
@Composable
public fun <T, R> SettingObject<T, R>.asStateNull(): State<T?> {
    val ctx = LocalContext.current
    return flow(ctx).collectAsStateWithLifecycle(initialValue = null)
}


@Composable
public fun <T, R> SettingObject<T, R>.asMutableStateNull(): MutableState<T?> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by flow(ctx).collectAsStateWithLifecycle(initialValue = null)

    return remember(state) {
        object : MutableState<T?> {
            override var value: T?
                get() = state
                set(value) {
                    scope.launch {
                        this@asMutableStateNull.set(ctx, value)
                    }
                }

            override fun component1() = value
            override fun component2(): (T?) -> Unit = { value = it }
        }
    }
}