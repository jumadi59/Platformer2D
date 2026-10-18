package com.example.platformer

import com.jumbox.game.core.scene.BehaviorComponent
import com.jumbox.game.core.scene.RuntimeSceneRunner
import com.jumbox.game.core.scene.SceneDimension
import com.jumbox.game.core.scene.SchemaBehavior
import com.jumbox.game.core.scene.UiCanvasComponent

/**
 * Owns the in-game pause dialog. The HUD only carries a pause button; Resume, Retry, Settings
 * and Main Menu live on this hidden canvas, shown/hidden through its UiCanvas show/hide triggers.
 * While open, gameplay canvases stop taking input and [Platformer2DGame] freezes simulation time.
 */
class PauseMenuBehavior : SchemaBehavior() {
    override val supportedDimensions = setOf(SceneDimension.TWO_D)
    override val requiredComponents = listOf("UiCanvas")
    var pauseAction: String by stringProperty("pauseAction", "Toggle Pause Action", "pause")
    var showTrigger: String by stringProperty("showTrigger", "Show Dialog Trigger", "pause_open")
    var hideTrigger: String by stringProperty("hideTrigger", "Hide Dialog Trigger", "pause_close")
    var gameplayCanvases: String by stringProperty("gameplayCanvases", "Gameplay Canvas IDs (comma separated)", "hud,gameplay-controls")

    var isOpen = false
        private set
    /** Requested state, applied in [onUpdate] so a canvas is never hidden inside its own pointer dispatch. */
    private var requestedOpen: Boolean? = null
    private var lastToggleNanos = 0L

    override fun onStart() {
        sound.preload("sfx/button.wav")
        close()
    }

    override fun onInput(action: String, isDown: Boolean) {
        if (!isDown) return
        when (action) {
            pauseAction -> toggle()
            // UI buttons use the canvas show/hide triggers directly, so no Scene Flow route is needed.
            showTrigger -> requestedOpen = true
            // PlayerBehavior performs the actual level reset on "restart"; resume so its fixed step runs.
            hideTrigger, "restart" -> { requestedOpen = false; lastToggleNanos = 0L }
        }
    }

    override fun onUpdate(delta: Float) {
        val wanted = requestedOpen ?: return
        requestedOpen = null
        if (wanted) open() else close()
    }

    /** Escape/Back may arrive as both a host back() and a bound action in the same frame; debounce them. */
    fun toggle() {
        val now = System.nanoTime()
        if (now - lastToggleNanos < TOGGLE_DEBOUNCE_NANOS) return
        lastToggleNanos = now
        requestedOpen = !(requestedOpen ?: isOpen)
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        setGameplayInput(false)
        context.runner.cancelGameInput()
        context.runner.trigger(showTrigger)
    }

    fun close() {
        isOpen = false
        setGameplayInput(true)
        context.runner.trigger(hideTrigger)
    }

    override fun onDestroy() {
        isOpen = false; requestedOpen = null
    }

    private fun setGameplayInput(enabled: Boolean) {
        for (id in gameplayCanvases.split(',').map(String::trim).filter(String::isNotEmpty)) {
            context.runner.spawnedEntities[id]?.get<UiCanvasComponent>()?.inputEnabled = enabled
        }
    }

    companion object {
        private const val TOGGLE_DEBOUNCE_NANOS = 150_000_000L

        fun find(runner: RuntimeSceneRunner): PauseMenuBehavior? = runner.spawnedEntities.values.firstNotNullOfOrNull { entity ->
            entity.get<BehaviorComponent>()?.allInstances()?.filterIsInstance<PauseMenuBehavior>()?.firstOrNull()
        }

        fun isOpen(runner: RuntimeSceneRunner): Boolean = find(runner)?.isOpen == true
    }
}
