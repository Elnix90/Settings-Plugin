package io.github.elnix90.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import io.github.elnix90.annotations.SettingEntry

public class SettingActionScope
	internal constructor(
		@StringRes public val title: Int,
		@StringRes public val description: Int?,
		@DrawableRes public val icon: Int?
	)

/**
 * A helper to mark any composable to be discovered using the [SettingEntry] annotation.
 *
 * Simply put your button or anything in there to make it discovered by the compiler plugin as a searchable setting
 */
@SettingEntry
@Composable
public fun SettingAction(
	@StringRes title: Int,
	@StringRes description: Int? = null,
	@DrawableRes icon: Int? = null,
	content: @Composable SettingActionScope.() -> Unit
) {
	SettingActionScope(
		title = title,
		description = description,
		icon = icon
	).content()
}
