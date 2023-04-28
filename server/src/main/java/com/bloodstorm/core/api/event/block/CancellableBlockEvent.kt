package com.bloodstorm.core.api.event.block

import com.bloodstorm.core.api.event.CancellableEvent
import net.minecraft.block.Block

open class CancellableBlockEvent(block: Block) : CancellableEvent() {
    var block: Block = block
        internal set
}