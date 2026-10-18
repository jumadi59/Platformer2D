package com.example.platformer

import com.jumbox.game.core.control.InputPointerController
import com.jumbox.game.core.scene.RuntimeScene

/** Preserve TouchHandler's game-space pointer contract; Layer owns the UI Y conversion. */
internal class RuntimePointerAdapter(val scene: RuntimeScene) : InputPointerController {
    override fun pointerDown(pointerId: Int, screenX: Int, screenY: Int) =
        scene.pointerDown(pointerId, screenX, screenY)
    override fun pointerUp(pointerId: Int, screenX: Int, screenY: Int) =
        scene.pointerUp(pointerId, screenX, screenY)
    override fun pointerMoved(pointerId: Int, screenX: Int, screenY: Int, dx: Float, dy: Float) =
        scene.pointerMoved(pointerId, screenX, screenY, dx, dy)
    override fun pointerCancelled(pointerId: Int) = scene.pointerCancelled(pointerId)
    override fun touchScroll(dx: Float, dy: Float) = scene.touchScroll(dx, dy)
    override fun fling(velocityX: Float, velocityY: Float) = scene.fling(velocityX, velocityY)
}
