package com.Tangle.timetable.schedule_import

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import com.Tangle.timetable.SplashActivity
import com.Tangle.timetable.base_view.BaseActivity
import com.Tangle.timetable.utils.Const
import es.dmoral.toasty.Toasty

class LoginWebActivity : BaseActivity() {

    private val viewModel by viewModels<ImportViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent.extras?.getString("import_type")?.let {
            viewModel.importType = it
        }
        intent.extras?.getString("school_name")?.let {
            viewModel.school = it
        }

        val fragment = when (viewModel.importType) {
            "login" -> {
                LoginWebFragment()
            }
            "file" -> {
                FileImportFragment()
            }
            "excel" -> {
                ExcelImportFragment()
            }
            "html" -> {
                HtmlImportFragment()
            }
            "image" -> {
                ImageImportFragment()
            }
            else -> {
                if (viewModel.importType.isNullOrEmpty() || viewModel.school.isNullOrEmpty()) {
                    null
                } else {
                    WebViewLoginFragment.newInstance(intent.getStringExtra("url") ?: "")
                }
            }
        }
        fragment?.let { frag ->
            val transaction = supportFragmentManager.beginTransaction()
            transaction.add(android.R.id.content, frag, viewModel.school)
            transaction.commit()
            if (viewModel.importType != "file") {
                showImportSettingDialog()
            }
        }

        if (fragment == null && intent.action == Intent.ACTION_VIEW) {
            launch {
                viewModel.importId = viewModel.getNewId()
                viewModel.newFlag = true
                val uri = intent.data
                if (uri == null) {
                    Toasty.error(this@LoginWebActivity, "导入参数缺失").show()
                    finish()
                    return@launch
                }
                val scheme = uri.scheme?.toLowerCase()
                if (scheme !in listOf("content", "file", "http", "https")) {
                    Toasty.error(this@LoginWebActivity, "非法的导入来源").show()
                    finish()
                    return@launch
                }
                val path = uri?.path ?: ""
                val type = when {
                    path.contains("wakeup_schedule") -> "file"
                    path.endsWith("csv") -> "csv"
                    path.endsWith("html") -> "html"
                    else -> ""
                }
                if (type.isEmpty()) {
                    Toasty.error(this@LoginWebActivity, "文件的扩展名不对哦>_<", Toast.LENGTH_LONG).show()
                    val intent = Intent(this@LoginWebActivity, SplashActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                    return@launch
                }
                val transaction = supportFragmentManager.beginTransaction()
                when (type) {
                    "file" -> transaction.add(android.R.id.content, FileImportFragment(), null)
                    "csv" -> transaction.add(android.R.id.content, ExcelImportFragment(), null)
                    "html" -> transaction.add(android.R.id.content, HtmlImportFragment(), null)
                }
                transaction.commit()
                if (type == "html") {
                    viewModel.htmlUri = uri
                } else {
                    try {
                        when (type) {
                            "file" -> viewModel.importFromFile(uri)
                            "csv" -> viewModel.importFromExcel(uri)
                        }
                        Toasty.success(this@LoginWebActivity, "导入成功(ﾟ▽ﾟ)/请在右侧栏切换后查看", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@LoginWebActivity, SplashActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Toasty.error(this@LoginWebActivity, "发生异常>_<\n${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showImportSettingDialog() {
        ImportSettingFragment().apply {
            isCancelable = false
        }.show(supportFragmentManager, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            Const.REQUEST_CODE_IMPORT_FILE -> {
                launch {
                    try {
                        viewModel.importFromFile(data?.data)
                        Toasty.success(this@LoginWebActivity, "导入成功(ﾟ▽ﾟ)/请在右侧栏切换后查看", Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK)
                        finish()
                    } catch (e: Exception) {
                        Toasty.error(this@LoginWebActivity, "发生异常>_<\n${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            Const.REQUEST_CODE_IMPORT_CSV -> {
                launch {
                    try {
                        viewModel.importFromExcel(data?.data)
                        Toasty.success(this@LoginWebActivity, "导入成功(ﾟ▽ﾟ)/请在右侧栏切换后查看", Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK)
                        finish()
                    } catch (e: Exception) {
                        Toasty.error(this@LoginWebActivity, "发生异常>_<请确保所有应填的格子不为空\n且没有更改模板的属性\n${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        // W1-02：本 Activity **没有自己的布局**（全文不 setContentView，视图全部来自被 add 到
        // android.R.id.content 的 Fragment）。原来这里用的是 `fab_login` 的 **LoginWebActivity 版**
        // 合成属性 —— 它是生成脚本按"同包同前缀"批量复制出来的，扫的是 Activity 的视图树，
        // 只在"对应 Fragment 恰好已附着"时碰巧命中；换成别的 Fragment 就会抛异常。
        // 已改为走 **LoginWebFragment 自身**的访问器（作用域正确）。
        // （同批已把 `SynthViewsCompat.kt` 里那 23 条 LoginWebActivity.* 属性整段删除）
        //
        // W1-11：补 `isAdded && view != null` 判活 —— 合成属性的 getter 内部是
        // `(view ?: throw IllegalStateException).findViewById(..)!!`，属**抛异常**语义，
        // Kotlin 的 `?.` 挡不住它（getter 在 null 检查之前就抛了）。
        val suda = supportFragmentManager.findFragmentByTag("苏州大学") as? LoginWebFragment
        if (suda != null && suda.isAdded && suda.view != null && suda.fab_login.isExpanded) {
            suda.fab_login.isExpanded = false
        } else {
            super.onBackPressed()
        }
    }

}


