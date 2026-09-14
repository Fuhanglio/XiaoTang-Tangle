/*
 * Copyright 2019 Louis Cognault Ayeva Derman. Use of this source code is governed by the Apache 2.0 license.
 */

package splitties.experimental

// 说明：Kotlin 1.9 起旧的 @Experimental / Experimental.Level 已废弃且无法映射为 Java 注解，
// kapt 生成 stub 时会产出 @error.NonExistentClass() 导致编译失败，故改用现行 API @RequiresOptIn。
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalSplittiesApi

@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class InternalSplittiesApi
