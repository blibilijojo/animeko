/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import me.him188.ani.app.data.persistent.DataStoreJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameInterpolationOptionsTest {
    @Test
    fun `off applies no mpv option`() {
        assertEquals(emptyMap(), frameInterpolationMpvOptions(FrameInterpolationMode.OFF))
    }

    @Test
    fun `smooth resamples to display and blends linearly`() {
        assertEquals(
            mapOf(
                "video-sync" to "display-resample",
                "interpolation" to "yes",
                "tscale" to "linear",
            ),
            frameInterpolationMpvOptions(FrameInterpolationMode.SMOOTH),
        )
    }

    @Test
    fun `sharp resamples to display and blends with mitchell`() {
        assertEquals(
            mapOf(
                "video-sync" to "display-resample",
                "interpolation" to "yes",
                "tscale" to "mitchell",
            ),
            frameInterpolationMpvOptions(FrameInterpolationMode.SHARP),
        )
    }

    @Test
    fun `enabled modes only ever set the managed properties`() {
        for (mode in FrameInterpolationMode.entries) {
            assertTrue(
                frameInterpolationMpvOptions(mode).keys.all { it in FRAME_INTERPOLATION_MPV_PROPERTIES },
                "mode $mode set a property outside FRAME_INTERPOLATION_MPV_PROPERTIES",
            )
        }
    }

    @Test
    fun `managed properties do not overlap video enhancement properties`() {
        // 超分控制器 (MpvVideoEnhancementController) 管理的属性, 见 enhancementPropertyNames.
        val enhancementProperties = setOf(
            "correct-downscaling",
            "linear-downscaling",
            "sigmoid-upscaling",
            "scale",
            "dscale",
            "cscale",
            "scale-antiring",
            "dscale-antiring",
            "deband",
            "deband-iterations",
            "deband-threshold",
            "deband-range",
            "deband-grain",
        )

        assertTrue(
            FRAME_INTERPOLATION_MPV_PROPERTIES.none { it in enhancementProperties },
            "frame interpolation and video enhancement share a property, they cannot restore independently",
        )
        // 超分用 change-list 管理着色器列表, 补帧不能碰它.
        assertTrue("glsl-shaders" !in FRAME_INTERPOLATION_MPV_PROPERTIES)
    }

    @Test
    fun `default mode is off`() {
        val config = DataStoreJson.decodeFromString(VideoScaffoldConfig.serializer(), "{}")

        assertEquals(FrameInterpolationMode.OFF, config.frameInterpolationDefaultMode)
    }

    @Test
    fun `mode survives serialization`() {
        for (mode in FrameInterpolationMode.entries) {
            val config = VideoScaffoldConfig.Default.copy(frameInterpolationDefaultMode = mode)

            val decoded = DataStoreJson.decodeFromString(
                VideoScaffoldConfig.serializer(),
                DataStoreJson.encodeToString(VideoScaffoldConfig.serializer(), config),
            )

            assertEquals(mode, decoded.frameInterpolationDefaultMode)
        }
    }

    @Test
    fun `each mode maps to its own tscale`() {
        val tscales = FrameInterpolationMode.entries
            .filter { it != FrameInterpolationMode.OFF }
            .map { frameInterpolationMpvOptions(it).getValue("tscale") }

        assertEquals(tscales.size, tscales.toSet().size, "two modes produced the same tscale: $tscales")
    }
}
