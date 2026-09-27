# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
############################################

# 对于一些基本指令的添加

############################################
# 代码混淆压缩比，在0~7之间，默认为5，一般不做修改
-optimizationpasses 5

# 配置字典
-obfuscationdictionary proguard_dic.txt
-classobfuscationdictionary proguard_dic.txt
-packageobfuscationdictionary proguard_dic.txt

# 混合时不使用大小写混合，混合后的类名为小写
-dontusemixedcaseclassnames

# 指定不去忽略非公共库的类
-dontskipnonpubliclibraryclasses

# 这句话能够使我们的项目混淆后产生映射文件
# 包含有类名->混淆后类名的映射关系
-verbose

# 指定不去忽略非公共库的类成员
-dontskipnonpubliclibraryclassmembers

# 不做预校验，preverify是proguard的四个步骤之一，Android不需要preverify，去掉这一步能够加快混淆速度。
-dontpreverify

# 保留Annotation不混淆
-keepattributes *Annotation*,InnerClasses

# 保留自定义异常类
-keep public class * extends java.lang.Exception

# 避免混淆泛型
-keepattributes Signature

# 抛出异常时保留代码行号
-keepattributes SourceFile,LineNumberTable

# 指定混淆是采用的算法，后面的参数是一个过滤器
# 这个过滤器是谷歌推荐的算法，一般不做更改
-optimizations !code/simplification/cast,!field/*,!class/merging/*

#所有代码移动到一个包下
#-repackageclasses me.bakumon.moneykeeper

#############################################
#
# Android开发中一些需要保留的公共部分
#
#############################################

# 保留我们使用的四大组件，自定义的Application等等这些类不被混淆
# 因为这些子类都有可能被外部调用
-keep public class * extends android.app.Activity
# W4-09：此处原为 android.app.Appliction（漏了 a），类名拼错 → 该条**永不匹配**。
# 之所以一直没出事：App 类写在 Manifest 的 android:name 里，AGP 会为它生成 aapt 规则保住，
# 属"侥幸正确"。改正后这条才真正生效。
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep public class com.android.vending.licensing.ILicensingService


# W4-08：原此处有一组 android.support.** 的 keep 规则（`android.support.**`、
# `* extends android.support.v4.** / v7.** / annotation.**`）。
# 本工程 android.useAndroidX=true + android.enableJetifier=true，源码里 `android.support` 0 命中，
# Jetifier 会把第三方 AAR 里的 support 引用在构建期改写成 androidx →
# 最终依赖图里已经没有 android.support 类，这几条 keep 保护的是**不存在的类**，属漂移规则，已删。
# 保留R下面的资源
-keep class **.R$* {*;}

# 保留本地native方法不被混淆
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留在Activity中的方法参数是view的方法，
# 这样以来我们在layout中写的onClick就不会被影响
-keepclassmembers class * extends android.app.Activity{
    public void *(android.view.View);
}

# 保留我们自定义控件（继承自View）不被混淆
-keep public class * extends android.view.View{
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留Parcelable序列化类不被混淆
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留Serializable序列化的类不被混淆
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 移除Log类打印各个等级日志的代码，打正式包的时候可以做为禁log使用，这里可以作为禁止log打印的功能使用
# 记得proguard-android.txt中一定不要加-dontoptimize才起作用
# 另外的一种实现方案是通过BuildConfig.DEBUG的变量来控制
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

#############################################
#
# 项目中特殊处理部分
#
#############################################

# W4-08：原此处为 `-keep class * extends android.support.v7.widget.LinearLayoutManager`。
# 工程已全面 androidx（useAndroidX + enableJetifier，源码 android.support 0 命中），
# 该类在最终依赖图里不存在 → 漂移规则，已删。

#-----------处理第三方依赖库---------

# BRVAH
# W4-08：`BaseViewHolder` 在 BRVAH 3.x 已从 com.chad.library.adapter.base 移到
# com.chad.library.adapter.base.viewholder 子包。已解包 BaseRecyclerViewAdapterHelper-3.0.0-beta11.aar
# 核对：classes.jar 里只有 com/chad/library/adapter/base/viewholder/BaseViewHolder.class，
# 旧路径不存在 → 原三条规则**永不匹配**（"看起来保了、其实没保"）。下面已改为正确包名。
-keep class com.chad.library.adapter.** {
*;
}
# W4-10：上面这条 `-keep class com.chad.library.adapter.** { *; }` 把整个 BRVAH 包都留下了，
# 它是本工程**最粗的一颗 keep**（BRVAH 3.0.0-beta11 的类本来就全部被反射/泛型擦除牵连）。
# 结论：**无法在规则层面"弱化"** —— keep 规则只能取并集、不能相减；去掉它会在 release 下
# 出现 adapter 内部类被裁导致的运行时崩溃。
# ⇒ 本批**接受现状**（代价：混淆强度略降、包体略大，但不出功能故障）。
#   若要真正收窄，只有一条路：升级到 BRVAH 4.x（其内部结构对 R8 更友好），
#   那属于**依赖升级**，要单独评估（会把 BaseQuickAdapter 的泛型签名与
#   `setOnItemChildClickListener` 等 API 换掉，牵动所有列表页）。
-keep public class * extends com.chad.library.adapter.base.BaseQuickAdapter
-keep public class * extends com.chad.library.adapter.base.viewholder.BaseViewHolder
-keepclassmembers  class **$** extends com.chad.library.adapter.base.viewholder.BaseViewHolder {
     <init>(...);
}

# fabric crashlytics
# W4-08：本工程从未引入 Crashlytics（build.gradle 里 grep 无命中），
# 原 `-keep class com.crashlytics.** { *; }` 保护的是不存在的类，已删。
# 而 `-dontwarn com.crashlytics.**` **刻意保留** —— 见下方 -dontwarn 段落的统一说明。
-dontwarn com.crashlytics.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

# W4-08：原此处有 `-keep class net.fortuna.ical4j.** { *; }`。
# 本工程用的是 biweekly（net.sf.biweekly:biweekly:0.6.3），已解包 biweekly-0.6.3.jar 核对：
# 顶层包只有 biweekly.*，**没有** net/fortuna/ical4j → 该 keep 保护的是不存在的类，已删。

# ⚠ 关于下面这一整组 -dontwarn：
#   -dontwarn 与 -keep 的**风险方向相反**：
#     · 删掉一条指向不存在类的 -keep → 零影响（本来就没保护到任何东西）
#     · 删掉一条 -dontwarn    → 只要依赖图里有一处引用了该类，R8 会以
#       "Missing classes detected while running R8" **直接让 release 构建失败**
#
# W4-07（2026-09-26）：release 链路第一次真正跑起来，R8 **当场就报了一个缺口**：
#   ERROR: Missing class com.fasterxml.jackson.core.JsonToken
#          (referenced from: biweekly.io.json.JCalParseException.actual and 5 other contexts)
#   根因：biweekly（net.sf.biweekly:biweekly:0.6.3）把 Jackson 当**可选**依赖参与部分路径，
#   而工程没有引 Jackson → R8 全模式下"引用了不存在的类"是**错误**而不是警告。
#   这条恰好印证了上面那句话：缺一条 -dontwarn 就炸 release。
#   官方建议的单类写法是 `-dontwarn com.fasterxml.jackson.core.JsonToken`，
#   这里放大到整个 core 包 —— biweekly 对 Jackson 的引用不止一处（报错原文即 "and 5 other contexts"），
#   逐个补会在下次升级时再报一遍。
-dontwarn com.fasterxml.jackson.core.**

# W4-08（收尾）：以下 5 条经 2026-09-26 的**真实 R8 运行**验证为冗余，已删除：
#   net.fortuna.ical4j.model.**  —— 解包 biweekly-0.6.3.jar 确认无此包（无引用点）
#   groovy.** / org.codehaus.groovy.** / aQute.bnd.** / com.squareup.picasso.**
#                                —— 依赖图里根本没有对应库
# 删除依据不是"猜"，而是"删掉后 R8 仍然通过"（见 build_r8.log / B27 报告）。
# 若将来某次升级后又出现 Missing class，R8 会在 missing_rules.txt 里给出该补哪一条。
-dontwarn javax.annotation.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.Platform$Java8

# for iCal4J —— W4-08：原 `-dontwarn net.fortuna.ical4j.model.**` 已随下方 5 条一并删除
# （biweekly 0.6.3 不含该包；且删掉后真实 R8 运行通过）。
-dontwarn org.slf4j.impl.**
-dontwarn org.apache.commons.logging.**
-dontwarn sun.misc.Perf

# Glide（W4-08：原来的 `# Matisse` + `-dontwarn com.squareup.picasso.**` 已删 —— 工程未用 Matisse/Picasso）
-dontwarn com.bumptech.glide.**

# for retrofit 2
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

##---------------Begin: proguard configuration for Gson  ----------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
#-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class com.google.gson.examples.android.model.** { <fields>; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

##---------------End: proguard configuration for Gson  ----------

-keepattributes Annotation
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers public class com.Tangle.timetable.schedule_import.**{
    <fields>;
    <methods>;
    public *;
    private *;
}

-keepclassmembers public class com.Tangle.timetable.bean.**{
    <fields>;
    <methods>;
    public *;
    private *;
}

# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Most of volatile fields are updated with AFU and should not be mangled
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

-keep class it.sephiroth.android.library.xtooltip.TooltipOverlayDrawable { *; }