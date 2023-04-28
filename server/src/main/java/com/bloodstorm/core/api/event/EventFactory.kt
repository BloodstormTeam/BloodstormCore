package com.bloodstorm.core.api.event

import com.bloodstorm.core.api.event.block.BlockRedstoneEvent
import com.bloodstorm.core.api.event.block.EntityBlockFormEvent
import com.bloodstorm.core.api.event.block.SignChangeEvent
import net.minecraft.block.Block
import net.minecraft.entity.EntityLiving
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.world.World
import net.optifine.BlockPos

object EventFactory {
    private val blockPos = BlockPos(0, 0, 0)
    private val entityBlockFormEvent = EntityBlockFormEvent(Blocks.diamond_block, null, blockPos)
    private val blockRedstoneEvent = BlockRedstoneEvent(Blocks.diamond_block, blockPos, 0, 0)
    private val signChangeEvent = SignChangeEvent(Blocks.diamond_block, blockPos, null, arrayOf())

    @JvmStatic
    fun postEntityBlockFormEvent(x: Int, y: Int, z: Int, block: Block, entityLiving: EntityLiving): EntityBlockFormEvent {
        entityBlockFormEvent.block = block
        entityBlockFormEvent.blockPos = blockPos.set(x, y, z)
        entityBlockFormEvent.livingEntity = entityLiving
        entityBlockFormEvent.post()
        return entityBlockFormEvent
    }
    @JvmStatic
    fun postRedstoneEvent(world: World, x: Int, y: Int, z: Int, oldValue: Int, newValue: Int): BlockRedstoneEvent {
        blockRedstoneEvent.block = world.getBlock(x, y, z)
        blockRedstoneEvent.blockPos = blockPos.set(x, y, z)
        blockRedstoneEvent.oldValue = oldValue
        blockRedstoneEvent.newValue = newValue
        blockRedstoneEvent.post()
        return blockRedstoneEvent
    }

    @JvmStatic
    fun postSignChangeEvent(world: World, x: Int, y: Int, z: Int, playerEntity: EntityPlayer, lines: Array<String>): SignChangeEvent {
        signChangeEvent.block = world.getBlock(x, y, z)
        signChangeEvent.blockPos = blockPos.set(x, y, z)
        signChangeEvent.playerEntity = playerEntity
        signChangeEvent.lines = lines
        signChangeEvent.post()
        return signChangeEvent
    }
}