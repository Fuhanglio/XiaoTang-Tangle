package androidx.fragment.app

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.LayoutRes
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.google.android.material.card.MaterialCardView
import com.Tangle.timetable.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.dimensions.dip

abstract class BaseDialogFragment : DialogFragment() {

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = lifecycleScope.launch {
        lifecycle.whenStarted(block)
    }

    @get:LayoutRes
    protected abstract val layoutId: Int

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout(context!!.dip(280), ViewGroup.LayoutParams.WRAP_CONTENT)
        val root = inflater.inflate(R.layout.fragment_base_dialog, container, false)
        val cardView = root.findViewById<MaterialCardView>(R.id.base_card_view)
        LayoutInflater.from(context).inflate(layoutId, cardView, true)
        return root
    }

    /**
     * 重写 show()：正常路径走 super.show()（框架内部会维护自身状态），
     * 仅当状态已保存、super.show() 内部 commit() 抛 IllegalStateException 时，
     * 退回 commitAllowingStateLoss() 完成添加。
     * 说明：新版 androidx.fragment 把 DialogFragment 的 mDismissed / mShownByMe 改成了 private，
     * 旧实现直接赋值已不可编译。
     */
    override fun show(manager: FragmentManager, tag: String?) {
        try {
            super.show(manager, tag)
        } catch (e: IllegalStateException) {
            val ft = manager.beginTransaction()
            ft.add(this, tag)
            ft.commitAllowingStateLoss()
        }
    }

}