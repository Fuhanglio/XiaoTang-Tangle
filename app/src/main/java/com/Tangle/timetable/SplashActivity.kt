package com.Tangle.timetable

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.Tangle.timetable.schedule.ScheduleActivity
import com.Tangle.timetable.utils.MigrationUtils
import com.Tangle.timetable.update.PendingUpdateStore
import com.Tangle.timetable.update.UpdateDownloadService
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import splitties.activities.start
import java.io.File
import kotlin.coroutines.resume

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // 启动数据迁移失败也必须继续进主页，不能在协程里抛出未捕获异常导致闪退
            try {
                MigrationUtils.tranOldData(applicationContext)
            } catch (e: Exception) {
                Log.e("SplashActivity", "启动数据迁移异常（已忽略）", e)
            }
            // W6-13③：下载已完成但没装的更新，冷启动时补一次安装入口。
            // 整体静默降级：无记录零 UI 零开销；任何异常都不得拖慢/拖崩冷启动。
            try {
                PendingUpdateStore.cleanPartFiles(applicationContext)
                val pending = PendingUpdateStore.peek(applicationContext)
                if (pending == null) {
                    Log.i("PendingUpdate", "pending: none")
                } else {
                    Log.i("PendingUpdate", "pending: v${pending.versionName} (code=${pending.versionCode}) -> ${pending.apkPath}")
                    val installNow = awaitPendingDialog(pending.versionName)
                    // 无论确认还是取消，记录都清掉（确认后安装由系统接管；取消即放弃这一次提示）
                    PendingUpdateStore.clear(applicationContext)
                    if (installNow) {
                        val err = UpdateDownloadService.launchInstaller(
                                this@SplashActivity, File(pending.apkPath))
                        if (err != null) {
                            Toast.makeText(this@SplashActivity, err, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("PendingUpdate", "冷启动待装检查异常（已忽略）", t)
            }
            try {
                start<ScheduleActivity>()
            } catch (e: Exception) {
                Log.e("SplashActivity", "进入主页失败", e)
            }
            //startActivity<SudaLifeActivity>("type" to "澡堂")
            finish()
        }
    }

    /** 弹一次「上次下载的更新已就绪，现在安装？」；返回用户是否确认。返回键=取消。 */
    private suspend fun awaitPendingDialog(versionName: String): Boolean =
            suspendCancellableCoroutine { cont ->
                val display = if (versionName.isBlank()) "" else "v$versionName"
                val dialog = AlertDialog.Builder(this)
                        .setTitle("更新已就绪")
                        .setMessage("上次下载的更新已就绪（$display），现在安装？")
                        .setPositiveButton("安装") { _, _ -> if (cont.isActive) cont.resume(true) }
                        .setNegativeButton("取消") { _, _ -> if (cont.isActive) cont.resume(false) }
                        .setOnCancelListener { if (cont.isActive) cont.resume(false) }
                        .setCancelable(true)
                        .create()
                cont.invokeOnCancellation { runCatching { dialog.dismiss() } }
                dialog.show()
            }

    override fun onBackPressed() {

    }
}
