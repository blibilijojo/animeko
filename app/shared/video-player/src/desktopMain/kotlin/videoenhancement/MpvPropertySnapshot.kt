/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.videoenhancement

import org.openani.mediamp.mpv.MPVHandle

/**
 * mpv 属性的读写入口.
 *
 * 抽出接口是为了让「超分与补帧能否共存」可以被单元测试覆盖: 两个控制器各自只持有自己的
 * [MpvPropertySnapshot], 谁先关闭都不会影响另一个.
 *
 * @since 4.9.0
 */
internal interface MpvPropertyStore {
    fun getPropertyString(name: String): String?

    fun setPropertyString(name: String, value: String): Boolean
}

/** 直接读写 mpv 句柄; 属性名不存在时返回 `null` 而不是抛异常. */
internal class HandleMpvPropertyStore(
    private val handle: MPVHandle,
) : MpvPropertyStore {
    override fun getPropertyString(name: String): String? =
        runCatching { handle.getPropertyString(name) }.getOrNull()

    override fun setPropertyString(name: String, value: String): Boolean =
        runCatching { handle.setPropertyString(name, value) }.getOrDefault(false)
}

/**
 * 一组 mpv 属性的原值快照.
 *
 * 快照只覆盖 [originals] 里的属性, [restore] 也只写这些属性, 因此多个功能只要属性集合不相交,
 * 就可以各自独立恢复. 写入失败只返回属性名, 由调用方决定是抛异常还是记日志.
 *
 * @since 4.9.0
 */
internal class MpvPropertySnapshot(
    private val store: MpvPropertyStore,
    /** 捕获时的原值. 属性不存在时不会包含在内. */
    val originals: Map<String, String>,
) {
    /** 写入 [values], 返回被 mpv 拒绝的属性名. */
    fun apply(values: Map<String, String>): List<String> =
        values.mapNotNull { (name, value) -> name.takeUnless { store.setPropertyString(name, value) } }

    /** 恢复全部原值, 返回被 mpv 拒绝的属性名. */
    fun restore(): List<String> = apply(originals)
}

/**
 * 捕获 [names] 的原值. 不可用的属性会被跳过, 由调用方决定是否视为致命错误.
 *
 * @since 4.9.0
 */
internal fun MpvPropertyStore.capture(names: Collection<String>): MpvPropertySnapshot =
    MpvPropertySnapshot(
        this,
        buildMap {
            for (name in names) {
                val value = getPropertyString(name) ?: continue
                put(name, value)
            }
        },
    )
