# 小唐Tangle 开发者变更日志

> **当前版本**：v3.660 (versionCode 129)　·　**最后更新**：2026-09-13

---

## v3.660 (versionCode 129) — 2026-09-13

### 🐛 Bug 修复

- **课表文件导入健壮性加固**：ImportViewModel.importFromFile 的 uri.path!! 改为安全调用 ?.contains()，openInputStream(uri)!! 改为 ?.use {} 关闭流，新增 list.size < 5 行数检查防止损坏文件直接 IndexOutOfBoundsException。
- **跨天时间计算丢分钟**：CourseUtils.calAfterTime 原逻辑在 newHour > 23 时把分钟也清零（23:50 + 20 分错误返回 00:00），改为 newHour -= 24 保留正确分钟（23:50 + 20 分 → 00:10）；同时 substring 提取加 toIntOrNull() 兜底，格式异常不再崩溃。
- **ScheduleActivity 监听器 / Observer 累积**：initEvent() 每次调用都会叠加注册 addOnPageChangeListener，initView() 每次调用都会叠加注册 7 个课程 observe（onCreate + 加载回调 + onActivityResult 三条触发路径）。修复：initEvent() 开头先 clearOnPageChangeListeners()；新增成员变量 courseObserversRegistered 保护 7 个 observe 只注册一次。
- **AddCourseActivity 空指针崩溃**：onCreate 内 8 处 intent.extras!!.getInt(...)，进程被系统重建后 intent.extras 可能为 null 直接 NPE。改为统一 val extras = intent.extras ?: run { finish(); return }，getInt 全部加默认值兜底。

---

## v3.659 (versionCode 128) — 五批系统性修复 · 批次五

### 🐛 Bug 修复

- **dao.CourseDao.coverImport 空列表越界**：方法开头新增 if (courseBaseList.isEmpty() || courseDetailList.isEmpty()) return，避免空列表时 courseBaseList[0] 越界。
- **utils.CourseUtils.getMultiCourse 数组越界**：allCourseList[day - 1] 改为 allCourseList.getOrNull(day - 1)?.value?.filter {...} ?: emptyList()，防止 day 超界时 ArrayIndexOutOfBoundsException。
- **ScheduleActivity selectedWeek 越界**：viewPager 翻页时用 (position + 1).coerceIn(1, viewModel.table.maxWeek) 钳制，极端情况下不会出现第 0 周或超出 maxWeek 的周数。
- **SettingsActivity dayNightIndex 越界**：主题列表切换时用 coerceIn(0, dayNightTheme.size - 1) 保护，防止新增主题项后用户存储的旧索引越界。

### 🎨 UI 优化

- **小部件空色课程回退统一**：widget/WidgetData.kt 中两处分隔色回退值从 #5D4037（深棕）统一为 #007AFF（iOS 蓝主色），与 AppWidgetUtils 现有回退一致。

---

## v3.658 (versionCode 127) — 五批系统性修复 · 批次四

### 🐛 Bug 修复

- **茅台学院 NewZFParser 笔误**：headerText.contains("周一") || headerText.contains("周一") 改为 || headerText.contains("周日")，修复 React 教务页面的列头识别，茅台学院 2025 级之前的教务系统周日列被漏解析。
- **exportICS 空 catch 吞异常**：ScheduleViewModel.exportICS 里遍历课程生成 ICS 事件时，单节课的空 catch 改为 Log.e(TAG, "导入ICS时跳过一节课", e)，不再静默丢失课程且便于排查。
- **ColorWheelView 崩溃保护**：圆盘调色板 onDraw / onTouchEvent 计算出 innerR 后加 width<=0 || height<=0 || innerR<=0f 判空，drawSvDisc 创建 Bitmap 前判 size<=0，避免窗口未测量或极小尺寸下 createBitmap 负数崩溃。

### 🔒 安全加固

- **AppDatabase.allowMainThreadQueries 标注技术债**：在该调用上方加 TODO 注释，明确后续需迁移到 Room Coroutines / Paging 彻底消除主线程查询依赖。

---

## v3.657 (versionCode 126) — 五批系统性修复 · 批次三

### 🐛 Bug 修复

