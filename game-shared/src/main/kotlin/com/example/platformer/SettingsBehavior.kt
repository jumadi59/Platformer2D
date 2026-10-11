package com.example.platformer

import com.jumbox.game.core.audio.AudioBus
import com.jumbox.game.core.audio.AudioMixerSettings
import com.jumbox.game.core.scene.SchemaBehavior
import com.jumbox.game.core.scene.SceneDimension
import kotlin.math.roundToInt

/** +/- controls reflect the live mixer, including when returning from a paused game. */
class SettingsBehavior : SchemaBehavior() {
    override val supportedDimensions = setOf(SceneDimension.TWO_D)
    override val requiredComponents = listOf("UiCanvas")
    private var status = "Changes save automatically"
    private val displayed = mutableMapOf<String, String>()

    override fun onStart() {
        AudioSettings.apply(sound.mixer)
        sound.preload("sfx/coin.wav")
        sound.preload("sfx/button.wav")
    }

    override fun onInput(action: String, isDown: Boolean) {
        if (!isDown) return
        if (action == "pause") { context.sceneNavigator.trigger("back"); return }
        if (action == "test_sfx") { sound.oneShot("sfx/coin.wav"); return }
        val mixer = sound.mixer
        when (action) {
            "mute_audio" -> mixer.setMuted(AudioBus.MASTER, !mixer.isMuted(AudioBus.MASTER))
            "reset_audio" -> mixer.apply(AudioMixerSettings.DEFAULT)
            else -> {
                val bus = when {
                    action.startsWith("master_") -> AudioBus.MASTER
                    action.startsWith("sfx_") -> AudioBus.SFX
                    action.startsWith("ui_") -> AudioBus.UI
                    else -> return
                }
                val direction = when {
                    action.endsWith("_up") -> 1
                    action.endsWith("_down") -> -1
                    else -> return
                }
                val percent = ((mixer.gain(bus) * 100).roundToInt() + direction * 10).coerceIn(0, 100)
                mixer.setGain(bus, percent / 100f)
            }
        }
        status = if (AudioSettings.save(mixer.settings)) "Saved automatically" else "Applied for this session; could not save preferences"
    }

    override fun onUpdate(delta: Float) {
        for (bus in AudioBus.entries) {
            display("${bus.name.lowercase()}-value", "${(sound.mixer.gain(bus) * 100).roundToInt()}%")
        }
        display("mute-status", if (sound.mixer.isMuted(AudioBus.MASTER)) "All audio muted" else "Audio enabled")
        display("save-status", status)
    }

    private fun display(id: String, text: String) {
        if (displayed[id] != text && context.runner.setUiText("settings", id, text)) displayed[id] = text
    }
}
