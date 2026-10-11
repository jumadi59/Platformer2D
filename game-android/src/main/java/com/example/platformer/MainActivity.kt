package com.example.platformer

import com.jumbox.game.android.GameActivity
import com.jumbox.game.core.Game
import com.jumbox.game.core.Engine
import android.view.KeyEvent

class MainActivity : GameActivity() {
    override fun startGame(): Game {
        Engine.game.actions.apply {
            bindKey("move_left", KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT)
            bindKey("move_right", KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT)
            bindKey("jump", KeyEvent.KEYCODE_SPACE)
            bindKey("restart", KeyEvent.KEYCODE_R)
            bindKey("pause", KeyEvent.KEYCODE_ESCAPE)
        }
        return Platformer2DGame()
    }
}