- **小部件 ANR（主线程阻塞）**：widget/WidgetUpdateReceiver.onReceive 原本在主线程做 Room 同步查询 + 遍历所有小部件刷新 RemoteViews，改为 goAsync() 包裹后台线程执行，ANR 窗口约 10 秒，杜绝系统弹框。
- **多课表切换后周小部件串表**：refreshAllWidgets 原本所有周小部件统一传全局默认 TableBean，导致用户切表后所有已安装的周小部件都显示同一张课表。改为按每个 AppWidgetBean.info 字段取各自绑定的课表（info 为空用默认表，不为空则 getTableByIdSync(info.toIntOrNull())），口径与 ScheduleAppWidget.kt 现有绑定逻辑一致。
- **倒计时逻辑写死"明天"**：AppWidgetUtils.refreshNextWidget 原逻辑 mins > 120 一律显示"明天 HH:mm"，实际上当天下午 3 点的课还有好几个小时就被显示成"明天"。改为先比较日期判断今天 / 明天 / 其他日期，今天的课显示"今天 HH:mm 上课 / 还有 X 小时X分"。

### 🔒 安全加固

- **timeToMillis 解析失败静默消失**：WidgetData.timeToMillis 原本解析失败返回 0L，课程时间被当成 1970 年静默消失（无异常、无日志）。改为返回 null，调用处 mapNotNull 跳过该节并用 Log.w 记录原始时间串。

### ⚡ 性能优化

- **全局 PendingIntent 补 FLAG_IMMUTABLE**：WidgetScheduler、WidgetUpdateReceiver、TodayCourseAppWidget、AppWidgetUtils 中所有 PendingIntent 统一从 flags=0 或 FLAG_UPDATE_CURRENT 改为 FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE（Kotlin 写法 or 组合），满足 targetSdk 29 下系统对不可变 PendingIntent 的要求。

---

## v3.656 (versionCode 125) — 五批系统性修复 · 批次二

### 🔒 安全加固

- **WebView SSL 错误不再静默放行**：WebViewLoginFragment.onReceivedSslError 删除原按渠道区分的逻辑（某些渠道 handler.proceed() 放行），所有渠道统一弹 AlertDialog 说明"证书存在风险，可能被窃听，是否仍要继续"，用户显式点"继续"才 proceed，默认 cancel。
- **LoginWebActivity Intent 校验**：读取 intent.url / action / data 全部判空，url 缺失或 action 不是 ACTION_VIEW 又无合法 url 时 Toasty 提示"导入参数缺失"并 finish；data.scheme 校验只接受 content / file / http / https，非法来源直接 finish。
- **WebViewLoginFragment arguments 判空**：arguments?.getString("url") 加安全调用，缺参时友好退出。

### 🐛 Bug 修复

- **导入流程崩溃**：之前某些深链跳转会因 Intent 数据缺字段直接 NPE，现在参数异常时 Activity 自退，不会 crash 到系统。

---

## v3.655 (versionCode 124) — 五批系统性修复 · 批次一

### 🐛 Bug 修复

- **默认课表数据库迁移根因**：dao.TableDao.getDefaultTable() / getDefaultTableSync() 返回类型从非空 TableBean 改为可空 TableBean?，老用户升级后可能没有默认课表导致 UI 层 lateinit 崩溃。Migration 7→8 末尾追加 UPDATE tablebean SET type=1 WHERE id=(SELECT MIN(id) FROM tablebean)，保证 v7 老用户升级后也有默认课表。
- **全局空态处理**：ScheduleActivity、WidgetUpdateReceiver、WidgetData、TodayCourseAppWidget、WeekScheduleAppWidgetConfigActivity 等所有调用 getDefaultTable() 的地方统一判空：UI 层空态显示 / 友好提示，小部件层跳过该实例或渲染"点击配置"占位。
- **updateFromOldVer 新课表 id 来源**：从原来的 tableDao.getLastId() + 1（可能重复）改为 getDefaultTableSync()?.id ?: return，确保导入的新课表挂到正确的父课表 ID 下。
- **UpdateUtils KEY_HAS_ADJUST 标记提前**：原逻辑表没建成就写 KEY_HAS_ADJUST=true，导致下次启动跳过已初始化路径但实际数据不全。改为只在数据确实插入成功后才标记。

### 🧹 代码质量

- **空 catch 补日志**：UpdateUtils 两处空 catch（建表 / 插入时间段）从静默吞异常改为 Log.e(TAG, "初始化默认课表失败", e)，便于排查首次安装异常。

---

## v3.654 (versionCode 123) — 拨生日滚轮时整页退出

### 🐛 Bug 修复

- **系统边缘返回手势误触发**：生日选择的数字滚轮区域被系统边缘返回手势覆盖，滑动滚轮到极端值时触发返回手势导致整页退出到课表设置页。修复：把滚轮区从系统边缘返回手势排除。

---

## v3.653 (versionCode 122) — 图片导入（OCR）

### ✨ 新功能

