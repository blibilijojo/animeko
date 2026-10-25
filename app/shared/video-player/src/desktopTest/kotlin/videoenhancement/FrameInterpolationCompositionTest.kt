/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.videoenhancement

import me.him188.ani.app.data.models.preference.FRAME_INTERPOLATION_MPV_PROPERTIES
import me.him188.ani.app.data.models.preference.FrameInterpolationMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 超分与补帧的共存矩阵.
 *
 * 两个控制器各自只持有自己属性集合的快照, 因此任意开启顺序、任意关闭顺序都不应该互相污染.
 */
class FrameInterpolationCompositionTest {
    private class FakeMpvPropertyStore(initial: Map<String, String>) : MpvPropertyStore {
        val values: MutableMap<String, String> = initial.toMutableMap()
        val rejected: MutableSet<String> = mutableSetOf()

        override fun getPropertyString(name: String): String? = values[name]

        override fun setPropertyString(name: String, value: String): Boolean {
            if (name in rejected) return false
            values[name] = value
            return true
        }
    }

    /** 模拟超分控制器: 快照自己的属性, 开启时写入自己的目标值, 关闭时恢复. */
    private class FakeEnhancementController(store: MpvPropertyStore) {
        private val snapshot = store.capture(enhancementPropertyNames)
        private var on = false

        fun enable() {
            snapshot.apply(enhancementTargetValues)
            on = true
        }

        fun disable() {
            snapshot.restore()
            on = false
        }

        fun isOn() = on
    }

    private fun storeWithDefaults() = FakeMpvPropertyStore(
        buildMap {
            for (name in enhancementPropertyNames) put(name, "default-$name")
            for (name in FRAME_INTERPOLATION_MPV_PROPERTIES) put(name, "default-$name")
            put("glsl-shaders", "")
        },
    )

    @Test
    fun `the two controllers own disjoint mpv properties`() {
        val overlap = enhancementPropertyNames.filter { it in FRAME_INTERPOLATION_MPV_PROPERTIES }

        assertTrue(overlap.isEmpty(), "overlapping mpv properties: $overlap")
        assertTrue("glsl-shaders" !in FRAME_INTERPOLATION_MPV_PROPERTIES)
    }

    @Test
    fun `frame interpolation only writes its own properties`() {
        val store = storeWithDefaults()
        val enhancement = FakeEnhancementController(store)
        enhancement.enable()
        val enhancementValues = store.values.filterKeys { it in enhancementPropertyNames }

        FrameInterpolationStateOn(store).use { it.setMode(FrameInterpolationMode.SMOOTH) }

        assertEquals(
            enhancementValues,
            store.values.filterKeys { it in enhancementPropertyNames },
            "frame interpolation must not touch super resolution properties",
        )
    }

    @Test
    fun `closing frame interpolation keeps super resolution applied`() {
        val store = storeWithDefaults()
        val enhancement = FakeEnhancementController(store)
        enhancement.enable()
        val enhancementValues = store.values.filterKeys { it in enhancementPropertyNames }

        val interpolation = FrameInterpolationStateOn(store)
        interpolation.setMode(FrameInterpolationMode.SHARP)
        interpolation.close()

        assertTrue(enhancement.isOn())
        assertEquals(
            enhancementValues,
            store.values.filterKeys { it in enhancementPropertyNames },
            "closing frame interpolation reverted super resolution",
        )
        assertEquals(
            "default-video-sync",
            store.values["video-sync"],
            "closing frame interpolation must restore its own property",
        )
    }

    @Test
    fun `closing super resolution keeps frame interpolation applied`() {
        val store = storeWithDefaults()
        val interpolation = FrameInterpolationStateOn(store)
        interpolation.setMode(FrameInterpolationMode.SMOOTH)
        val interpolationValues = store.values.filterKeys { it in FRAME_INTERPOLATION_MPV_PROPERTIES }

        val enhancement = FakeEnhancementController(store)
        enhancement.enable()
        enhancement.disable()

        assertEquals(
            interpolationValues,
            store.values.filterKeys { it in FRAME_INTERPOLATION_MPV_PROPERTIES },
            "closing super resolution reverted frame interpolation",
        )
    }

    @Test
    fun `restore returns the original values regardless of order`() {
        for (interpolationFirst in listOf(true, false)) {
            val store = storeWithDefaults()
            val original = store.values.toMap()
            val enhancement = FakeEnhancementController(store)
            val interpolation = FrameInterpolationStateOn(store)

            if (interpolationFirst) {
                interpolation.setMode(FrameInterpolationMode.SMOOTH)
                enhancement.enable()
            } else {
                enhancement.enable()
                interpolation.setMode(FrameInterpolationMode.SMOOTH)
            }

            interpolation.close()
            enhancement.disable()

            assertEquals(
                original,
                store.values,
                "restore order mismatch (interpolationFirst=$interpolationFirst)",
            )
        }
    }

    @Test
    fun `switching mode twice does not leave stale values`() {
        val store = storeWithDefaults()
        val original = store.values.toMap()
        FrameInterpolationStateOn(store).use {
            it.setMode(FrameInterpolationMode.SMOOTH)
            it.setMode(FrameInterpolationMode.SHARP)
        }

        assertEquals(original, store.values)
    }

    @Test
    fun `rejected properties do not break the other controller`() {
        val store = storeWithDefaults()
        store.rejected += "tscale"
        val enhancement = FakeEnhancementController(store)
        enhancement.enable()
        val enhancementValues = store.values.filterKeys { it in enhancementPropertyNames }

        FrameInterpolationStateOn(store).use { it.setMode(FrameInterpolationMode.SMOOTH) }

        assertEquals(enhancementValues, store.values.filterKeys { it in enhancementPropertyNames })
        assertEquals("default-video-sync", store.values["video-sync"], "partial apply must still restore")
    }

    @Test
    fun `mode changes after close are ignored`() {
        val store = storeWithDefaults()
        val original = store.values.toMap()
        val interpolation = FrameInterpolationStateOn(store)
        interpolation.setMode(FrameInterpolationMode.SMOOTH)
        interpolation.close()

        interpolation.setMode(FrameInterpolationMode.SHARP)

        assertEquals(original, store.values, "a late mode change must not re-apply properties after close")
    }

    /** 包一层, 让测试可以直接按 mode 驱动 [MpvFrameInterpolationState]. */
    private class FrameInterpolationStateOn(store: MpvPropertyStore) : AutoCloseable {
        private val state = MpvFrameInterpolationState(store)

        fun setMode(mode: FrameInterpolationMode) = state.apply(mode)

        override fun close() = state.restore()
    }

    private companion object {
        val enhancementTargetValues = mapOf(
            "correct-downscaling" to "yes",
            "linear-downscaling" to "yes",
            "sigmoid-upscaling" to "yes",
            "scale" to "ewa_lanczossharp",
            "dscale" to "ewa_lanczossharp",
            "cscale" to "ewa_lanczossharp",
            "scale-antiring" to "0.7",
            "dscale-antiring" to "0.7",
            "deband" to "no",
            "deband-iterations" to "1",
            "deband-threshold" to "32",
            "deband-range" to "16",
            "deband-grain" to "0",
        )
    }
}
