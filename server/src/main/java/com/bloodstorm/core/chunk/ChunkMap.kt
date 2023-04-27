package com.bloodstorm.core.chunk

import com.bloodstorm.core.util.ChunkHash.chunkToKey
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectCollection
import it.unimi.dsi.fastutil.objects.ObjectIterator
import it.unimi.dsi.fastutil.objects.ObjectSet
import net.minecraft.world.chunk.Chunk


class ChunkMap {
    val map = Int2ObjectOpenHashMap<Chunk>()

    fun put(x: Int, z: Int, chunk: Chunk) = put(chunkToKey(x, z), chunk)

    fun put(key: Int, chunk: Chunk) {
        map[key] = chunk
    }

    fun get(x: Int, z: Int): Chunk? = get(chunkToKey(x, z))

    fun get(key: Int): Chunk? = map[key]
    fun remove(x: Int, z: Int): Chunk? {
        return map.remove(chunkToKey(x, z))
    }

    fun remove(hash: Int): Chunk? {
        return map.remove(hash)
    }

    fun contains(x: Int, z: Int): Boolean {
        return map.containsKey(chunkToKey(x, z))
    }

    operator fun contains(hash: Int): Boolean {
        return map.containsKey(hash)
    }

    operator fun iterator(): ObjectIterator<Chunk> {
        return map.values.iterator()
    }

    fun entrySet(): ObjectSet<Int2ObjectMap.Entry<Chunk>> {
        return map.int2ObjectEntrySet()
    }

    fun valueCollection(): ObjectCollection<Chunk> {
        return map.values
    }

    fun size(): Int {
        return map.size
    }

    fun clear() {
        map.clear()
    }
}