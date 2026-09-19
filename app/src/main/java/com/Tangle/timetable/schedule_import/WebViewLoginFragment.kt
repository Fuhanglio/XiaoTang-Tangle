package com.Tangle.timetable.schedule_import

import android.app.Activity.RESULT_OK
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseFragment
import android.net.Uri
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.Utils
import com.Tangle.timetable.utils.ViewUtils
import com.Tangle.timetable.utils.getPrefer
import es.dmoral.toasty.Toasty
import splitties.activities.start
import splitties.snackbar.longSnack

class WebViewLoginFragment : BaseFragment() {

    private lateinit var url: String
    private val viewModel by activityViewModels<ImportViewModel>()
    private var isRefer = false
    private val hostRegex = Regex("""(http|https)://.*?/""")
    private var tips = "1. 在上方输入教务网址，部分学校需要连接校园网\n2. 登录后点击到个人课表的页面，注意选择自己需要导入的学期\n3. 点击右下角的按钮完成导入\n4. 如果遇到总是提示密码错误或者网页错位等问题，可以取消底栏的「电脑模式」或者调节字体缩放"
    private var zoom = 100
    private var countClick = 0

    // ====== 智能抓取状态 ======
    // 背景：部分教务（如茅台学院新版正方）的课表页默认只渲染「当前周/简表」，
    // 必须先点页面上的「更多」进入完整视图，抓到的数据才齐全。
    // 因此点导入时先尝试展开，再抓源码；找不到入口则与旧版行为一致（直接抓）。
    private var captureAfterLoad = false   // 等待页面加载完成后抓取（点击「更多」触发了跳转时）
    private var captureDone = false        // 防止重复抓取
    private var expandTried = false        // 本次导入是否已尝试过点「更多」（防止跳转后重复点击）
    private var pageProgress = 0           // 当前页面加载进度（0~100）

    /** 站点校验锚点：本次会话首次加载的 URL 的 host（由 startVisit 记录） */
    private var firstLoadHost: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString("url")?.let { url = it }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_web_view_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewUtils.resizeStatusBar(context!!.applicationContext, view.findViewById(R.id.v_status))

        if (url != "") {
            et_url.setText(url)
            startVisit()
        } else {
            val url = context!!.getPrefer().getString(Const.KEY_SCHOOL_URL, "")
            if (url != "") {
                et_url.setText(url)
            } else {
                et_url.setText("https://www.baidu.com")
            }
            startVisit()
        }

        if (viewModel.importType == "apply") {
            tips = "1. 在上方输入教务网址，部分学校需要连接校园网\n2. 登录后点击到个人课表或者相关的页面\n3. 点击右下角的按钮抓取源码，并上传到服务器"
        }

        if (viewModel.school == "强智教务") {
            cg_qz.visibility = View.VISIBLE
            chip_qz1.isChecked = true
        } else {
            cg_qz.visibility = View.GONE
        }

        if (viewModel.school == "正方教务") {
            cg_zf.visibility = View.VISIBLE
            chip_zf1.isChecked = true
            tips = "1. 在上方输入教务网址，部分学校需要连接校园网\n2. 登录后点击到「个人课表」的页面，注意不是「班级课表」！注意选择自己需要导入的学期。正方教务目前仅支持个人课表的导入\n3. 点击右下角的按钮完成导入\n" +
                    "4. 如果遇到总是提示密码错误或者网页错位等问题，可以取消底栏的「电脑模式」或者调节字体缩放"
        } else {
            cg_zf.visibility = View.GONE
        }

        if (viewModel.importType == Common.TYPE_HNUST) {
            cg_old_qz.visibility = View.VISIBLE
            chip_old_qz2.isChecked = true
            viewModel.oldQzType = 1
        } else {
            cg_old_qz.visibility = View.GONE
        }

        MaterialAlertDialogBuilder(requireContext())
                .setTitle("注意事项")
                .setMessage(tips)
                .setPositiveButton("我知道啦", null)
                .setNeutralButton("如何正确选择教务？") { _, _ ->
                    Utils.openUrl(activity!!, "https://support.qq.com/embed/97617/faqs/59901")
                }
                .setCancelable(false)
                .show()

