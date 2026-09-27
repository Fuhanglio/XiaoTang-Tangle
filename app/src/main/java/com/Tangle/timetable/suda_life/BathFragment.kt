package com.Tangle.timetable.suda_life

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseFragment
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class BathFragment : BaseFragment() {

    private lateinit var viewModel: SudaLifeViewModel

    /**
     * W1-05：与 W1-04 同型 —— `BaseFragment.launch` 绑的是 Fragment 生命周期，
     * 挂起返回时视图可能已销毁，写 `tv_male_stay.text` 这类合成属性会抛 IllegalStateException
     * （内部是 `findViewById(...)!!`），被 catch 吞掉。
     * 更糟的是：男浴那条 try 里的写入若抛异常，catch 会走到 error 分支，
     * 而女浴那条仍会正常跑完 —— 页面一半新一半旧，且没有任何一处告诉用户数据没更新。
     * 改为绑 viewLifecycleOwner：视图销毁即取消，挂起返回后不再碰已销毁的视图。
     */
    private fun launchWithView(block: suspend CoroutineScope.() -> Unit): Job =
            viewLifecycleOwner.lifecycleScope.launch(block = block)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // W4-12：`ViewModelProviders` 自 lifecycle 2.5.0 起已被 `ViewModelProvider` 取代
        // （前者 2.2.0 时代产物，依赖已删的 lifecycle-extensions）。调用语义完全一致。
        viewModel = ViewModelProvider(activity!!).get(SudaLifeViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_bath, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshData()
        cv_female.setOnClickListener {
            refreshData(true)
        }
        cv_male.setOnClickListener {
            refreshData(true)
        }
    }

    private fun refreshData(refresh: Boolean = false) {
        launchWithView {
            // W1-05：两次取数用 async 并行（保持原有并发度），但**统一 await 之后再写 UI**。
            // 原实现是两条互不相干的协程，各自 try/catch：任一侧失败时另一侧已经把数字写完了，
            // 用户看到的是"一半新一半旧"，而且男浴那条还会先弹一句「刷新成功」——
            // 向用户报告了一个实际没有发生的成功。
            val male = async { viewModel.getBathData(true) }
            val female = async { viewModel.getBathData(false) }
            try {
                male.await()
                female.await()

                val maleCount = if (viewModel.maleBathData.inNum > viewModel.maleBathData.outNum) {
                    viewModel.maleBathData.inNum - viewModel.maleBathData.outNum
                } else {
                    0
                }
                tv_male_stay.text = maleCount.toString()
                tv_male_rate.text = "拥挤度：${(maleCount / 80f) * 100}%"

                val femaleCount = if (viewModel.femaleBathData.inNum > viewModel.femaleBathData.outNum) {
                    viewModel.femaleBathData.inNum - viewModel.femaleBathData.outNum
                } else {
                    0
                }
                tv_female_stay.text = femaleCount.toString()
                tv_female_rate.text = "拥挤度：${(femaleCount / 90f) * 100}%"

                // 只有两侧都成功、数字都写完了，才有资格说「刷新成功」
                if (refresh) {
                    Toasty.success(activity!!, "刷新成功").show()
                }
            } catch (e: CancellationException) {
                // 协程取消不是业务异常，继续向上抛，不要弹"发生异常"
                throw e
            } catch (e: Exception) {
                if (isAdded && view != null) {
                    Toasty.error(activity!!, "发生异常>_<${e.message}").show()
                }
            }
        }
    }
}