- **课表截图直接识别**：新增「图片导入」入口，用户拍/传一张课表截图，用本机离线 Tesseract 5 OCR（简体中文训练数据 chi_sim.traineddata）识别课名、教师、教室、时间节点，还原成课程列表后一键导入。

### 🔧 内部实现

- 新增 utils/TessOcrUtils 封装单例 OCR 引擎，释放时机在 ImageImportFragment.onDestroy 兜底。
- 训练数据首启从 assets/tessdata/ 释放到应用私有目录，首次加载约 1-2 秒。

---

## v3.652 (versionCode 121) — 每日课表

### ✨ 新功能

- **每日课表**：同步到系统日历时，额外写一条"每日全天汇总"事件，方便在系统日历日视图下直接看到当天所有课。

---

## v3.651 (versionCode 120) — 茅台学院导入不全

### 🐛 Bug 修复

- **茅台学院课表"更多"漏抓**：茅台学院 React 教务页面默认只渲染部分课程，完整课表藏在"展开更多"按钮后。修复：NewZFParser 自动展开后再抓取完整 DOM。

---

## v3.650 (versionCode 119) — 生日快捷指令跳转修复

### 🐛 Bug 修复

- **设置生日后误跳到时间表页**：生日设置页的快捷指令（QuickSettings）存在消费不彻底 / 重建问题，用户设置完生日后意外跳到选择时间表页。修复：快捷指令加消费与重建守卫。

---

## v3.649 (versionCode 118) — 固定 APK 签名

### ⚡ 性能优化

- **固定 APK 签名**：签名配置改为固定 keystore 文件，支持覆盖安装（从 Play Store / 应用中心升级不会签名冲突）。签名文件路径在 Windows 下为 xiaotang.jks，原作者遗留的 Mac 路径 /Volumes/Document/yzune.jks 保留为注释说明。

---

## v3.648 (versionCode 117) — 长课名不再挤掉教室名

### 🎨 UI 优化

- **课名 / 教室两行自适应**：原布局课名和教室共用一行，长课名会把教室挤掉看不见。修复：课名行加 maxLines=1 + ellipsize=end 省略号，教室行独立一行优先保住完整显示。

---

## v3.647 (versionCode 116) — 装机反馈修正

### 🎨 UI 优化

- 今日卡行数修正（超过 4 行时自动滚动）。
- 生日板块渲染时序修正（View 未 attach 前触发）。
- 设置页图标资源替换。
- 默认课时从 10 改为项目实际课时数。

---

## v3.646 (versionCode 115) — 今日卡两种排列

### ✨ 新功能

- **今日卡双样式**：新增两种桌面小部件样式可选：
  - 竖排（右侧显示上课时间 HH:mm，课名在左）。
  - 紧凑两列（适合桌面空间紧张的用户）。

---

## v3.645 (versionCode 114) — 课表同步到系统日历

### ✨ 新功能

- **系统日历同步**：课程可一键同步到系统日历，每节课一条事件，包含课名、教师、教室、起止时间；同步前需动态申请 READ_CALENDAR / WRITE_CALENDAR 权限。
- 新增 utils/CalendarSyncUtils 工具类，widget/PermissionGuideActivity 首次未授权时弹引导页。

### 📦 仓库建设

- README 加当前版本号与下载入口。
- GitHub Releases 首次发布 v114~v117。

---

## v3.612 → v3.644 (versionCode 1-93) — 二开主要迭代期

基于开源 WakeUp课程表 3.612（com.yzune.wakeupschedule）二次开发，完整包名迁移 + 功能 / UI 大规模重塑。以下按模块汇总，部分细节（v1-v113 中间迭代）无精确提交记录，标注 （待确认）。

### ✨ 新功能

- **茅台学院教务一键导入**：在 SchoolListActivity 新增 SchoolInfo("M", "茅台学院", "https://jwxt.mtxy.edu.cn/", TYPE_ZF_NEW)，自研 NewZFParser 解析 React courseBox 结构，支持诊断 dump 到 Download/wakeup_import_debug_*.html 便于本地定位解析失败原因。
- **自定义圆盘调色板**：widget/ColorWheelView 自研取色组件，提供 HSV 圆盘 + 6 套彩色预设（清新绿 / 天空蓝 / 樱花粉 / 活力橙 / 优雅紫 / 薄荷青）+ 跟随系统默认 iOS 蓝 #007AFF，支持自定义取色。
- **智能今日 / 明日桌面小部件**：静态 RemoteViews 实现，今日课程上完后自动预告次日课程；支持每节开始 / 结束精确闹钟刷新 + WorkManager 30 分钟兜底刷新。
- **课表文件导入 / 导出**：导出格式为 .wakeup_schedule（自定义 JSON 结构），其他用户可通过 LoginWebActivity（VIEW intent）打开分享文件一键导入。Manifest 中 exported=true 是刻意设计，安全校验写在 Activity 内部而非依赖 exported=false。
- **Excel 课表导入** （待确认）。
- **图片 OCR 导入**（v122 实现，已在上方记录）。
- **散周课程支持**：课表数据模型支持单周 / 双周 / 指定某几周的散周课程，显示时正确判断命中。

