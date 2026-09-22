package com.Tangle.timetable.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 通过 GitHub Releases API 获取最新版本；依次尝试多个国内可访问的镜像前缀，
 * 任一成功即返回。镜像全部失败则抛异常，由上层归类为 NoNetwork / Error。
 */
object UpdateClient {

    private const val REPO = "Fuhanglio/XiaoTang-Tangle"
    private const val API_PATH = "https://api.github.com/repos/$REPO/releases/latest"

    /** 国内可访问的 GitHub 加速镜像：直接前缀拼接完整官方 URL */
    private val MIRRORS = listOf(
        "https://ghproxy.net/",
        "https://ghproxy.com/",
        "https://ghfast.top/",
        "https://mirror.ghproxy.com/",
        "" // 直连官方（最后兜底，国内通常不可用）
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 拉取最新 release，返回可安装版本信息；失败抛异常 */
    @Throws(Exception::class)
    suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (mirror in MIRRORS) {
            try {
                val json = requestJson(mirror + API_PATH)
                val info = parse(json, mirror) ?: continue
                return@withContext info
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("无法获取更新信息")
    }

    private fun requestJson(url: String): String {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code()}")
            return resp.body()?.string() ?: throw Exception("空响应")
        }
    }

    private fun parse(json: String, mirror: String): UpdateInfo? {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name", "")
        val versionCode = parseVersionCode(tag)
        // tag 形如 v162 → 用版本号比较；形如 v3.693 → 退回版本名语义比较
        val versionName = tag.removePrefix("v").removePrefix("V")
            .ifBlank { obj.optString("name", tag) }
        val assets = obj.optJSONArray("assets") ?: JSONArray()
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val ct = a.optString("content_type", "")
            if (name.endsWith(".apk", true) || ct.contains("android")) {
                apkUrl = a.optString("browser_download_url", "")
                break
            }
        }
        if (apkUrl.isBlank()) return null
        val proxiedUrl = if (mirror.isBlank()) apkUrl else (mirror + apkUrl)
        val notes = obj.optString("body", "").trim()
        return UpdateInfo(versionCode, versionName, proxiedUrl, notes)
    }

    /** 仅当 tag 整体为纯数字（如 v162）时才作为 versionCode，避免 v3.693 被误解析成 3693 */
    private fun parseVersionCode(tag: String): Int {
        val digits = tag.removePrefix("v").removePrefix("V")
        return if (digits.matches(Regex("^\\d+$"))) digits.toIntOrNull() ?: 0 else 0
    }
}
