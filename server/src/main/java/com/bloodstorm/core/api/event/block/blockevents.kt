package com.bloodstorm.core.api.event.block

import com.bloodstorm.core.api.event.CancellableEvent
import net.minecraft.block.Block
import net.minecraft.entity.EntityLiving
import net.minecraft.entity.player.EntityPlayer
import net.optifine.BlockPos
import org.bukkit.Instrument
import org.bukkit.Note
import org.bukkit.block.BlockState

open class BlockRedstoneEvent(block: Block, blockPos: BlockPos, oldValue: Int, newValue: Int) : CancellableBlockEvent(block) {
    var blockPos: BlockPos = blockPos
        internal set
    var oldValue: Int = oldValue
        internal set
    var newValue: Int = newValue
        internal set
}
class EntityBlockFormEvent(var block: Block, livingEntity: EntityLiving?, blockPos: BlockPos) : CancellableEvent() {
    private var entityLiving: EntityLiving? = livingEntity

    var livingEntity: EntityLiving
        get() = entityLiving!!
        internal set(value) {
            entityLiving = value
        }
    var blockPos: BlockPos = blockPos
        internal set
}
class LeavesDecayEvent(block: Block, val blockPos: BlockPos): CancellableBlockEvent(block)
class NotePlayEvent(block: Block, var instrument: Instrument?, var note: Note?) : CancellableBlockEvent(block)
class SignChangeEvent(block: Block, blockPos: BlockPos, playerEntity: EntityPlayer?, lines: Array<String>) : CancellableBlockEvent(block) {
    var blockPos: BlockPos = blockPos
        internal set

    private var entityPlayer: EntityPlayer? = playerEntity
    var playerEntity: EntityPlayer
        get() = entityPlayer!!
        set(value) {
            entityPlayer = value
        }
    var lines: Array<String> = lines
        internal set
    @Throws(ArrayIndexOutOfBoundsException::class)
    fun getLine(index: Int): String {
        return lines[index]
    }
}
