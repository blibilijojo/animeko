/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.videoenhancement

import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.utils.platform.currentPlatformDesktop
import me.him188.ani.utils.platform.isLinux
import me.him188.ani.utils.platform.isWindows
import me.him188.ani.utils.video.enhancement.shader.provider.VideoEnhancementShaderProvider
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.mpv.MPVHandle
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

/** macOS and Windows share the shader and scaler; Windows omits deband for its lighter profile. */
actual fun createVideoEnhancementController(
    player: MediampPlayer,
    playerKernelConfig: Flow<PlayerKernelConfig>,
    parentCoroutineContext: CoroutineContext,
): VideoEnhancementController? {
    if (currentPlatformDesktop().isLinux()) {
        return null
    }
    val handle = player.impl as? MPVHandle ?: return null
    return MpvVideoEnhancementController(player, handle, parentCoroutineContext)
}

private class MpvVideoEnhancementController(
    player: MediampPlayer,
    private val handle: MPVHandle,
    parentCoroutineContext: CoroutineContext,
) : BaseVideoEnhancementController(player, parentCoroutineContext) {
    private val lock = Any()
    private val originalProperties = enhancementPropertyNames.associateWith { name ->
        checkNotNull(handle.getPropertyString(name)) { "mpv property is unavailable: $name" }
    }
    private val clearProperties = if (currentPlatformDesktop().isWindows()) {
        windowsLiteClearProperties
    } else {
        fullClearProperties
    }

    private var effectiveMode: VideoEnhancementMode = VideoEnhancementMode.OFF
    private val appliedAnime4kShaders = mutableListOf<Path>()

    init {
        startObserving()
    }

    override suspend fun apply(
        mode: VideoEnhancementMode,
        videoSize: VideoDimensions?,
        viewportSize: VideoDimensions?,
    ) {
        val targetMode = getEffectiveMode(mode, videoSize, viewportSize)
        if (synchronized(lock) { targetMode == effectiveMode }) return

        val shaderPaths = when (targetMode) {
            VideoEnhancementMode.OFF -> emptyList()
            VideoEnhancementMode.PERFORMANCE -> listOf(
                VideoEnhancementShaderProvider.getShaderPath(anime4kRestoreShaderName),
            )

            VideoEnhancementMode.QUALITY -> anime4kQualityShaderNames.map {
                VideoEnhancementShaderProvider.getShaderPath(it)
            }

            VideoEnhancementMode.ARTCNN_FAST -> listOf(
                VideoEnhancementShaderProvider.getShaderPath(artCnnFastShaderName),
            )

            VideoEnhancementMode.ARTCNN_HQ -> listOf(
                VideoEnhancementShaderProvider.getShaderPath(artCnnHqShaderName),
            )
        }

        synchronized(lock) {
            applyEffectiveModeLocked(targetMode, shaderPaths)
        }
    }

    private fun getEffectiveMode(
        requestedMode: VideoEnhancementMode,
        videoSize: VideoDimensions?,
        viewportSize: VideoDimensions?,
    ): VideoEnhancementMode {
        val scale = if (videoSize != null && viewportSize != null) {
            minOf(
                viewportSize.width.toDouble() / videoSize.width,
                viewportSize.height.toDouble() / videoSize.height,
            )
        } else {
            0.0
        }
        // ArtCNN 的 shader 自带 `//!WHEN OUTPUT.w LUMA.w / 1.3 >`, 放大不足 1.3 倍时 mpv 会跳过它.
        // 这里用同一个阈值, 免得 UI 显示已启用而实际什么都没做.
        val minScale = if (requestedMode == VideoEnhancementMode.ARTCNN_FAST ||
            requestedMode == VideoEnhancementMode.ARTCNN_HQ
        ) {
            artCnnMinUpscale
        } else {
            1.0
        }
        return if (requestedMode != VideoEnhancementMode.OFF && scale > minScale) {
            requestedMode
        } else {
            VideoEnhancementMode.OFF
        }
    }

    private fun applyEffectiveModeLocked(
        targetMode: VideoEnhancementMode,
        shaderPaths: List<Path>,
    ) {
        if (targetMode != effectiveMode) {
            removeAnime4kShadersLocked()
            val properties = when (targetMode) {
                VideoEnhancementMode.OFF -> originalProperties
                VideoEnhancementMode.PERFORMANCE,
                VideoEnhancementMode.QUALITY,
                    -> clearProperties

                // ArtCNN 自己把 LUMA 放大 2 倍并做抗振铃, 再让 mpv 用 ewa_lanczossharp 重采样
                // 只会把 CNN 的锐度糊掉. 后续缩放交给 bilinear, 与 mpv 官方 ArtCNN 用法一致.
                VideoEnhancementMode.ARTCNN_FAST,
                VideoEnhancementMode.ARTCNN_HQ,
                    -> artCnnProperties
            }
            properties.forEach { (name, value) ->
                check(handle.setPropertyString(name, value)) {
                    "mpv rejected video enhancement property $name=$value"
                }
            }
            shaderPaths.forEach(::applyAnime4kShaderLocked)
            effectiveMode = targetMode
        }
    }

    private fun applyAnime4kShaderLocked(shaderPath: Path) {
        val absolutePath = shaderPath.toAbsolutePath()
        check(handle.command("change-list", "glsl-shaders", "append", absolutePath.toString())) {
            "mpv rejected Anime4K shader: $absolutePath"
        }
        appliedAnime4kShaders.add(absolutePath)
    }

    private fun removeAnime4kShadersLocked() {
        appliedAnime4kShaders.asReversed().forEach { shaderPath ->
            check(handle.command("change-list", "glsl-shaders", "remove", shaderPath.toString())) {
                "mpv failed to remove Anime4K shader: $shaderPath"
            }
        }
        appliedAnime4kShaders.clear()
    }

    override fun restore() {
        synchronized(lock) {
            appliedAnime4kShaders.asReversed().forEach { shaderPath ->
                runCatching {
                    handle.command("change-list", "glsl-shaders", "remove", shaderPath.toString())
                }
            }
            appliedAnime4kShaders.clear()
            originalProperties.forEach { (name, value) ->
                runCatching { handle.setPropertyString(name, value) }
            }
            effectiveMode = VideoEnhancementMode.OFF
        }
    }
}

