package com.Tangle.timetable.utils

import android.content.Context
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
        if (!dataFile.exists() || dataFile.length() < MIN_TRAINEDDATA_SIZE) {
            try {
                tessdata.mkdirs()
                context.assets.open("tessdata/$LANG.traineddata").use { input ->
                    dataFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (t: Throwable) {
                throw OcrUnavailableException("OCR 训练数据释放失败，请重装应用后再试", t)
            }
        }

        // TessBaseAPI 的静态块里会加载 native 库：设备的 CPU 架构恰好没有对应的 .so 时，
        // 这里会抛 UnsatisfiedLinkError / ExceptionInInitializerError。它们都是 Error，
        // 普通 catch(Exception) 拦不住会直接闪退，所以这里按 Throwable 兜住并转成可读异常。
        val api = try {
            TessBaseAPI()
        } catch (t: Throwable) {
            throw OcrUnavailableException("当前设备的 CPU 架构不受支持，无法使用图片识别", t)
        }
        if (!api.init(dataRoot.absolutePath, LANG)) {
            api.recycle()
            throw OcrUnavailableException("OCR 引擎初始化失败，请重装应用后再试", null)
        }
        // 课表里文字是散落在格子里的，用「稀疏文本」模式，让引擎把每一小块都抓出来
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
        tess = api
        return api
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
