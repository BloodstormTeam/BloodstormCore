package com.bloodstorm.core

import net.minecraft.server.MinecraftServer
fun isServerSide(): Boolean = MinecraftServer.getServer() != null
fun executeOnServerSide(block: (MinecraftServer) -> Runnable) {
    if (MinecraftServer.getServer() != null) {
        block(MinecraftServer.getServer()).run()
    }
}