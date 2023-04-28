package com.bloodstorm.core.compat


object RangeUtil {
    @JvmStatic
    fun checkPositive(n: Long, name: String): Long {
        require(n > 0) { "$name: $n (expected: > 0)" }
        return n
    }
    @JvmStatic
    fun checkPositiveOrZero(n: Int, name: String): Int {
        require(n >= 0) { "$name: $n (expected: >= 0)" }
        return n
    }
    @JvmStatic
    fun checkLessThan(n: Int, expected: Int, name: String): Int {
        require(n < expected) { "$name: $n (expected: < $expected)" }
        return n
    }
    @JvmStatic
    fun checkLessThanOrEqual(n: Int, expected: Long, name: String): Int {
        require(n <= expected) { "$name: $n (expected: <= $expected)" }
        return n
    }
    @JvmStatic
    fun checkGreaterThanOrEqual(n: Int, expected: Int, name: String): Int {
        require(n >= expected) { "$name: $n (expected: >= $expected)" }
        return n
    }
}