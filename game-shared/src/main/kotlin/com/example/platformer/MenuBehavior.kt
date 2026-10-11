package com.example.platformer

import com.jumbox.game.core.scene.SchemaBehavior
import com.jumbox.game.core.scene.SceneDimension

class MenuBehavior : SchemaBehavior() {
    override val supportedDimensions = setOf(SceneDimension.TWO_D)
    override val requiredComponents = listOf("UiCanvas")

    override fun onStart() {
        AudioSettings.apply(sound.mixer)
        sound.preload("sfx/button.wav")
    }

    override fun onInput(action: String, isDown: Boolean) {
        if (isDown && action == "confirm") context.sceneNavigator.trigger("play")
    }
}
