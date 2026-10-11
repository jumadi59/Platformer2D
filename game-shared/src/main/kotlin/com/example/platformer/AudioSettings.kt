package com.example.platformer

import com.jumbox.game.core.audio.AudioMixer
import com.jumbox.game.core.audio.AudioMixerSettings
import com.jumbox.game.core.storage.SaveCodec
import com.jumbox.game.core.storage.SaveGameStore
import com.jumbox.game.core.util.Files
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.WeakHashMap

/** Local player preferences, never written into the authored project/assets. */
object AudioSettings {
    private val initializedMixers = WeakHashMap<AudioMixer, Boolean>()
    private fun store(file: File) = SaveGameStore(file, 1, object : SaveCodec<AudioMixerSettings> {
        override fun write(output: DataOutputStream, value: AudioMixerSettings) = output.writeUTF(value.encode())
        override fun read(input: DataInputStream, savedVersion: Int) = AudioMixerSettings.parse(input.readUTF())
    })

    fun load(file: File = Files.local("platformer2d/audio-settings.bin").file): AudioMixerSettings =
        store(file).let { if (it.exists) runCatching { it.load() }.getOrDefault(AudioMixerSettings.DEFAULT) else AudioMixerSettings.DEFAULT }

    fun save(settings: AudioMixerSettings, file: File = Files.local("platformer2d/audio-settings.bin").file): Boolean =
        runCatching { store(file).save(settings) }.isSuccess

    /** Each game mixer loads once; unsaved live adjustments survive scene changes too. */
    fun apply(mixer: AudioMixer) {
        synchronized(initializedMixers) {
            if (initializedMixers.put(mixer, true) == null) mixer.apply(load())
        }
    }
}
