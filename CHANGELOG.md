# 更新说明 · 小唐 Tangle

本仓库基于 [WakeUp课程表](https://github.com/YZune/WakeupSchedule_Kotlin)（作者 YZune / 杨增，Apache License 2.0）二次开发。
下文按版本列出标志性改动，起点是「小卡片更新不了课表」这一问题链，逐版记录做了什么、为什么这么做。

上游项目地址：<https://github.com/YZune/WakeupSchedule_Kotlin>

---

## 起点：问题从哪来

v104 之前，今日课程卡走的是「ListView + 位图行」方案。

v104 把卡片改成静态行，但每一行的内容仍然是先用代码绘制成 Bitmap，再塞进 RemoteViews 下发。4 行位图在同一次 `updateAppWidget` 里约有 1.9MB，超过系统约 1MB 的传输上限，整次更新被系统直接丢弃。

表现就是：卡片停在布局文件里的默认文案，看起来像「加载不出来」「更新不了」。

这条问题链贯穿 v104 到 v107，也是本轮改造的起点。

---

## 总览

| 版本 | versionName | 类型 | 一句话 |
| --- | --- | --- | --- |
| 104 | 3.635 | 改版 | 今日卡首轮静态行改版，行内容仍用位图（引入超限问题） |
| 105 | 3.636 | 重做 | 今日卡彻底去位图，改原生控件行块 + 高度自适应 + 空态 |
| 106 | 3.637 | 重做 | 周课表卡改成「一周概览列表」，去掉贴图方案 |
| 107 | 3.638 | 修复 | 修掉今日卡长期「加载中…」的真根因（刷新入口依赖数据库登记） |
| 108 | 3.639 | 外观 | 三个小部件预览图全部重画，尺寸调整 |
| 109 | 3.640 | 功能 | 周课表卡加「+」直达加课；同名课程颜色自动对齐 |
| 110 | 3.641 | 功能 | 「课程预告范围」二选一；空态短句库 55 条 |
| 111 | 3.642 | 修正 | 预告范围语义修正为「从今天起共 N 天」；短句库扩到 255 条 |
| 112 | 3.643 | 功能 | 新增「生日提醒板块」 |
| 113 | 3.644 | 功能 | 课表网格内**拖拽移动课程** |

---

## v104 / 3.635 · 今日卡首轮静态行改版

- 今日课程卡由 ListView 改为静态行布局。
- 行内容改用 Bitmap 绘制后下发。
- 结果：单次更新数据量约 1.9MB，超系统上限被丢弃，卡片表现为空白 / 停在默认文案。

## v105 / 3.636 · 今日卡彻底重做

这是第一版真正可用的今日卡。

- **去掉位图**，行内容改用 RemoteViews 原生的控件行块（TextView + ImageView 组合），单次更新体积降到可以忽略。
- 卡片高度按桌面实际占用自适应，显示几行由 `AppWidgetManager.getAppWidgetOptions()` 的实际高度算出，避免留白和滚动条。
- 逐行着色走 `ImageView.setColorFilter`，ImageView 用白色圆角 drawable 作 src。直接 `setBackgroundColor` 会丢圆角，这条路走不通。
- 卡片背景做成玻璃感（半透明白 + 细描边）。RemoteViews 不支持 `setAlpha`，也做不了真正的背景模糊，只能用这个近似方案。
- 补上无课时的空态显示。
- 新增 `AppWidgetUtils.refreshWidgetById`，修掉「改完课后卡片不刷新」的回归问题。
- 删除 `TodayRowRenderer.kt` 与废弃布局 `today_course_app_widget_1.xml`。

## v106 / 3.637 · 周课表卡改成「一周概览列表」

- `schedule_app_widget.xml` 整份重写。原来是透明背景 + ListView 贴课表位图，改为玻璃卡 + 每天一行（`wrow_0` ~ `wrow_6`，周一至周日）。
- 新增 `WidgetData.getWeekPlan`，汇总每天的课程名。
- 删除 `ScheduleAppWidgetService.kt`、`item_schedule_widget.xml` 及其在 manifest 里的注册。
- `refreshScheduleWidget` 去掉适配器，改为按卡片高度自适应 1 ~ 7 行。

## v107 / 3.638 · 修掉今日卡「加载中…」的真根因

这是这个项目里最值得记一笔的一个 bug。

- **根因**：今日卡没有配置页（info xml 里没有 `android:configure`），所以它从未被登记进 `appwidgetbean` 表。而当时所有刷新入口都在遍历数据库里的登记记录，循环恒为空，等于谁都没刷新过它。
  周课表卡之所以正常，是因为它有配置页，会被写进登记表。
- **修法**：刷新不再依赖数据库登记，改用系统传入的 `appWidgetIds`；广播路径用 `ComponentName` + `getAppWidgetIds` 拿到全部实例并自动补登记。
- 顺带把智能预告从「只看今明两天」扩展为「往后找最近有课的一天」。

## v108 / 3.639 · 预览图重画与尺寸调整

- 三个小部件的 `previewImage` 全部重画。原本今日卡用的是上游项目自带的蓝色旧截图，另两个没配预览图，被系统兜底成白底小图标。
- 尺寸调整：今日卡 150 → 240dp，周课表 190 → 200dp，下一节课 110 → 140dp。

## v109 / 3.640 · 加课入口与颜色对齐

- 周课表小部件右上角新增「+」，点击直达加课页。
- 新建同名课程时，颜色自动沿用已有同名课程的颜色，避免同一门课两个色。

## v110 / 3.641 · 课程预告范围设置

- 新增「课程预告范围」二选一：「提前 2 天」/「提前 7 天」。用单值存储，两个选项天然互斥。
- 空态短句库 55 条，卡片无课时随机展示。

## v111 / 3.642 · 预告语义修正与短句库扩充

- 「预告范围」的语义修正为「从今天起共 N 天」。提前 2 天 = 今天 + 明天。
- 空态短句库扩到 255 条：幽默 66 条、励志 79 条、关心 70 条、遗憾 40 条，全部不超过 10 个字，资源体积约 5.8KB。

## v112 / 3.643 · 生日提醒板块

- 今日卡底部新增生日提醒板块，**只在今天课程全部上完时**才出现（今天本来没课也算）。
- 日期选择用 `NumberPicker` 滚轮，只选月和日；年份由 `BirthdayUtils.nextYear` 推断：月日已过算明年，未到算今年，正好是今天算今年。
- 新增文件：`BirthdayUtils.kt`、`BirthdayReminderActivity.kt`、`activity_birthday_reminder.xml`、`widget_birthday_bg.xml` / `widget_birthday_bg_dark.xml`。
- 偏好键：`birthday_month` / `birthday_day` / `birthday_text`。
- 入口：课表设置 → 桌面小部件外观 → 「生日提醒」。
- 板块出现时，课程行压缩到最多 2 行，避免底部被裁。

## v113 / 3.644 · 课表网格内拖拽移动课程

本轮最大的一次交互改造。

- 长按课程块起拖，拖动过程中实时高亮目标格，松手落位。
- 一次移动**整组**：同名 + 同节次 + 同 step 的多条周次分段（单双周、散周）一起走，保留 step 与周次不变。
- 落位后弹出 Snackbar 提供「撤销」，可以直接搬回原位。
- 数据结构上先按旧复合主键删除、再插新记录（`@Transaction` 包住）。`CourseDetailBean` 的主键是 `(day, startNode, startWeek, type, tableId, id)`，改位置就换了主键，直接用 `@Update` 会更新 0 行，用 `REPLACE` 会残留旧记录。
- 新增文件：`CourseDragController.kt`、`drawable/course_drag_highlight.xml`；`ScheduleFragment.kt` 增加长按回调与 `onCourseMoved`；`CourseDao.kt` 增加 `moveCourseDetails` 事务方法。
- 只支持移动位置，不支持拉长 / 缩短课程时长。

---

## 技术备忘（踩过的坑）

以下每一条都是实际踩过并修掉的，留给后来者。

1. **今日卡一直「加载中…」有两个完全不同的根因**，排查时两个都要看：
   - 刷新入口只遍历数据库登记表，而今日卡没有配置页所以从未被登记（v107 修）。
   - 行内容用 Bitmap 下发，超过 RemoteViews 约 1MB 的传输上限，整次更新被丢弃（v105 修）。
   两者的症状都是「停在布局文件默认文案」，很容易混淆。

2. **RemoteViews 逐行着色**只能走 `ImageView.setColorFilter`。ImageView 用白色圆角 drawable 作 src，再 `setInt(id, "setColorFilter", 颜色)`。直接 `setBackgroundColor` 会丢圆角。

3. **RemoteViews 不支持 `setAlpha`**，也做不了真正的背景模糊（毛玻璃），只能半透明白 + 细描边近似。

4. **批量引用 id 用显式数组**，不要用 `R.id.xxx_0 + i` 这种算术递增。

5. 卡片显示几行，按 `AppWidgetManager.getAppWidgetOptions()` 拿到的实际高度算，避免滚动条与留白。

6. `CourseUtils.countWeek` 不会把 `startDate` 吸附到周边界，`countWeek = daysBetween / 7 + 1`。`startDate` 必须填该周第一天，否则周次会差 1。

7. **ConstraintLayout 里动态定位不能用 `setX` / `setY`**，会被约束布局覆盖。要用 `LayoutParams.marginStart` / `topMargin` 配 `startToStart` / `topToTop`。

8. **`DragEvent` 的坐标基准是「接收该事件的 View」**，分发细节不可依赖。拖拽时加了一层与网格同原点的透明接收层（`dropZone`）把坐标原点钉死。

9. 拖拽用 `View.startDrag`（API 11+）而不是 `startDragAndDrop`（API 24+），以保住 minSdk 21。

---

## 已知限制 / 待验证

- **拖拽手感未在真机验证过。** 逻辑与编译结果已核验，但长按阈值、滑动判定这些手感参数需要真机上手调。
- 拖拽只支持移动，不支持改变课程时长（拉长 / 缩短）。
- 生日提醒依赖手动设置，暂未接入通讯录。

---

## 构建说明

工程为 Android 项目（Kotlin / Gradle 5.4.1 / AGP 3.5.3 / compileSdk 29，minSdk 21，targetSdk 29）。

```bash
# 需要 JDK 8
./gradlew assembleNormalDebug
```

产物：`app/build/outputs/apk/normal/debug/app-normal-debug.apk`

> `local.properties` 需要自行创建并指向本机 Android SDK 路径，该文件已被 git 忽略。
