package io.github.elnix90.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.elnix90.annotations.SettingEntry
import io.github.elnix90.shared.ui.base.components.BoxedIcon
import io.github.elnix90.shared.ui.dragon.components.DragonGroupScope
import io.github.elnix90.shared.ui.dragon.text.TextWithDescription

/**
 * A ready-made clickable settings row that indexes itself for search.
 *
 * This is a pre-built [SettingEntry] wrapper: applications annotate nothing.
 * The compiler plugin snapshots [title], [description] and [icon] from each
 * call site into the module's search index, so every row created with this
 * function is findable by its localized text. Because annotation arguments
 * must be compile-time constants (and library-module `R` fields are not),
 * the metadata travels through ordinary parameters instead.
 *
 * ```kotlin
 * SettingAction(
 *     title = R.string.set_wallpaper,
 *     icon = R.drawable.ic_wallpaper,
 *     onClick = ::openWallpaperPicker
 * ) {
 *     ChevronIcon() // optional trailing content
 * }
 * ```
 *
 * @param title String resource id of the row title; indexed for search.
 * @param description Optional string resource id shown under the title.
 * @param icon Optional drawable resource id shown on the left.
 * @param enabled Whether the row accepts clicks.
 * @param onClick Invoked when the row is clicked.
 * @param trailingContent Optional trailing slot rendered after the title.
 */
@SettingEntry
@Composable
public fun DragonGroupScope.SettingAction(
	@StringRes title: Int,
	@StringRes description: Int? = null,
	@DrawableRes icon: Int? = null,
	enabled: Boolean = true,
	onClick: () -> Unit,
	trailingContent: @Composable RowScope.() -> Unit = {}
) {
	Row(
		modifier =
			Modifier
				.dragonSettingGroup(enabled) {
					clickable(
						enabled = enabled,
						onClick = onClick
					)
				},
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp)
	) {
		if (icon != null) {
			BoxedIcon(icon, enabled)
		}

		TextWithDescription(
			text = stringResource(title),
			description = description?.let { stringResource(description) },
			modifier = Modifier.weight(1f),
			enabled = enabled
		)

		trailingContent()
	}
}
