package com.Tangle.timetable.update

/**
 * 更新检查的结果密封类
 */
sealed class UpdateState {
    /** 已是最新（或已被用户跳过） */
    object Latest : UpdateState()

    /** 发现新版本 */
    data class Available(val info: UpdateInfo) : UpdateState()

    /** 无网络 / 无法连接（GitHub 在国内常被墙等） */
    object NoNetwork : UpdateState()

    /** 检查过程出错（限流、JSON 解析失败、镜像全部不可用等） */
    data class Error(val message: String) : UpdateState()
}

/**
 * 一个可安装的新版本信息
 * @param versionCode 远程版本号（来自 release tag，纯数字时有效；否则为 0）
 * @param versionName 展示用版本名（如 v162 / v3.693）
 * @param apkUrl      经国内镜像代理后的 APK 下载地址
 * @param notes       更新说明（release body，可能含 markdown）
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)
