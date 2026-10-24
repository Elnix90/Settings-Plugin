package io.github.elnix90.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.elnix90.core.objects.BooleanSettingObject
import io.github.elnix90.runtime.asState
import io.github.elnix90.shared.ui.dragon.components.DragonGroupScope
import io.github.elnix90.shared.ui.dragon.components.SwitchRow
import io.github.elnix90.shared.ui.dragon.dialogs.UserValidation
import kotlinx.coroutines.launch

@Composable
public fun DragonGroupScope.Setting(
	setting: BooleanSettingObject,
	enabled: Boolean = true,
	needValidationToEnable: Boolean = false,
	needValidationToDisable: Boolean = false,
	confirmText: String = "Are you sure?",
	onCheck: ((Boolean) -> Unit)? = null
) {
	val ctx = LocalContext.current
	val scope = rememberCoroutineScope()

	val state by setting.asState()

	var showConfirmPopup by remember { mutableStateOf<Boolean?>(null) }

	fun toggle(state: Boolean) {
		onCheck?.invoke(state)
		scope.launch {
			setting.set(ctx, state)
		}
	}

	SwitchRow(
		state = state,
		title = setting.title!!,
		description = setting.description!!, // Keep the !! to enforce all boolean settings to have a desc
		icon = setting.icon,
		enabled = enabled
	) { clicked ->
		when {
			clicked && needValidationToEnable -> showConfirmPopup = true
			!clicked && needValidationToDisable -> showConfirmPopup = false
			else -> toggle(clicked)
		}
	}

	if (showConfirmPopup != null) {
		UserValidation(
			message = confirmText,
			onDismiss = { showConfirmPopup = null }
		) {
			toggle(showConfirmPopup!!)
			showConfirmPopup = null
		}
	}
}
