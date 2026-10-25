/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.FrameInterpolationMode
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_frame_interpolation
import me.him188.ani.app.ui.lang.video_player_frame_interpolation_sharp
import me.him188.ani.app.ui.lang.video_player_frame_interpolation_smooth
import me.him188.ani.app.ui.lang.video_player_off
import me.him188.ani.app.videoplayer.videoenhancement.FrameInterpolationController
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器内的超帧率档位选择. 与 [VideoEnhancementDropdown] 并列, 两者可同时启用.
 *
 * @since 4.9.0
 */
@Composable
fun FrameInterpolationDropdown(
    frameInterpolation: FrameInterpolationController,
    showDropdown: Boolean,
    onDismissRequest: () -> Unit,
) {
    val mode by frameInterpolation.mode.collectAsState()
    val title = stringResource(Lang.video_player_frame_interpolation)
    val smoothText = stringResource(Lang.video_player_frame_interpolation_smooth)
    val sharpText = stringResource(Lang.video_player_frame_interpolation_sharp)
    val offText = stringResource(Lang.video_player_off)

    DropdownMenu(
        expanded = showDropdown,
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        listOf(
            FrameInterpolationMode.SMOOTH,
            FrameInterpolationMode.SHARP,
            FrameInterpolationMode.OFF,
        ).forEach { item ->
            DropdownMenuItem(
                text = {
                    Text(
                        when (item) {
                            FrameInterpolationMode.OFF -> offText
                            FrameInterpolationMode.SMOOTH -> smoothText
                            FrameInterpolationMode.SHARP -> sharpText
                        },
                    )
                },
                onClick = {
                    frameInterpolation.setMode(item)
                    onDismissRequest()
                },
                enabled = item != mode,
            )
        }
    }
}
