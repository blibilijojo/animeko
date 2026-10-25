/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.models.preference.FrameInterpolationMode
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.rememberTestSettingsState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class FrameInterpolationDefaultItemTest {
    @Test
    fun `selecting a mode saves it to video scaffold config`() = runAniComposeUiTest {
        lateinit var state: SettingsState<VideoScaffoldConfig>
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab {
                    state = rememberTestSettingsState(VideoScaffoldConfig.Default)
                    PlayerGroupPlatform(state, rememberTestSettingsState(PlayerKernelConfig.Default))
                }
            }
        }

        assertEquals(FrameInterpolationMode.OFF, state.value.frameInterpolationDefaultMode)

        onNodeWithTag(FrameInterpolationDefaultItemTestTags.ITEM).performClick()
        waitForIdle()

        onNodeWithText("平滑").performClick()
        waitUntil { state.value.frameInterpolationDefaultMode == FrameInterpolationMode.SMOOTH }
    }

    @Test
    fun `the current mode is shown without opening the dropdown`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab {
                    PlayerGroupPlatform(
                        rememberTestSettingsState(
                            VideoScaffoldConfig.Default.copy(
                                frameInterpolationDefaultMode = FrameInterpolationMode.SHARP,
                            ),
                        ),
                        rememberTestSettingsState(PlayerKernelConfig.Default),
                    )
                }
            }
        }

        onNodeWithText("锐利").assertExists()
    }
}
