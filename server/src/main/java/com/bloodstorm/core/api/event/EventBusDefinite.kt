package com.bloodstorm.core.api.event

import me.bush.eventbuskotlin.Event
import me.bush.eventbuskotlin.EventBus
import me.bush.eventbuskotlin.listener

object EventBusDefinite {
    val advancedApiEventBus = EventBus()
}

object EventBuilder {
    inline fun <reified T : Event> register(noinline block: (T) -> Unit) {
        val eventListener = listener<T> { block(it) }
        EventBusDefinite.advancedApiEventBus.register(eventListener)
    }
}

fun eventScope(block: EventBuilder.() -> Unit) {
    block(EventBuilder)
}