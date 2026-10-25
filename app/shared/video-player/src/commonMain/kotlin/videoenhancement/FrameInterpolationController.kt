/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.videoenhancement

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.FrameInterpolationMode
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.CoroutineContext

/**
 * 补帧 (帧率提升) 控制器.
 *
 * 只改动 [me.him188.ani.app.data.models.preference.FRAME_INTERPOLATION_MPV_PROPERTIES] 中的 mpv 属性,
 * 与 [VideoEnhancementController] 管理的属性 (`scale`/`deband` 系列) 以及 `glsl-shaders` 都不重叠,
 * 因此超分与补帧可以同时开启, 各自持有独立的原值快照, 独立恢复, 关闭顺序不影响结果.
 *
 * @since 4.9.0
 */
interface FrameInterpolationController : AutoCloseable {
    val mode: StateFlow<FrameInterpolationMode>

    fun setMode(mode: FrameInterpolationMode)
}

/**
 * 创建补帧控制器. 返回 `null` 表示当前平台或播放器内核不支持, 调用方按未启用处理.
 *
 * @since 4.9.0
 */
expect fun createFrameInterpolationController(
    player: MediampPlayer,
    parentCoroutineContext: CoroutineContext,
): FrameInterpolationController?

internal abstract class BaseFrameInterpolationController(
    protected val player: MediampPlayer,
    parentCoroutineContext: CoroutineContext,
) : FrameInterpolationController {
    private val controllerJob = SupervisorJob(parentCoroutineContext[Job])
    protected val scope = CoroutineScope(parentCoroutineContext + controllerJob + player.mainDispatcher)
    private val mutableMode = MutableStateFlow(FrameInterpolationMode.OFF)

    final override val mode: StateFlow<FrameInterpolationMode> = mutableMode.asStateFlow()

    protected fun startObserving() {
        scope.launch {
            mode.collect { apply(it) }
        }
    }

    final override fun setMode(mode: FrameInterpolationMode) {
        mutableMode.value = mode
    }

    final override fun close() {
        restore()
        scope.cancel()
    }

    /** 按 [mode] 应用 mpv 属性. 由 [startObserving] 串行驱动. */
    protected abstract fun apply(mode: FrameInterpolationMode)

    /** 恢复首次修改前的 mpv 属性. 只恢复本控制器写过的属性. */
    protected abstract fun restore()
}
