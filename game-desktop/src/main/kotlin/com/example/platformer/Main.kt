package com.example.platformer

import com.jumbox.game.core.Game
import com.jumbox.game.core.GameCallback
import com.jumbox.game.core.Engine
import org.lwjgl.glfw.GLFW.*
import com.jumbox.game.desktop.DesktopConfig
import com.jumbox.game.desktop.DesktopGameHost

fun main(args: Array<String>) {
    if ("--help" in args) {
        println("Platformer2D [--fps=auto|120|240] [--no-vsync|--vsync]")
        return
    }
    var targetFps = 0f
    var vsync = true
    for (arg in args) when {
        arg == "--no-vsync" -> vsync = false
        arg == "--vsync" -> vsync = true
        arg.startsWith("--fps=") -> {
            val value = arg.substringAfter('=')
            targetFps = if (value == "auto") 0f else value.toFloatOrNull()
                ?.takeIf { it.isFinite() && it > 0f }
                ?: error("Invalid FPS '$value'; use auto or a positive frame rate")
        }
        else -> error("Unknown option '$arg'; use --help")
    }
    lateinit var host: DesktopGameHost
    host = DesktopGameHost(
        gameCallback = object : GameCallback {
            override fun exit() = host.stop()
            override fun startGame(): Game {
                Engine.game.actions.apply {
                    bindKey("move_left", GLFW_KEY_A, GLFW_KEY_LEFT)
                    bindKey("move_right", GLFW_KEY_D, GLFW_KEY_RIGHT)
                    bindKey("jump", GLFW_KEY_SPACE)
                    bindKey("restart", GLFW_KEY_R)
                    bindKey("pause", GLFW_KEY_ESCAPE)
                }
                return Platformer2DGame()
            }
        },
        config = DesktopConfig(title = "Platformer2D", width = 960, height = 540,
            targetFramesPerSecond = targetFps, vsync = vsync)
    )
    host.run()
}
