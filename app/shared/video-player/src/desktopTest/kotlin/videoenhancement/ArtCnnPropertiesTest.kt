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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ArtCNN 档位在桌面端 mpv 上的属性契约.
 *
 * 控制器在进入某个档位时会先恢复自己快照里的全部属性再写入目标值, 所以
 * **目标值的键集合必须与快照的键集合完全一致**, 否则会有属性残留.
 */
class ArtCnnPropertiesTest {
    @Test
    fun `artcnn writes exactly the snapshot property set`() {
        assertEquals(
            enhancementPropertyNames.toSet(),
            artCnnProperties.keys,
            "artCnnProperties must cover exactly the properties captured in the snapshot",
        )
    }

    @Test
    fun `artcnn leaves frame interpolation properties alone`() {
        val overlap = artCnnProperties.keys.filter { it in FRAME_INTERPOLATION_MPV_PROPERTIES }

        assertTrue(overlap.isEmpty(), "artCnnProperties overlaps frame interpolation: $overlap")
    }

    @Test
    fun `artcnn hands the final rescale to bilinear`() {
        // ArtCNN 自己把 LUMA 放大 2 倍; 后续再让 ewa_lanczossharp 重采样会把 CNN 的锐度糊掉.
        assertEquals("bilinear", artCnnProperties["scale"])
        assertEquals("bilinear", artCnnProperties["dscale"])
        assertEquals("bilinear", artCnnProperties["cscale"])
        assertEquals("no", artCnnProperties["sigmoid-upscaling"])
        assertEquals("0.0", artCnnProperties["scale-antiring"])
        assertEquals("0.0", artCnnProperties["dscale-antiring"])
    }
}
