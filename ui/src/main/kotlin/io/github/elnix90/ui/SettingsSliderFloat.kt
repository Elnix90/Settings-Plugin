package io.github.elnix90.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.elnix90.annotations.SettingCall
import io.github.elnix90.core.objects.FloatSettingObject
import io.github.elnix90.runtime.asState
import io.github.elnix90.shared.ui.dragon.components.DragonGroupScope
import io.github.elnix90.shared.ui.dragon.components.SliderWithLabel
import kotlinx.coroutines.launch

@SettingCall
@Composable
public fun DragonGroupScope.Setting(
	setting: FloatSettingObject,
	modifier: Modifier = Modifier,
	decimals: Int = 2,
	enabled: Boolean = true,
	customDesc: ((Float) -> String)? = null
) {
	val ctx = LocalContext.current
	val scope = rememberCoroutineScope()

	val state by setting.asState()

	var tempState by remember { mutableFloatStateOf(state) }

	LaunchedEffect(state) { tempState = state }

	SliderWithLabel(
		label = stringResource(setting.title!!),
		description = customDesc?.invoke(state) ?: stringResource(setting.description!!),
		value = tempState,
		valueRange = setting.allowedRange,
		enabled = enabled,
		decimals = decimals,
		resetEnabled = tempState != setting.default,
		onReset = { scope.launch { setting.reset(ctx) } },
		onDragStateChange = { scope.launch { setting.set(ctx, tempState) } }
	) { tempState = it }
}
