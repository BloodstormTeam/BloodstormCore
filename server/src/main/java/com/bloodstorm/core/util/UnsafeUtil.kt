package com.bloodstorm.core.util

import sun.misc.Unsafe

object UnsafeUtil {
    @JvmField
    val UNSAFE: Unsafe

    init {
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        UNSAFE = unsafeField.get(null) as Unsafe
    }
}