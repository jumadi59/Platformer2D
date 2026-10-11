package com.example.platformer

import com.jumbox.game.core.Game
import com.jumbox.game.core.Engine
import com.jumbox.game.core.scene.ProjectSceneLoader
import com.jumbox.game.core.scene.RuntimeScene
import com.jumbox.game.core.screen.Scene
import com.jumbox.game.core.screen.SceneTransition

/** Entry point shared by target platforms - loads startup scene from project manifest. */
class Platformer2DGame : Game() {
    override fun start() {
        Engine.game.showFpsPreview = true
        // start() runs before Engine.currentGame is assigned. Pass the owner so
        // Scene Flow installs its dispatcher on this game and queued routes drain.
        setInitialScene(ProjectSceneLoader.loadStartupScene(game = this))
    }

    override fun update(delta: Float) {
        super.update(delta)
        installPointerAdapter()
    }

    private var pointerAdapter: RuntimePointerAdapter? = null

    private fun installPointerAdapter() {
        val scene = getScene() as? RuntimeScene ?: return
        // 0.7.16's TouchHandler sends bottom-origin coordinates, while
        // RuntimeScene expects top-origin before its own UI conversion.
        if (Engine.game.input.touchController === scene) {
            pointerAdapter = RuntimePointerAdapter(scene)
            Engine.game.input.touchController = pointerAdapter
        }
    }

    private fun restorePointerDelegate() {
        pointerAdapter?.let { adapter ->
            if (Engine.game.input.touchController === adapter) {
                Engine.game.input.touchController = adapter.scene
            }
        }
        pointerAdapter = null
    }

    override fun pushScene(scene: Scene, transition: SceneTransition?, isRecord: Boolean) {
        restorePointerDelegate()
        super.pushScene(scene, transition, isRecord)
        installPointerAdapter()
    }

    override fun replaceScene(scene: Scene, transition: SceneTransition?) {
        restorePointerDelegate()
        super.replaceScene(scene, transition)
        installPointerAdapter()
    }

    override fun popScene(transition: SceneTransition?): Boolean {
        restorePointerDelegate()
        return super.popScene(transition).also { installPointerAdapter() }
    }

    override fun pause() {
        restorePointerDelegate()
        super.pause()
    }

    override fun show() {
        restorePointerDelegate()
        super.show()
        installPointerAdapter()
    }

    override fun dispose() {
        restorePointerDelegate()
        super.dispose()
    }

    // Desktop Escape / Android Back are host navigation commands, not action-map keys.
    override fun back(): Boolean {
        val current = getScene() as? RuntimeScene
        if (current?.sceneDef?.entities?.any { it.id == "player" } == true) {
            return !current.runner.sceneNavigator.trigger("settings")
        }
        return super.back()
    }
}
