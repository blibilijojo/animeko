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
import me.him188.ani.app.data.models.preference.frameInterpolationMpvOptions
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentPlatformDesktop
import me.him188.ani.utils.platform.isLinux
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.mpv.MPVHandle
import kotlin.coroutines.CoroutineContext

private val logger = logger<MpvFrameInterpolationController>()

actual fun createFrameInterpolationController(
    player: MediampPlayer,
    parentCoroutineContext: CoroutineContext,
): FrameInterpolationController? {
    if (currentPlatformDesktop().isLinux()) {
        return null
    }
    val handle = player.impl as? MPVHandle ?: return null
    return MpvFrameInterpolationController(
        player,
        MpvFrameInterpolationState(HandleMpvPropertyStore(handle)),
        parentCoroutineContext,
    )
}

private class MpvFrameInterpolationController(
    player: MediampPlayer,
    private val state: MpvFrameInterpolationState,
    parentCoroutineContext: CoroutineContext,
) : BaseFrameInterpolationController(player, parentCoroutineContext) {
    init {
        startObserving()
    }

    override fun apply(mode: FrameInterpolationMode) {
        state.apply(mode)
    }

    override fun restore() {
        state.restore()
    }
}

/**
 * 补帧的可测状态机: 只通过 [MpvPropertyStore] 读写 mpv 属性.
 *
 * 用 mpv 原生时域插值 (`--interpolation` + `--video-sync=display-resample`) 做补帧.
 * 只写 [FRAME_INTERPOLATION_MPV_PROPERTIES] 里的属性, 与 [MpvVideoEnhancementController] 管理的
 * `scale`/`deband` 系列和 `glsl-shaders` 零重叠, 所以两者可以同时开启, 各自恢复.
 *
 * 注意这不是 AI 补帧 (RIFE/SVP/minterpolate): mediamp 附带的 `libmpv-2.dll` 已裁剪掉
 * `avfilter` 的 `minterpolate`, 也没有 VapourSynth. 效果是"帧混合"级别, 快速平移会有拖影.
 *
 * @since 4.9.0
 */
internal class MpvFrameInterpolationState(
    private val store: MpvPropertyStore,
) {
    private val lock = Any()

    /**
     * 打开补帧前的原值. 此时用户的「自定义 mpv 选项」已经应用过 (见 EpisodeViewModel 的
     * `applyCustomOptions`), 所以用户显式设置的值会被原样保留并在关闭时还原.
     */
    private val snapshot = store.capture(FRAME_INTERPOLATION_MPV_PROPERTIES)

    private var appliedMode: FrameInterpolationMode = FrameInterpolationMode.OFF

    /** 关闭后不再接受任何 [apply], 避免已排队的协程在 [restore] 之后又写回 mpv 属性. */
    private var closed = false

    init {
        snapshot.originals.keys
            .let { captured ->
                val missing = FRAME_INTERPOLATION_MPV_PROPERTIES.filterNot { it in captured }
                if (missing.isNotEmpty()) {
                    logger.warn { "mpv property is unavailable, frame interpolation may not work: $missing" }
                }
            }
    }

    fun apply(mode: FrameInterpolationMode) {
        synchronized(lock) {
            if (closed) return
            if (mode == appliedMode) return

            if (mode == FrameInterpolationMode.OFF) {
                restoreLocked()
                return
            }

            val rejected = snapshot.apply(frameInterpolationMpvOptions(mode))
            if (rejected.isNotEmpty()) {
                logger.warn { "mpv rejected frame interpolation properties: $rejected" }
            }
            appliedMode = mode
        }
    }

    fun restore() {
        synchronized(lock) {
            closed = true
            restoreLocked()
        }
    }

    private fun restoreLocked() {
        val rejected = snapshot.restore()
        if (rejected.isNotEmpty()) {
            logger.error { "Failed to restore mpv properties: $rejected" }
        }
        appliedMode = FrameInterpolationMode.OFF
    }
}
