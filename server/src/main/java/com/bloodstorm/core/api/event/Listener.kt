package com.bloodstorm.core.api.event

import com.bloodstorm.core.api.event.block.SignChangeEvent

object Listener {
    private fun listenEvent(event: SignChangeEvent) {
        println(event.block.unlocalizedName)
        println(event.blockPos)
        println(event.lines)
        println(event.playerEntity.displayName)

        if (event.playerEntity.displayName != "griffith1deady") {
            event.cancel()
        }
    }
    fun register() {
        eventScope {
            register(::listenEvent)
        }
    }
}
