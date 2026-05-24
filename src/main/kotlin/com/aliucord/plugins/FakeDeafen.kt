package com.aliucord.plugins

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.patcher.after
import com.discord.models.user.CoreUser
import com.discord.stores.StoreStream

/**
 * FakeDeafen Plugin
 * 
 * Allows users to appear deafened in Discord without actually being deafened.
 * This lets you use voice communication while appearing unavailable to others.
 */
@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class FakeDeafen : Plugin() {
    
    private var isFakeDeafened = false

    override fun start(context: Context) {
        // Patch the method that returns the deafen status
        try {
            patcher.after<CoreUser>(
                "isDeafened"
            ) { param ->
                if (isFakeDeafened) {
                    param.result = true
                }
            }
        } catch (e: Throwable) {
            logger.error("Failed to patch isDeafened", e)
        }

        // Register a command to toggle fake deafen
        try {
            commands.registerCommand(
                "fakedeafen",
                "Toggle fake deafen status"
            ) { ctx ->
                isFakeDeafened = !isFakeDeafened
                val status = if (isFakeDeafened) "enabled" else "disabled"
                com.aliucord.api.CommandsAPI.CommandResult(
                    "Fake deafen has been **$status**",
                    null,
                    false
                )
            }
        } catch (e: Throwable) {
            logger.error("Failed to register command", e)
        }
    }

    override fun stop(context: Context) {
        // Reset fake deafen status
        isFakeDeafened = false
        patcher.unpatchAll()
    }
}
