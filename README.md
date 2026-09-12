# 小唐 Tangle

一个 Android 课表应用，基于开源的 WakeUp课程表二次开发，改造重点放在**桌面小部件**和**课表交互**上。

### 这个版本改了什么

**当前版本：v121 / 3.652**（2026-09-12）

| 方向 | 内容 |
| --- | --- |
| 桌面小部件 | 今日课程卡、周课表卡、下一节课卡三个部件全部重做：去掉位图方案、高度自适应、玻璃卡外观、空态短句 |
| 刷新机制 | 修掉「小卡片更新不了课表」的根因，刷新不再依赖数据库登记，改按系统 `appWidgetIds` 走 |
| 交互增强 | 课表网格内长按拖拽移动课程（可撤销）、周课表卡右上角加课入口、同名课程颜色自动对齐 |
| 负一屏 / 日历 | 课表可同步进系统日历：每节课一条（提前 15 分钟提醒）+ 每天一条「每日课表」整天汇总，周课表和每日课表互补 |
| 贴心功能 | 生日提醒板块（今天课上完后出现在今日卡底部）、课程预告范围设置 |
| 安装升级 | APK 固定签名，新版可直接覆盖安装，不必卸载、课表数据不会丢 |
| 问题修复 | 修掉设置生日时误跳到「选择时间表」的问题（快捷指令被重复执行） |
| 导入修复 | 茅台学院等新版教务「导入不全」：自动展开「更多」的完整课表视图后再抓取；每次导入留档原始页面便于排查 |

完整的版本演进、每一版做了什么、踩过哪些坑，见 [CHANGELOG.md](CHANGELOG.md)。

### 下载

安装包放在 [Releases](https://github.com/Fuhanglio/XiaoTang-Tangle/releases) 里，每个版本一个 APK：

- [**最新版（v121 / 3.652）**](https://github.com/Fuhanglio/XiaoTang-Tangle/releases/latest)
- [全部历史版本](https://github.com/Fuhanglio/XiaoTang-Tangle/releases)

安装前需在「设置 → 关于本机」里连点版本号开启开发者选项，并允许安装未知来源应用。若已装过旧版，直接覆盖安装即可，课表数据不会丢。

### 来源与声明

- 上游项目：[YZune/WakeupSchedule_Kotlin](https://github.com/YZune/WakeupSchedule_Kotlin)（WakeUp课程表，作者 YZune / 杨增）
- 上游许可：Apache License 2.0。原 `LICENSE` 与版权声明完整保留
- 本仓库是在上游源码基础上的二次开发版本，特此声明

上游作者在 README 中写道：「开源旨在可以降低后来者的门槛，借鉴可以，但是希望在相关 App 中能有所声明。」据此，本仓库在此明确标注来源与二次开发的事实。

---

# 上游 README（WakeUp课程表 3.612）

[Google Play 下载](https://play.google.com/store/apps/details?id=com.suda.yzune.wakeupschedule.pro) | [酷安下载](https://www.coolapk.com/apk/159120)

## 声明

开源旨在可以降低后来者的门槛，借鉴可以，但是希望在相关 App 中能有所声明。

教务网页解析的部分单独抽出了一个库，见 [CourseAdapter](https://github.com/YZune/CourseAdapter)

近期要忙于毕设，欢迎大佬们 PR

## 上架情况

截至2020.02.10

- 酷安[√] 19万
- 应用宝[√] 12674
- 魅族应用商店[√] 21590
- 小米应用商店[√] 61799
- OPPO应用商店[√] 19.3万
- VIVO应用商店[√] 23万
- 华为应用商店[√] 51.9万

## 开源相关

### 集成的开源库

- AndroidX 项目
- [Kotlin](https://github.com/JetBrains/kotlin)
- [Material Design](https://github.com/material-components/material-components-android)
- [Retrofit2](https://github.com/square/retrofit)
- [Toasty](https://github.com/GrenderG/Toasty)
- [jsoup](https://github.com/jhy/jsoup)
- [NumberPickerView](https://github.com/Carbs0126/NumberPickerView)
- [BaseRecyclerViewAdapterHelper](https://github.com/CymChad/BaseRecyclerViewAdapterHelper)
- [ColorPicker](https://github.com/jaredrummler/ColorPicker)
- [Glide](https://github.com/bumptech/glide)
- [Gson](https://github.com/google/gson)
- [kotlin-csv](https://github.com/doyaaaaaken/kotlin-csv)
- [TextDrawable](https://github.com/jahirfiquitiva/TextDrawable)
- [Android-QuickSideBar](https://github.com/saiwu-bigkoo/Android-QuickSideBar/)
- [sticky-headers-recyclerview](https://github.com/timehop/sticky-headers-recyclerview)
- [biweekly](https://github.com/mangstadt/biweekly)
- [appcenter-sdk-android](https://github.com/microsoft/appcenter-sdk-android)

### 参考项目

苏大的正方教务模拟登录和课程解析部分参考了[另一个课程表项目](https://github.com/mnnyang/ClassSchedule)，不过我对课程解析部分改动非常大，导入更为准确。

## TODO

- 集成“咩咩”
- 支持课程笔记
- 直接写入系统日历
- ~~完善对方正教务课程的解析~~
- 适配已经提交数据的学校
- ~~数据备份和恢复（用课程文件导出导入实现了，还支持分享）~~
- ~~课程分享~~
- ~~增加对夏冬令时的支持（可以设置任意数量的时间表）~~
- 注册登录，小范围的社交，主要是为社团的活动服务
- 完全迁移至AndroidX
- 国际化

## License

```
Copyright 2019 YZune. https://github.com/YZune

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
 limitations under the License.
 ```