### 🎨 UI 优化

- **全局 iOS 风 UI 重塑**：中性灰白骨架背景 page_bg #F2F2F7、卡片白底 card_bg #FFFFFF、每行马卡龙彩块点缀；默认主色 iOS 蓝 #007AFF，深色 #0A84FF。
- **顶部 iconfont 乱码修复**：原作者使用的 iconfont 在部分机型显示为方块，替换为 AndroidX 原生矢量图标。
- **设置页重新整理**：改为 4 分组（课表设置 / 外观设置 / 桌面小部件 / 关于），每组独立卡片。
- **权限页滚动修复**：权限申请列表在小屏机型上滚动到最后一项时无法滑到底。
- **开关按钮、抽屉、底部弹层组件样式统一**。
- **课表周数胶囊**：周数 ≥ 10 时之前被挤成上下两行，修复为单行自适应宽度。
- **抽屉 + 底部弹层互斥**：抽屉打开时底部弹层未关闭，两层叠加导致 UI 混乱，修复为互斥。

### 🐛 Bug 修复

- **散周课程误判**（原 parser bug）：某些教务返回的"单双周"标记被错误解析为连续周范围。
- **导入课表后重复同一课表**（NewZFParser 笔误 contains("周一")||contains("周一")，v127 修复）。
- **课表设置页开关关闭态看不清**：关闭态 alpha 过低，改为统一 alpha=0.35。

### ⚡ 性能优化

- **删除 Microsoft AppCenter 统计 SDK**：原版包含 Microsoft AppCenter 遥测上报，二次开发时删除（无广告、无付费、无统计上报），APK 体积明显减小。
- **删除作者后端相关代码**：原版部分功能依赖作者自建后端（云同步等），二次开发时移除。

### 🔧 基础迁移

- **包名**：com.yzune.wakeupschedule → com.Tangle.timetable（Google Play 渠道变体 com.Tangle.timetable.pro）。
- **应用名**：WakeUp → 小唐Tangle。
- **数据库 schema 升级**：原版数据库迁移链从 v1 到 v8，迁移期间新增默认课表标识位 type、课程详情表 course_detail 等。
- **Room allowMainThreadQueries 保留**：为避免遗漏的主线程查询崩溃，迁移期间暂保留主线程查询能力，上方加 TODO 标注技术债。

---

## 📋 版本说明

- **应用名**：小唐Tangle（Google Play 渠道变体 WakeupSchedule Pro）。
- **包名**：com.Tangle.timetable（debug）/ com.Tangle.timetable.pro（release）。
- **基于开源项目**：[WakeUp课程表](https://github.com/yzune/WakeupSchedule_Kotlin)（作者 yzune，基于 Kotlin + Room + ViewPager + AppWidget，原版作者已被收购并停止更新）。
- **当前编译配置**：compileSdkVersion 29 / targetSdkVersion 29 / minSdkVersion 21。
- **APK 大小**：约 14.32 MB（normal debug）。
- **网络安全**：兼容数百所仅支持 http 的老旧教务系统，network_security_config.xml 保持 cleartextTrafficPermitted="true"；茅台学院校内会重定向到内网 IP，禁明文会导致导入大面积失败。
- **无广告 · 无付费 · 无统计上报**：已删除原版 Microsoft AppCenter SDK 和作者自建后端相关代码。
- **许可证**：沿用原版开源协议（MIT），二次开发部分同协议。

---

## 🚀 发布记录

- **v3.660 (versionCode 129)** — 2026-09-12 发布
  - Tag：`v129`，基于提交 `e7474f3`（main）
  - Release 页：https://github.com/Fuhanglio/XiaoTang-Tangle/releases/tag/v129
  - 产物：`XiaoTangTangle-v129-3.660.apk`（约 14.3MB，normal debug，签名 SHA1 `4E:FA:65:D0:…:4F:B4`）
  - 本次为 v124~v129 一次性收口：端到端代码审查发现的稳定性 / 安全项全部落地（详见上方各版本条目）
  - 提交信息：v129 / 3.660 端到端代码审查修复 + 开发者日志补全（22 文件，含新增开发者变更日志）