/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable

/**
 * 帧率提升 (补帧) 档位.
 *
 * 由 mpv 的时域插值实现: 把低于显示器刷新率的片源重采样到刷新率, 帧间用 [tscale][frameInterpolationMpvOptions]
 * 指定的核做时域混合. 它与 [VideoEnhancementDefaultMode] 管理的 mpv 属性不重叠, 两者可同时开启.
 *
 * 仅在使用 mpv 内核的桌面平台 (Windows) 生效.
 *
 * @since 4.9.0
 */
@Serializable
enum class FrameInterpolationMode {
    OFF,

    /**
     * 平滑优先: 混合更柔和, 快速平移镜头有轻微拖影.
     */
    SMOOTH,

    /**
     * 锐度优先: 中间帧的拖影更少, 高对比边缘可能出现轻微振铃.
     */
    SHARP,
}

/**
 * 帧率提升实际写到的 mpv 属性.
 *
 * 与 [me.him188.ani.app.videoplayer.videoenhancement] 里的超分属性集合互不相交,
 * 因此两个功能各自恢复自己的快照即可, 谁先关闭都不会影响另一个.
 *
 * @since 4.9.0
 */
val FRAME_INTERPOLATION_MPV_PROPERTIES: List<String> = listOf(
    "video-sync",
    "interpolation",
    "tscale",
)

/**
 * [mode] 对应的 mpv 属性设置. 返回空表示不改动 mpv.
 *
 * `video-sync=display-resample` 让 mpv 把画面重采样到显示器刷新率, 由 `interpolation` 补出中间帧,
 * 具体混合方式由 `tscale` 决定: `linear` 最平滑, `mitchell` 保留更多高频细节.
 *
 * @since 4.9.0
 */
fun frameInterpolationMpvOptions(mode: FrameInterpolationMode): Map<String, String> = when (mode) {
    FrameInterpolationMode.OFF -> emptyMap()
    FrameInterpolationMode.SMOOTH -> mapOf(
        "video-sync" to "display-resample",
        "interpolation" to "yes",
        "tscale" to "linear",
    )

    FrameInterpolationMode.SHARP -> mapOf(
        "video-sync" to "display-resample",
        "interpolation" to "yes",
        "tscale" to "mitchell",
    )
}
