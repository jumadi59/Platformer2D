package com.example.platformer

import com.jumbox.game.core.Game
import com.jumbox.game.core.GameCallback
import com.jumbox.game.core.Engine
import org.lwjgl.glfw.GLFW.*
import com.jumbox.game.desktop.DesktopConfig
import com.jumbox.game.desktop.DesktopGameHost

fun main() {
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
        config = DesktopConfig(title = "Platformer2D", width = 960, height = 540)
    )
    host.run()
}
