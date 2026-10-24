package io.github.elnix90.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.elnix90.core.objects.DpSettingObject
import io.github.elnix90.runtime.asState
import io.github.elnix90.shared.ui.dragon.components.DragonGroupScope
import io.github.elnix90.shared.ui.dragon.components.SliderWithLabel
import kotlinx.coroutines.launch

@Composable
public fun DragonGroupScope.Setting(
	setting: DpSettingObject,
	enabled: Boolean = true,
	customDesc: (@Composable (Dp) -> String?) = { null }
) {
	val ctx = LocalContext.current
	val scope = rememberCoroutineScope()

	val state by setting.asState()

	var tempState by remember { mutableStateOf(state) }

	LaunchedEffect(state) { tempState = state }

	SliderWithLabel(
		label = stringResource(setting.title!!),
		description = customDesc.invoke(state) ?: stringResource(setting.description!!),
		value = tempState,
		valueRange = setting.allowedRange,
		enabled = enabled,
		resetEnabled = tempState != setting.default,
		onReset = { scope.launch { setting.reset(ctx) } },
		onDragStateChange = { scope.launch { setting.set(ctx, tempState) } },
		onChange = { tempState = it }
	)
}