private const val anime4kRestoreShaderName = "Anime4K_Restore_CNN_S.glsl"

/** ArtCNN C4F16: 12k 参数, 212 KB, 实时无压力. */
private const val artCnnFastShaderName = "ArtCNN_C4F16.glsl"

/** ArtCNN C4F32: 48k 参数, 743 KB, 明显更锐利也更吃显卡. */
private const val artCnnHqShaderName = "ArtCNN_C4F32.glsl"

/**
 * ArtCNN 的 shader 首行条件 `//!WHEN OUTPUT.w LUMA.w / 1.3 > OUTPUT.h LUMA.h / 1.3 > *`
 * 要求放大至少 1.3 倍, 否则 libplacebo 直接跳过整个 hook. 控制器用同一阈值.
 */
private const val artCnnMinUpscale = 1.3

private val anime4kQualityShaderNames = listOf(
    "Anime4K_Clamp_Highlights.glsl",
    "Anime4K_Restore_CNN_VL.glsl",
    "Anime4K_Upscale_CNN_x2_VL.glsl",
    "Anime4K_AutoDownscalePre_x2.glsl",
    "Anime4K_AutoDownscalePre_x4.glsl",
    "Anime4K_Upscale_CNN_x2_M.glsl",
)

/** 超分控制器管理的 mpv 属性. 与补帧的 `FRAME_INTERPOLATION_MPV_PROPERTIES` 必须零交集. */
internal val enhancementPropertyNames = listOf(
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

private val fullClearProperties = mapOf(
    "correct-downscaling" to "yes",
    "linear-downscaling" to "yes",
    "sigmoid-upscaling" to "yes",
    "scale" to "ewa_lanczossharp",
    "dscale" to "ewa_lanczossharp",
    "cscale" to "ewa_lanczossharp",
    "scale-antiring" to "0.7",
    "dscale-antiring" to "0.7",
    "deband" to "yes",
    "deband-iterations" to "1",
    "deband-threshold" to "32",
    "deband-range" to "16",
    "deband-grain" to "0",
)

private val windowsLiteClearProperties = fullClearProperties + ("deband" to "no")

/**
 * ArtCNN 档位的 mpv 属性.
 *
 * ArtCNN 只对 LUMA 做 2x 上采样 (最后 Depth-To-Space 输出 `LUMA.w 2.0 *`), 自带 `//!WHEN` 门槛,
 * 并已经包含抗振铃和 sigmoid 上升沿. 因此后续的缩放/振铃交给 bilinear 收尾即可,
 * 避免 ewa_lanczossharp 在 CNN 输出上再做一次重采样把细节糊掉.
 * 亮度去色带 (`deband`) 仍然关掉, 与 Windows 的轻量档保持一致.
 */
internal val artCnnProperties = mapOf(
    "correct-downscaling" to "no",
    "linear-downscaling" to "yes",
    "sigmoid-upscaling" to "no",
    "scale" to "bilinear",
    "dscale" to "bilinear",
    "cscale" to "bilinear",
    "scale-antiring" to "0.0",
    "dscale-antiring" to "0.0",
    "deband" to "no",
    "deband-iterations" to "1",
    "deband-threshold" to "32",
    "deband-range" to "16",
    "deband-grain" to "0",
)
