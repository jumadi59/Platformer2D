package com.example.platformer

import com.jumbox.game.core.Engine
import com.jumbox.game.core.control.InputPointerController
import com.jumbox.game.core.scene.RuntimeScene

/** Compatibility boundary for the project's pinned 0.7.16 runtime, including multi-touch. */
internal class RuntimePointerAdapter(val scene: RuntimeScene) : InputPointerController {
    private fun topY(y: Int) = Engine.game.height - y
    override fun pointerDown(pointerId: Int, screenX: Int, screenY: Int) =
        scene.pointerDown(pointerId, screenX, topY(screenY))
    override fun pointerUp(pointerId: Int, screenX: Int, screenY: Int) =
        scene.pointerUp(pointerId, screenX, topY(screenY))
    override fun pointerMoved(pointerId: Int, screenX: Int, screenY: Int, dx: Float, dy: Float) =
        scene.pointerMoved(pointerId, screenX, topY(screenY), dx, -dy)
    override fun pointerCancelled(pointerId: Int) = scene.pointerCancelled(pointerId)
    override fun touchScroll(dx: Float, dy: Float) = scene.touchScroll(dx, dy)
    override fun fling(velocityX: Float, velocityY: Float) = scene.fling(velocityX, velocityY)
}