        wv_course.settings.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wv_course.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        wv_course.addJavascriptInterface(InJavaScriptLocalObj(), "local_obj")
        wv_course.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 智能抓取：点击「更多」或等待加载时，等新页面加载完再走「展开 + 抓取」
                if (captureAfterLoad && !captureDone) {
                    captureAfterLoad = false
                    wv_course.postDelayed({ expandAndCapture() }, 1300L)
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel() // 默认拒绝所有证书有风险的连接
                activity?.let { act ->
                    MaterialAlertDialogBuilder(act)
                            .setTitle("安全提醒")
                            .setMessage("该网站证书存在风险，可能被窃听，是否仍要继续？")
                            .setPositiveButton("继续") { _, _ ->
                                handler.proceed()
                            }
                            .setNegativeButton("取消") { _, _ ->
                                handler.cancel()
                            }
                            .setCancelable(false)
                            .show()
                }
            }

        }
        wv_course.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                pageProgress = newProgress
                if (newProgress == 100) {
                    pb_load.progress = newProgress
                    pb_load.visibility = View.GONE
                    // Toasty.info(activity!!, wv_course.url, Toast.LENGTH_LONG).show()
                } else {
                    pb_load.progress = newProgress * 5
                    pb_load.visibility = View.VISIBLE
                }
            }
        }
        // 设置自适应屏幕，两者合用
        wv_course.settings.useWideViewPort = true //将图片调整到适合WebView的大小
        wv_course.settings.loadWithOverviewMode = true // 缩放至屏幕的大小
        // 缩放操作
        wv_course.settings.setSupportZoom(true) //支持缩放，默认为true。是下面那个的前提。
        wv_course.settings.builtInZoomControls = true //设置内置的缩放控件。若为false，则该WebView不可缩放
        wv_course.settings.displayZoomControls = false //隐藏原生的缩放控件wvCourse.settings
        wv_course.settings.javaScriptCanOpenWindowsAutomatically = true
        wv_course.settings.domStorageEnabled = true
        wv_course.settings.userAgentString = wv_course.settings.userAgentString.replace("Mobile", "eliboM").replace("Android", "diordnA")
        wv_course.settings.textZoom = 100
        initEvent()
    }

    private fun initEvent() {

        chip_mode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                wv_course.settings.userAgentString = wv_course.settings.userAgentString.replace("Mobile", "eliboM").replace("Android", "diordnA")
            } else {
                wv_course.settings.userAgentString = wv_course.settings.userAgentString.replace("eliboM", "Mobile").replace("diordnA", "Android")
            }
            wv_course.reload()
        }

        chip_zoom.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("设置缩放")
                    .setView(R.layout.dialog_edit_text)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.sure, null)
                    .create()
            dialog.show()
            val inputLayout = dialog.findViewById<TextInputLayout>(R.id.text_input_layout)
            val editText = dialog.findViewById<TextInputEditText>(R.id.edit_text)
            inputLayout?.helperText = "范围 10 ~ 200"
            inputLayout?.suffixText = "%"
            editText?.inputType = InputType.TYPE_CLASS_NUMBER
            val valueStr = zoom.toString()
            editText?.setText(valueStr)
            editText?.setSelection(valueStr.length)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = editText?.text
                if (value.isNullOrBlank()) {
                    inputLayout?.error = "数值不能为空哦>_<"
                    return@setOnClickListener
                }
                val valueInt = try {
                    value.toString().toInt()
                } catch (e: Exception) {
                    inputLayout?.error = "输入异常>_<"
                    return@setOnClickListener
                }
                if (valueInt < 10 || valueInt > 200) {
                    inputLayout?.error = "注意范围 10 ~ 200"
                    return@setOnClickListener
                }
                zoom = valueInt
                wv_course.settings.textZoom = zoom
                chip_zoom.text = "文字缩放 $zoom%"
                wv_course.reload()
                dialog.dismiss()
            }
        }

        var qzChipId = R.id.chip_qz1
        cg_qz.setOnCheckedChangeListener { chipGroup, id ->
            when (id) {
                R.id.chip_qz1 -> {
                    qzChipId = id
                    viewModel.qzType = 0
                }
                R.id.chip_qz2 -> {
                    qzChipId = id
                    viewModel.qzType = 1
                }
                R.id.chip_qz3 -> {
                    qzChipId = id
                    viewModel.qzType = 2
                }
                R.id.chip_qz4 -> {
                    qzChipId = id
                    viewModel.qzType = 3
                }
                else -> {
                    chipGroup.findViewById<Chip>(qzChipId).isChecked = true
                }
            }
        }

        var zfChipId = R.id.chip_zf1
        cg_zf.setOnCheckedChangeListener { chipGroup, id ->
            when (id) {
                R.id.chip_zf1 -> {
                    zfChipId = id
                    viewModel.zfType = 0
                }
                R.id.chip_zf2 -> {
                    zfChipId = id
                    viewModel.zfType = 1
                }
                else -> {
                    chipGroup.findViewById<Chip>(zfChipId).isChecked = true
                }
            }
        }

        var oldQZChipId = R.id.chip_old_qz2
        cg_old_qz.setOnCheckedChangeListener { chipGroup, id ->
            when (id) {
                R.id.chip_old_qz1 -> {
                    oldQZChipId = id
                    viewModel.oldQzType = 0
                }
                R.id.chip_old_qz2 -> {
                    oldQZChipId = id
                    viewModel.oldQzType = 1
                }
                else -> {
                    chipGroup.findViewById<Chip>(oldQZChipId).isChecked = true
                }
            }
        }

        tv_go.setOnClickListener {
            startVisit()
        }

        et_url.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                startVisit()
            }
            return@setOnEditorActionListener false
        }

        val js = "javascript:var ifrs=document.getElementsByTagName(\"iframe\");" +
                "var iframeContent=\"\";" +
                "for(var i=0;i<ifrs.length;i++){" +
                "iframeContent=iframeContent+ifrs[i].contentDocument.body.parentElement.outerHTML;" +
                "}\n" +
                "var frs=document.getElementsByTagName(\"frame\");" +
                "var frameContent=\"\";" +
                "for(var i=0;i<frs.length;i++){" +
                "frameContent=frameContent+frs[i].contentDocument.body.parentElement.outerHTML;" +
                "}\n" +
                "window.local_obj.showSource(document.getElementsByTagName('html')[0].innerHTML + iframeContent + frameContent);"

        fab_import.setOnClickListener {
            if (viewModel.importType == Common.TYPE_HNUST) {
                if (!isRefer) {
                    val referUrl = when (viewModel.school) {
                        "湖南科技大学" -> "http://kdjw.hnust.cn/kdjw/tkglAction.do?method=goListKbByXs&istsxx=no"
                        "湖南科技大学潇湘学院" -> "http://xxjw.hnust.cn:8080/xxjw/tkglAction.do?method=goListKbByXs&istsxx=no"
                        else -> getHostUrl() + "tkglAction.do?method=goListKbByXs&istsxx=no"
                    }
                    wv_course.loadUrl(referUrl)
                    it.longSnack("请在看到网页加载完成后，再点一次右下角按钮")
                    isRefer = true
                } else {
                    wv_course.loadUrl(js)
                }
            } else if (viewModel.importType == Common.TYPE_CF) {
                if (!isRefer) {
                    val referUrl = getHostUrl() + "xsgrkbcx!getXsgrbkList.action"
                    wv_course.loadUrl(referUrl)
                    it.longSnack("请重新选择一下学期再点按钮导入，要记得选择全部周，记得点查询按钮")
                    isRefer = true
                } else {
                    wv_course.loadUrl(js)
                }
            } else if (viewModel.importType == Common.TYPE_URP || viewModel.isUrp) {
                if (!isRefer) {
                    val referUrl = getHostUrl() + "xkAction.do?actionType=6"
                    wv_course.loadUrl(referUrl)
                    it.longSnack("请在看到网页加载完成后，再点一次右下角按钮")
                    isRefer = true
                } else {
                    wv_course.loadUrl(js)
                }
            } else if (viewModel.importType == Common.TYPE_URP_NEW) {
                if (!isRefer) {
                    val referUrl = getHostUrl() + "student/courseSelect/thisSemesterCurriculum/callback"
                    wv_course.loadUrl(referUrl)
                    it.longSnack("请在看到网页加载完成后，再点一次右下角按钮")
                    isRefer = true
                } else {
                    wv_course.loadUrl("javascript:window.local_obj.showSource(document.getElementsByTagName('html')[0].innerText);")
                }
            } else if (viewModel.importType == Common.TYPE_JNU) {
                if (countClick == 0) {
                    val referUrl = getHostUrl() + "Secure/TeachingPlan/wfrm_Prt_Report.aspx"
                    wv_course.loadUrl(referUrl)
                    it.longSnack("请在看到网页加载完成后，再点一次右下角按钮")
                    countClick++
                } else if(countClick == 1){
//                    val jnujs = "javascript:window.local_obj.jump2DespairingUrl(document.getElementById(\"ReportFrameReportViewer1\").src);"
                    val jnujs = "javascript:window.location.href = document.getElementById(\"ReportFrameReportViewer1\").src;"
                    wv_course.loadUrl(jnujs)
//                    wv_course.loadUrl(despairingUrl)
                    it.longSnack("请再点一次右下角按钮")
                    countClick++
                }else{
                    wv_course.loadUrl(js)
                    countClick = 0
                }
            } else {
                // 通用路径（含新版正方 TYPE_ZF_NEW，如茅台学院）：
                // 先尝试展开「更多」进入完整课表，再抓源码；失败则退化为直接抓取。
                smartImport()
            }
        }

        btn_back.setOnClickListener {
            if (wv_course.canGoBack()) {
                wv_course.goBack()
            }
        }
    }

    // ================== 智能抓取 ==================

    /**
     * 智能导入：先尝试点击页面上的「更多」等入口展开完整课表，再抓源码。
     * 找不到入口 / 非课表页时，行为与旧版一致（直接抓当前页）。
     */
    private fun smartImport() {
        captureDone = false
        captureAfterLoad = false
        expandTried = false
        fab_import.isEnabled = false
        Toasty.info(activity!!, "正在抓取页面源码…").show()
        // 按钮复位保险：无论走哪条抓取分支（含失败），12 秒后必然恢复可点，
        // 否则解析失败一次后本页永远无法重试
        wv_course.postDelayed({
            try {
                if (isAdded) fab_import.isEnabled = true
            } catch (t: Throwable) {
            }
        }, 12000L)

        if (pageProgress < 100) {
            // 页面还在加载中（例如用户刚点了「更多」或切换了学期）：
            // 等这次加载完成后走「展开 + 抓取」，6 秒保险防卡死
            captureAfterLoad = true
            wv_course.postDelayed({ ensureCapture() }, 6000L)
        } else {
            expandAndCapture()
        }
    }

    /** 尝试展开完整课表（点「更多」），随后抓取；已尝试过则直接抓。 */
    private fun expandAndCapture() {
        if (captureDone) return
        if (expandTried) {
            // 已经点过一次「更多」（当前页是展开后的结果）→ 直接抓，避免反复点造成死循环
            captureHtml()
            return
        }
        expandTried = true
        wv_course.evaluateJavascript(JS_EXPAND_FULL_SCHEDULE) { result ->
            Log.d("WebViewLogin", "expand-full-schedule result=$result")
            if (result?.contains("xt:clicked") == true) {
                // 已点击「更多」：可能同页展开（DOM 直接变），也可能触发页面跳转。
                // a) 若跳转：onPageFinished 里再延迟调用 ensureCapture；
                // b) 兜底：3.5 秒后仍未抓取，直接抓当前页面。
                captureAfterLoad = true
                wv_course.postDelayed({ ensureCapture() }, 3500L)
                // 按钮复位保险：万一抓取链路异常，用户还能重试（正常成功时页面会自行关闭）
                wv_course.postDelayed({
                    try {
                        if (isAdded) fab_import.isEnabled = true
                    } catch (t: Throwable) {
                    }
                }, 12000L)
            } else {
                captureHtml()
            }
        }
    }

    /** 兜底抓取：只要还没抓过，就抓。 */
    private fun ensureCapture() {
        if (!captureDone) {
            captureAfterLoad = false
            captureHtml()
        }
    }

    /** 抓取当前页面 DOM（含 iframe/frame），回调给本地桥 showSource。 */
    private fun captureHtml() {
        if (captureDone) return
        captureDone = true
        val js = "(function(){" +
                "var out='';" +
                "try{out+=document.getElementsByTagName('html')[0].innerHTML;}catch(e){}" +
                "try{" +
                "var ifrs=document.getElementsByTagName('iframe');" +
                "for(var i=0;i<ifrs.length;i++){try{out+=ifrs[i].contentDocument.body.parentElement.outerHTML;}catch(e){}}" +
                "var frs=document.getElementsByTagName('frame');" +
                "for(var i=0;i<frs.length;i++){try{out+=frs[i].contentDocument.body.parentElement.outerHTML;}catch(e){}}" +
                "}catch(e){}" +
                "try{window.local_obj.showSource(out);}catch(e){}" +
                "return ''+out.length;" +
                "})()"
        wv_course.evaluateJavascript(js, null)
    }

    private fun getHostUrl(): String {
        val rawUrl = wv_course.url ?: et_url.text?.toString() ?: ""
        if (rawUrl.isEmpty()) return ""
        val withSlash = if (rawUrl.endsWith('/')) rawUrl else rawUrl + "/"
        return hostRegex.find(withSlash)?.value ?: withSlash
    }

    private fun startVisit() {
        wv_course.visibility = View.VISIBLE
        ll_error.visibility = View.GONE
        val url = if (et_url.text.toString().startsWith("http://") || et_url.text.toString().startsWith("https://"))
            et_url.text.toString() else "http://" + et_url.text.toString()
        firstLoadHost = try { Uri.parse(url).host } catch (t: Throwable) { null }
        if (URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url)) {
            wv_course.loadUrl(url)
            context!!.getPrefer().edit {
                putString(Const.KEY_SCHOOL_URL, url)
            }
        } else {
            Toasty.error(context!!, "请输入正确的网址╭(╯^╰)╮").show()
        }
    }

    /**
     * 取「可注册主域名」：把常见的二级公共后缀（edu.cn / com.cn 等）也算进去，
     * 使同一学校的兄弟子域（jwxt.mtxy.edu.cn 与 cse.mtxy.edu.cn）互相认定为同站点。
     */
    private fun registrableDomain(host: String): String {
        if (host.isEmpty()) return host
        if (host.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) return host
        val parts = host.split(".")
        if (parts.size <= 2) return host
        val twoLevelSuffixes = setOf("edu.cn", "com.cn", "net.cn", "org.cn", "gov.cn", "ac.cn")
        val last2 = parts.takeLast(2).joinToString(".")
        return if (twoLevelSuffixes.contains(last2)) parts.takeLast(3).joinToString(".") else last2
    }

    /** 当前页 host 是否属于教务站点白名单（完全相同 / 互为子域 / 同一主域名） */
    private fun hostAllowed(current: String, allowed: Collection<String>): Boolean {
        if (current.isEmpty()) return false
        val cur = current.lowercase()
        val curDomain = registrableDomain(cur)
        for (a in allowed) {
            if (a.isEmpty()) continue
            val h = a.lowercase()
            if (cur == h) return true
            if (cur.endsWith(".$h") || h.endsWith(".$cur")) return true
            if (curDomain == registrableDomain(h)) return true
        }
        return false
    }

    /** 真正的导入动作（从原 showSource 里原样抽出，便于「仍要导入」分支复用） */
    private fun doImport(html: String) {
        launch {
            try {
                val result = viewModel.importSchedule(html)
                Toasty.success(activity!!,
                        "成功导入 $result 门课程(ﾟ▽ﾟ)/\n请在右侧栏切换后查看\n（页面源码已存至 Download/wakeup_import_ok_*.html，数据不全时可发给开发者）").show()
                activity!!.setResult(RESULT_OK)
                activity!!.finish()
            } catch (e: Exception) {
                Toasty.error(activity!!,
                        "导入失败>_<\n${e.message}", Toast.LENGTH_LONG).show()
                // 失败后恢复导入按钮，让用户可以重试
                try {
                    if (isAdded) fab_import.isEnabled = true
                } catch (t: Throwable) {
                }
            }
        }
    }

    internal inner class InJavaScriptLocalObj {
        @JavascriptInterface
        fun showSource(html: String) {
            // JS 桥对所有加载的页面都会生效，因此要确认当前页属于教务站点，
            // 避免 WebView 里的第三方页面静默触发「覆盖导入」污染课表数据。
            // 修复说明：原实现取 schoolInfo[1]（其实字段顺序是 sortKey/name/url/type，索引 1 是校名），
            // 且 schoolInfo 在全工程从未被赋值，导致白名单只剩「已保存的学校 URL」一条来源、
            // 页面一跳转或换子域就误判为「非教务站点」而拒绝导入。
            val currentHost = try { Uri.parse(wv_course.url ?: "").host ?: "" } catch (t: Throwable) { "" }
            val allowedHosts = LinkedHashSet<String>()
            firstLoadHost?.let { allowedHosts.add(it) }
            try {
                context?.getPrefer()?.getString(Const.KEY_SCHOOL_URL, null)?.let {
                    Uri.parse(it).host?.let { h -> allowedHosts.add(h) }
                }
            } catch (t: Throwable) {}
            // SchoolInfo(sortKey, name, url, type)，URL 在索引 2
            viewModel.schoolInfo.getOrNull(2)?.let {
                try { Uri.parse(it).host?.let { h -> allowedHosts.add(h) } } catch (t: Throwable) {}
            }
            val hostOk = hostAllowed(currentHost, allowedHosts)
            launch {
                if (allowedHosts.isNotEmpty() && !hostOk) {
                    // 兜底：域名对不上时不硬拒绝，交给用户确认（并把实际域名显示出来，便于定位）
                    val act = activity
                    if (act == null || act.isFinishing) {
                        return@launch
                    }
                    MaterialAlertDialogBuilder(act)
                            .setTitle("站点域名不一致")
                            .setMessage("当前页面域名：" + currentHost.ifEmpty { "(未知)" } +
                                    "\n教务站点域名：" + allowedHosts.joinToString("、") +
                                    "\n\n可能是学校更换了域名，或页面跳转到了别的站点。如果确认这就是你的教务页面，可以继续导入。")
                            .setPositiveButton("仍要导入") { _, _ -> doImport(html) }
                            .setNegativeButton("取消", null)
                            .setCancelable(false)
                            .show()
                } else {
                    doImport(html)
                }
            }
        }
    }

    override fun onDestroyView() {
        wv_course?.webChromeClient = null
        wv_course?.clearCache(true)
        wv_course?.clearHistory()
        wv_course?.removeAllViews()
        wv_course?.destroy()
        super.onDestroyView()
    }

    companion object {
        @JvmStatic
        fun newInstance(url: String = "") =
                WebViewLoginFragment().apply {
                    arguments = Bundle().apply {
                        putString("url", url)
                    }
                }

        /**
         * 「展开完整课表」探测脚本。
         *
         * 在页面中寻找文本为「更多 / 更多» / 全部课表 …」的可见元素并点击。
         * 仅当页面看起来是课表页（含 courseBox 或"星期/节"字样）时才动手，避免误点。
         * 优先点 <a>/<button>、带 onclick、子元素少的节点（最接近真正按钮的那个）。
         *
         * 返回："xt:clicked" 已点击 / "xt:notfound" 无此入口 / "xt:notschedule" 非课表页 / "xt:err:..."
         */
        private const val JS_EXPAND_FULL_SCHEDULE = """(function(){
  try {
    var bodyText = document.body ? (document.body.innerText || document.body.textContent || '') : '';
    var hasBox = document.querySelectorAll('div[class*=courseBox]').length > 0;
    var looksLikeSchedule = hasBox || (bodyText.indexOf('星期') >= 0 && bodyText.indexOf('节') >= 0);
    if (!looksLikeSchedule) { return 'xt:notschedule'; }
    var norm = function(s){ return (s || '').replace(/\s+/g, ''); };
    var targets = ['更多','更多»','更多>>','更多>','展开更多','更多选项','全部课表','完整课表'];
    var nodes = document.querySelectorAll('a,button,span,div,li,i,p,label,strong,em');
    var best = null, bestScore = -1;
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var t = norm(el.textContent);
      if (!t || t.length > 6) continue;
      if (targets.indexOf(t) < 0) continue;
      if (el.offsetWidth === 0 && el.offsetHeight === 0) continue;
      var score = 0;
      var tag = el.tagName;
      if (tag === 'A' || tag === 'BUTTON') score += 4;
      if (el.onclick) score += 2;
      if (el.children.length === 0) score += 2;
      score -= el.children.length;
      if (score > bestScore) { bestScore = score; best = el; }
    }
    if (best) { best.click(); return 'xt:clicked'; }
    return 'xt:notfound';
  } catch (e) { return 'xt:err:' + e.message; }
})()"""
    }
}

