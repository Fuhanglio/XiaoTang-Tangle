package com.Tangle.timetable.utils

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * 本地离线 OCR 封装（Tesseract 5 + 简体中文训练数据）。
 *
 * - 训练数据放在 assets/tessdata/chi_sim.traineddata，首次使用时释放到应用私有目录；
 * - 全部识别在本机完成：不联网、不上传图片；
 * - 引擎常驻单例（加载模型要 1~2 秒，连续识别多张图时复用更划算）。
 *
 * 产出「词 + 坐标」列表，交给 ImageTableRecognizer 还原课表结构。
 */
object TessOcrUtils {

    private const val LANG = "chi_sim"
    private const val MIN_TRAINEDDATA_SIZE = 100_000L

    /** 引擎初始化失败（含设备不受支持）时抛出的异常，调用方据此给用户提示 */
    class OcrUnavailableException(message: String, cause: Throwable?) : Exception(message, cause)

    @Volatile
    private var tess: TessBaseAPI? = null

    /** OCR 结果里的一个词：文本 + 在识别图上的包围盒 */
    data class Word(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        val boxWidth: Int get() = right - left
        val boxHeight: Int get() = bottom - top
    }

    /**
     * 初始化引擎：释放训练数据 + 加载模型。必须在后台线程调用。
     * 初始化一次后常驻复用。
     */
    @Synchronized
    fun ensureInit(context: Context): TessBaseAPI {
        tess?.let { return it }

        val dataRoot = File(context.filesDir, "tesseract")
        val tessdata = File(dataRoot, "tessdata")
        val dataFile = File(tessdata, "$LANG.traineddata")
        // W4-06：原来的判据只有 `length() < 100_000` —— 拷贝到一半进程被杀会留下
        // **>100KB 的截断文件**，旧判据认为"数据完整"，之后每次 init 都失败，
        // 文案还让用户"重装应用"，没有任何自愈路径。现在：
        // ① 先写 .tmp 并校验长度，通过才 renameTo 原子替换（见 releaseTrainedData）；
        // ② init 失败时删掉数据文件、重新释放一次再重试，失败才报错。
        if (!dataFile.exists() || dataFile.length() < MIN_TRAINEDDATA_SIZE) {
            releaseTrainedData(context, tessdata, dataFile)
        }

        // TessBaseAPI 的静态块里会加载 native 库：设备的 CPU 架构恰好没有对应的 .so 时，
        // 这里会抛 UnsatisfiedLinkError / ExceptionInInitializerError。它们都是 Error，
        // 普通 catch(Exception) 拦不住会直接闪退，所以这里按 Throwable 兜住并转成可读异常。
        var api = try {
            TessBaseAPI()
        } catch (t: Throwable) {
            throw OcrUnavailableException("当前设备的 CPU 架构不受支持，无法使用图片识别", t)
        }
        if (!api.init(dataRoot.absolutePath, LANG)) {
            // W4-06 的自愈路径：init 失败最常见的原因就是数据文件损坏/截断。
            // 旧实现直接报错并让用户重装应用；这里删掉数据文件重新释放一次再试。
            api.recycle()
            dataFile.delete()
            releaseTrainedData(context, tessdata, dataFile)
            api = try {
                TessBaseAPI()
            } catch (t: Throwable) {
                throw OcrUnavailableException("当前设备的 CPU 架构不受支持，无法使用图片识别", t)
            }
            if (!api.init(dataRoot.absolutePath, LANG)) {
                api.recycle()
                throw OcrUnavailableException("OCR 引擎初始化失败（训练数据已重试释放仍不可用），请反馈日志", null)
            }
        }
        // 课表里文字是散落在格子里的，用「稀疏文本」模式，让引擎把每一小块都抓出来
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
        tess = api
        return api
    }

    /**
     * W4-06：把 assets 里的训练数据**原子地**释放到私有目录。
     *
     * 步骤：拷到 `chi_sim.traineddata.tmp` 并统计字节数 → 长度校验（≥ 下限；能从 assets
     * 读到声明长度时还要求完全相等，用来识别"拷到一半"）→ `renameTo` 原子替换正式文件。
     * 任一步失败都清掉 .tmp 并抛错，**绝不会**在正式文件名下留下半截数据。
     */
    private fun releaseTrainedData(context: Context, tessdata: File, dataFile: File) {
        val assetPath = "tessdata/$LANG.traineddata"
        val tmp = File(tessdata, "$LANG.traineddata.tmp")
        val copied = try {
            tessdata.mkdirs()
            context.assets.open(assetPath, AssetManager.ACCESS_STREAMING).use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        total += n
                    }
                    total
                }
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw OcrUnavailableException("OCR 训练数据释放失败，请重试（首次使用需解压约 30MB 数据）", t)
        }

        // 声明长度：assets 未压缩时能拿到（aapt 对 .traineddata 通常不压缩），拿不到就只做下限校验
        val declared = runCatching {
            context.assets.openFd(assetPath).use { it.length }
        }.getOrDefault(-1L)

        val complete = copied >= MIN_TRAINEDDATA_SIZE && (declared <= 0 || copied == declared)
        if (!complete) {
            tmp.delete()
            throw OcrUnavailableException(
                    "OCR 训练数据释放不完整（$copied/$declared 字节），请重试", null)
        }

        // 同一目录内 renameTo 是原子操作：要么还是旧文件，要么就是完整的新文件
        if (dataFile.exists()) dataFile.delete()
        if (!tmp.renameTo(dataFile)) {
            tmp.delete()
            throw OcrUnavailableException("OCR 训练数据写入失败，请重试", null)
        }
    }

    /**
     * 识别一张图，返回词列表（含坐标）。必须在后台线程调用。
     * 第一次调用会顺带完成引擎初始化。
     */
    @Synchronized
    fun recognize(context: Context, bitmap: Bitmap): List<Word> {
        val api = ensureInit(context)
        val words = ArrayList<Word>(256)

        api.setImage(bitmap)
        // 先触发一次识别；结果从下面的迭代器里按词取，这里要的只是「把识别跑完」
        api.getUTF8Text()

        val iterator = api.resultIterator ?: return words
        try {
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                val box = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                if (box != null && box.size >= 4 && !text.isNullOrBlank()) {
                    words.add(Word(text.trim(), box[0], box[1], box[2], box[3]))
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        } finally {
            iterator.delete()
        }
        return words
    }

    /** 释放引擎（一般不需要，除非内存吃紧；释放后下次识别会重新初始化） */
    @Synchronized
    fun release() {
        tess?.recycle()
        tess = null
    }
}
