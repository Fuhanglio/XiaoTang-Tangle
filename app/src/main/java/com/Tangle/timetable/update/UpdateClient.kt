package com.Tangle.timetable.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 通过 GitHub Releases API 获取最新版本。
 *
 * W6-05 起：**API 查询只走官方直连**（ghproxy 系镜像不代理 api.github.com，见 [API_URLS] 的注释），
 * 下载地址也直接用官方 `browser_download_url`。失败抛异常，由上层归类为 NoNetwork / Error。
 */
object UpdateClient {

    private const val REPO = "Fuhanglio/XiaoTang-Tangle"
    private const val API_PATH = "https://api.github.com/repos/$REPO/releases/latest"

    /**
     * W6-05：API 查询与文件下载是**两类不同的流量**，不能共用同一套镜像前缀。
     *
     * 原实现把镜像无差别拼到 API 域名上：`mirror + API_PATH` 得到
     * `https://ghproxy.net/https://api.github.com/...`。而 ghproxy 系镜像的定位是代理
     * `github.com/.../releases/download` 这类**文件** URL，基本不代理 `api.github.com`
     * （多数返回 404 / 403）→ 5 个候选里前 4 个大概率白跑，还平白拖长一次检查的耗时。
     *
     * 现在拆开：API **只走官方直连**。候选表保留为 List，便于将来追加**确实可用**的 API
     * 端点（例如自建反代或仓库内的静态清单），届时不必再改调用逻辑。
     */
    private val API_URLS = listOf(API_PATH)

    /** W6-05②：候选之间线性退避的基数（毫秒），避免一次瞬时抖动就直接判失败 */
    private const val RETRY_BACKOFF_MS = 300L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 拉取最新 release，返回可安装版本信息；失败抛异常。
     *
     * 注意：`parse` 的 mirror 参数传**空串** —— 下载地址用官方原始 `browser_download_url`，
     * 不拼接任何代理。这与原实现在「官方 API 直连成功」时的实际行为完全一致
     * （那一路径下 mirror 本来就是空串），因此**不构成下载链路的回归**。
     */
    @Throws(Exception::class)
    suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for ((i, url) in API_URLS.withIndex()) {
            try {
                val json = requestJson(url)
                return@withContext parse(json, "") ?: throw Exception("release 中没有可用的 apk 资源")
            } catch (e: Exception) {
                // W6-05③：留住最后一次的**具体**错误原因，别让上层只拿到一句"无法获取更新信息"
                lastError = e
                if (i < API_URLS.lastIndex) delay(RETRY_BACKOFF_MS * (i + 1))
            }
        }
        throw lastError ?: Exception("无法获取更新信息")
    }

    private fun requestJson(url: String): String {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: throw Exception("空响应")
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
        var digest = ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val ct = a.optString("content_type", "")
            if (name.endsWith(".apk", true) || ct.contains("android")) {
                apkUrl = a.optString("browser_download_url", "")
                // W6-01：GitHub Releases 的 asset 从 2025 起带 digest 字段（形如 sha256:abcd…），
                // 拿它做下载后的哈希校验；拿不到就退化成"不做哈希校验"（长度校验仍在）
                digest = a.optString("digest", "")
                break
            }
        }
        if (apkUrl.isBlank()) return null
        // W6-08：下载地址必须来自 GitHub 的 **https** 资源域，否则拒绝。
        // 这是供应链防线的关键一环：apkUrl 直接取自 release JSON 的 browser_download_url，
        // 属**外部输入**；没有这道校验时，一个被篡改的响应（或将来换源时的疏忽）就能把
        // "下载并安装 APK"指向任意 http 主机 —— 而那是本 App 权限最高的一条路径
        // （REQUEST_INSTALL_PACKAGES），也是唯一能真正拿下设备的一条。
        // 校验对象是**内层原始地址**而不是拼了镜像前缀的最终地址：镜像会把 host 换成
        // ghproxy 之类，但被它代理的目标仍然必须是 github 的资源域。
        if (!isTrustedDownloadUrl(apkUrl)) return null
        val proxiedUrl = if (mirror.isBlank()) apkUrl else (mirror + apkUrl)
        val notes = obj.optString("body", "").trim()
        // 只认 sha256 且必须是 64 位十六进制，其它算法/异常格式一律视为不可校验
        val sha256 = if (digest.startsWith("sha256:", true)) {
            digest.substring(7).trim().lowercase().takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }.orEmpty()
        } else ""
        return UpdateInfo(versionCode, versionName, proxiedUrl, notes, sha256)
    }

    /**
     * W6-08：允许的下载域后缀。Release asset 直链只可能落在这些域上
     * （`github.com` 含 `codeload.github.com` 等子域；`githubusercontent.com` 含
     * `objects.githubusercontent.com`；最后一条是 GitHub Releases 历史上用过的 S3 桶）。
     */
    private val TRUSTED_DOWNLOAD_SUFFIXES = listOf(
            "github.com",
            "githubusercontent.com",
            "github-production-release-asset-2e65be.s3.amazonaws.com")

    /**
     * W6-08：下载地址必须是 https，且 host 精确命中（或为其子域）可信下载域。
     *
     * ⚠ 必须用 `host == suffix || host.endsWith("." + suffix)` 而不是裸 `endsWith(suffix)` ——
     * 后者会让 `evilgithub.com` 这类域名通过校验（正是供应链攻击最省事的做法）。
     */
    private fun isTrustedDownloadUrl(url: String): Boolean {
        val u = runCatching { java.net.URL(url) }.getOrNull() ?: return false
        if (!u.protocol.equals("https", ignoreCase = true)) return false
        val host = u.host.lowercase()
        return TRUSTED_DOWNLOAD_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }

    /** 仅当 tag 整体为纯数字（如 v162）时才作为 versionCode，避免 v3.693 被误解析成 3693 */
    private fun parseVersionCode(tag: String): Int {
        val digits = tag.removePrefix("v").removePrefix("V")
        return if (digits.matches(Regex("^\\d+$"))) digits.toIntOrNull() ?: 0 else 0
    }
}
