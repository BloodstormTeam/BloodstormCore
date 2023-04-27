package com.bloodstorm.core.chunk

import net.minecraft.util.LongHashMap
import net.minecraft.world.chunk.Chunk

class VanillaChunkHashMap(private val chunkMap: ChunkMap): LongHashMap() {
    private fun v2x(key: Long): Int {
        return (key and 0xFFFFFFFFL).toInt()
    }

    private fun v2z(key: Long): Int {
        return (key ushr 32).toInt()
    }

    override fun getNumHashElements(): Int {
        return chunkMap.size()
    }

    override fun getValueByKey(key: Long): Any? {
        return chunkMap.get(v2x(key), v2z(key))
    }

    override fun containsItem(key: Long): Boolean {
        return chunkMap.contains(v2x(key), v2z(key))
    }

    override fun add(key: Long, obj: Any) {
        chunkMap.put(v2x(key), v2z(key), obj as Chunk)
    }

    override fun remove(key: Long): Any? {
        return chunkMap.remove(v2x(key), v2z(key))
    }
}