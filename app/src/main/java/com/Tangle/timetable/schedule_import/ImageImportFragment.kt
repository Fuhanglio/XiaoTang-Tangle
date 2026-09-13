package com.Tangle.timetable.schedule_import

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseFragment
import com.Tangle.timetable.schedule_import.bean.Course
import com.Tangle.timetable.schedule_import.image.ImageTableRecognizer
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.TessOcrUtils
import com.Tangle.timetable.utils.ViewUtils
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.fragment_image_import.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 图片导入课表。
 *
 * 流程：选图 → 本地 OCR（离线，不联网）→ 还原课表结构 → 列出识别结果 → 确认写库。
 * 识别结果先给用户看一眼再决定，避免自动写错数据。
 */
class ImageImportFragment : BaseFragment() {

    private val viewModel by activityViewModels<ImportViewModel>()

    private var recognized: List<Course> = arrayListOf()
    private var maxWeek = 20
    private var maxNode = 12
    private var busy = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_image_import, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewUtils.resizeStatusBar(context!!.applicationContext, v_status)

        ib_back.setOnClickListener { activity!!.finish() }
        tv_pick.setOnClickListener { pickImage() }
        tv_import.setOnClickListener { doImport() }

        tv_status.text = "选一张课表截图（建议保留顶部的「周一~周日」和左侧的节次列）"
    }

    /* ------------------------------------------------------------------ */

    private fun pickImage() {
        if (busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, Const.REQUEST_CODE_IMPORT_IMAGE)
        } catch (e: Exception) {
            e.printStackTrace()
            Toasty.error(context!!, "没有找到可用的图片选择器", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != Const.REQUEST_CODE_IMPORT_IMAGE) return
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        startRecognize(uri)
    }

    private fun startRecognize(uri: Uri) {
        busy = true
        recognized = arrayListOf()
        tv_import.visibility = View.GONE
        tv_result.visibility = View.GONE
        tv_status.text = "正在识别，请稍候…（本机离线识别，不会上传图片）"
        tv_pick.isEnabled = false

        launch {
            try {
                // 先用课表的实际设置作兜底（解析不到周次/节次时的默认值）
                viewModel.getImportTable()?.let { table ->
                    if (table.maxWeek in 1..40) maxWeek = table.maxWeek
                    if (table.nodes in 4..30) maxNode = table.nodes
                }

                val bitmap = loadBitmap(uri)
                if (bitmap == null) {
                    tv_status.text = "图片读取失败，请换一张试试"
                    return@launch
                }
                iv_preview.setImageBitmap(bitmap)
                iv_preview.visibility = View.VISIBLE

                val words = withContext(Dispatchers.Default) {
                    TessOcrUtils.recognize(context!!, bitmap)
                }
                val result = ImageTableRecognizer.recognize(
                        words, bitmap.width, bitmap.height, maxWeek, maxNode)
                recognized = result.courses
                tv_status.text = result.message
                if (recognized.isNotEmpty()) {
                    renderResult()
                    tv_result.visibility = View.VISIBLE
                    tv_import.visibility = View.VISIBLE
                    tv_pick.text = "换一张图片"
                }
            } catch (e: Exception) {
                tv_status.text = "识别失败：${e.message ?: "未知错误"}"
            } catch (t: Throwable) {
                // native 层的问题（缺 .so、内存不足等）是 Error 不是 Exception，单独兜一层
                tv_status.text = "识别失败：设备无法加载识别引擎，请换用其它导入方式"
            } finally {
                busy = false
                tv_pick.isEnabled = true
            }
        }
    }

    /** 解码图片：先用 inJustDecodeBounds 读尺寸，再采样到最长边 1800px 以内（精度与速度的平衡点）。 */
    private suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context!!.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext null

            var sample = 1
            while (max(w, h) / sample > 1800) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context!!.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            null
        }
    }

    /* ------------------------------------------------------------------ */

    /**
     * 列识别结果：每门课两行。
     * 第一行是「序号 + 课名」，课名过长会截断加省略号；第二行缩进放星期 / 节次 / 周次 / 教室。
     * 之前所有信息挤在一行，课名一长整行就被撑爆、后面的信息被挤到看不见。
     */
    private fun renderResult() {
        val sb = StringBuilder()
        recognized.forEachIndexed { index, course ->
            sb.append(index + 1).append(". ").append(shortName(course.name)).append('\n')
            sb.append("      ").append(dayName(course.day))
            sb.append(" 第").append(course.startNode).append('-').append(course.endNode).append("节")
            sb.append("  ").append(weekText(course))
            if (course.room.isNotEmpty()) sb.append("  @").append(course.room)
            if (index != recognized.size - 1) sb.append('\n')
        }
        tv_result.text = sb.toString()
    }

    /** 预览列表里的课名限长，超出用省略号；避免一行被超长课名撑爆 */
    private fun shortName(name: String): String {
        val limit = 14
        return if (name.length <= limit) name else name.substring(0, limit) + "…"
    }

    private fun dayName(day: Int): String {
        val names = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        return if (day in 1..7) names[day - 1] else "周$day"
    }

    private fun weekText(course: Course): String {
        val sb = StringBuilder()
        sb.append(course.startWeek).append('-').append(course.endWeek).append("周")
        when (course.type) {
            1 -> sb.append("(单)")
            2 -> sb.append("(双)")
        }
        return sb.toString()
    }

    private fun doImport() {
        if (busy || recognized.isEmpty()) return
        busy = true
        tv_import.isEnabled = false
        tv_status.text = "正在写入课表…"
        launch {
            try {
                val count = viewModel.importFromCourses(recognized)
                Toasty.success(context!!, "导入成功，共 $count 门课程(ﾟ▽ﾟ)/", Toast.LENGTH_LONG).show()
                activity?.setResult(Activity.RESULT_OK)
                activity?.finish()
            } catch (e: Exception) {
                tv_status.text = "导入失败：${e.message ?: "未知错误"}"
                tv_import.isEnabled = true
                busy = false
            }
        }
    }

}
