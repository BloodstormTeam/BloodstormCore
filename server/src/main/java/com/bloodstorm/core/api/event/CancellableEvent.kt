package com.bloodstorm.core.api.event

import me.bush.eventbuskotlin.Event

open class CancellableEvent : Event() {
    override val cancellable = true

    fun post(): Boolean {
        return !EventBusDefinite.advancedApiEventBus.post(this)
    }
}