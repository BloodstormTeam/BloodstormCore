package com.bloodstorm.core.util


object ChunkHash {
    @JvmStatic
    fun chunkToKey(x: Int, z: Int): Int {
        return x and 0xffff shl 16 or (z and 0xffff)
    }
    @JvmStatic
    fun keyToX(k: Int): Int {
        return (k shr 16 and 0xffff).toShort().toInt()
    }
    @JvmStatic
    fun keyToZ(k: Int): Int {
        return (k and 0xffff).toShort().toInt()
    }
    @JvmStatic
    fun chunkCoordToHash(x: Int, y: Int, z: Int): Short {
        return (y shl 8 or (z shl 4) or x).toShort()
    }
    @JvmStatic
    fun worldChunkToKey(dim: Int, x: Int, z: Int): Long {
        return dim.toLong() shl 32 or ((x and 0xffff).toLong() shl 16) or (z and 0xffff).toLong()
    }
    @JvmStatic
    fun blockCoordToHash(x: Int, y: Int, z: Int): Long {
        return (x and 0xffffff).toLong() or ((y and 0xff).toLong() shl 24) or ((z and 0xffffff).toLong() shl 32)
    }
    @JvmStatic
    fun blockKeyToX(key: Long): Int {
        var x = (key and 0xffffffL).toInt()
        if (x and 8388608 != 0) {
            x = x or (0xff shl 24)
        }
        return x
    }
    @JvmStatic
    fun blockKeyToZ(key: Long): Int {
        var z = (key shr 32 and 0xffffffL).toInt()
        if (z and 8388608 != 0) {
            z = z or (0xff shl 24)
        }
        return z
    }
    @JvmStatic
    fun blockKeyToY(key: Long): Int {
        return (key shr 24 and 0xffL).toInt()
    }
}