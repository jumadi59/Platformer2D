package com.example.platformer

import com.jumbox.game.core.audio.*
import com.jumbox.game.core.ecs.Transform2D
import com.jumbox.game.core.graphics.Texture
import com.jumbox.game.core.graphics.testing.RecordingRenderer2D
import com.jumbox.game.core.physics2d.*
import com.jumbox.game.core.platform.*
import com.jumbox.game.core.project.*
import com.jumbox.game.core.scene.*
import com.jumbox.game.core.ui.document.*
import com.jumbox.game.core.util.*
import com.jumbox.game.desktop.DesktopEngineBootstrap
import com.jumbox.generated.ProjectBehaviorInitializer
import java.io.File
import java.nio.file.Files as NioFiles
import kotlin.test.*

/** Script/scene integration and recorded sound requests; this is not audible/device acceptance. */
class PlatformerIntegrationTest {
    private val assets = File(System.getProperty("platformer.assets"))
    private lateinit var temporary: File
    private lateinit var previousFiles: PlatformFileSystem

    @BeforeTest fun setup() {
        DesktopEngineBootstrap.initializeTooling()
        temporary = NioFiles.createTempDirectory("platformer-preferences-test-").toFile()
        previousFiles = Files.fileSystem
        Files.install(ClasspathPlatformFileSystem(temporary, temporary))
    }
    @AfterTest fun cleanup() { Files.install(previousFiles); temporary.deleteRecursively() }

    private class RecordedAudio : PlatformAudio {
        val plays = mutableListOf<Pair<String, Float>>()
        override fun loadSound(file: FileHandle) = object : PlatformSound {
            private val managed = object : ManagedPlatformSound({ it() }) {
                override fun startVoice(parameters: SoundParameters): SoundVoice {
                    plays += file.name to parameters.left
                    return object : SoundVoice {
                        override val completionObservable = true
                        override val completed = false
                        override val supportsVolume = true
                        override fun setVolume(left: Float, right: Float) = Unit
                        override fun pause() = Unit
                        override fun resume() = Unit
                        override fun stop() = Unit
                    }
                }
            }.also { it.ready() }
            override val isLoaded = true
            override val supportsPlaybackHandles = true
            override fun requestPlay(parameters: SoundParameters) = managed.requestPlay(parameters)
            override fun play(leftVolume: Float, rightVolume: Float, loop: Int) = managed.play(leftVolume, rightVolume, loop)
            override fun pause() = managed.pause()
            override fun resume() = managed.resume()
            override fun stop() = managed.stop()
            override fun close() = managed.close()
        }
        override fun pauseAll() = Unit
        override fun resumeAll() = Unit
        override fun release() = Unit
        fun count(name: String) = plays.count { it.first == name }
    }
    private class Routes : SceneNavigator {
        val triggered = mutableListOf<String>()
        override fun trigger(name: String): Boolean { triggered += name; return true }
        override fun hasRoute(name: String) = true
        override fun push(sceneId: String) = true
        override fun replace(sceneId: String) = true
        override fun pop() = true
        override fun reload() = true
    }
    private fun resolver() = object : RuntimeAssetResolver by DefaultRuntimeAssetResolver(assets) {
        override fun resolveAssetBytes(assetPath: String): ByteArray? = File(assets, assetPath).takeIf(File::isFile)?.readBytes()
        override fun resolveAudio(assetPath: String): FileHandle? = File(assets, assetPath).takeIf(File::isFile)?.let { Files.external(it.path) }
        override fun resolveUiDocument(assetPath: String) = File(assets, assetPath).takeIf(File::isFile)?.let { UiDocumentCodec.parse(it.readText()) }
    }
    private fun runner(name: String, audio: RecordedAudio = RecordedAudio(), routes: Routes = Routes()): RuntimeSceneRunner {
        val registry = BehaviorRegistry().also(ProjectBehaviorInitializer::register)
        return RuntimeSceneRunner(SceneDocument.parse(File(assets, "scenes/$name.scene").readText()), assetResolver = resolver(), audio = audio,
            behaviorRegistry = registry, sceneNavigator = routes).also { it.start(); it.stepOnce() }
    }
    private fun RuntimeSceneRunner.player() = spawnedEntities.getValue("player").get<BehaviorComponent>()!!.allInstances().filterIsInstance<PlayerBehavior>().single()
    private fun contact(other: String, phase: PhysicsContactPhase = PhysicsContactPhase.ENTER) = PhysicsContactEvent("player", other, phase, true, false)

    @Test fun authoredMenuSettingsAndGameplayDocumentsValidateAndRoutesResolve() {
        val catalog = SceneCatalogDocument.parse(File(assets, "scenes.catalog").readText())
        val flow = SceneFlowDocument.parse(File(assets, "scene-flow.json").readText())
        assertTrue(SceneCatalogValidator.validate(catalog) { path -> File(assets.parentFile.parentFile, path).isFile }.isEmpty())
        assertTrue(SceneFlowValidator.validate(flow, catalog).isEmpty())
        assertEquals("Menu", SceneDocument.parse(File(assets, "scenes/Menu.scene").readText()).name)
        assertEquals(catalog.startupSceneId, SceneDocument.parse(File(assets, "scenes/Menu.scene").readText()).sceneId)
        for (path in listOf("menu", "settings", "platformer_hud")) {
            val doc = UiDocumentCodec.parse(File(assets, "ui/layouts/$path.jui").readText())
            assertTrue(UiDocumentValidator.validate(doc).none { it.severity == UiDiagnostic.Severity.ERROR }, "$path UI is invalid: ${UiDocumentValidator.validate(doc)}")
            assertEquals(doc, UiDocumentCodec.parse(UiDocumentCodec.write(doc)))
        }
        assertEquals(setOf("menu-play", "menu-settings", "game-settings", "game-menu", "settings-back"), flow.routes.map { it.id }.toSet())
    }

    @Test fun gemCheckpointFinishOnlyPlayOnceAndRestartRestoresLevel() {
        val audio = RecordedAudio(); val r = runner("Main", audio)
        try {
            val p = r.player()
            repeat(3) { p.onPhysicsContact(contact("gem-1")) }
            assertEquals(1, audio.count("coin.wav"))
            assertFalse(r.spawnedEntities.getValue("gem-1").get<Sprite2DComponent>()!!.isEnabled)
            repeat(3) { p.onPhysicsContact(contact("checkpoint")) }
            assertEquals(1, audio.count("health.wav"))
            p.onPhysicsContact(contact("goal")); assertEquals(0, audio.count("finish.wav"))
            for (i in 2..6) p.onPhysicsContact(contact("gem-$i"))
            repeat(3) { p.onPhysicsContact(contact("goal", PhysicsContactPhase.STAY)) }
            assertEquals(6, audio.count("coin.wav")); assertEquals(1, audio.count("finish.wav"))
            r.dispatchInput("restart", true); r.stepOnce()
            assertTrue((1..6).all { r.spawnedEntities.getValue("gem-$it").get<Sprite2DComponent>()!!.isEnabled })
            p.onPhysicsContact(contact("gem-1")); assertEquals(7, audio.count("coin.wav"))
        } finally { r.dispose() }
    }

    @Test fun repeatedHazardAndFallInSameStepProduceOneRespawn() {
        val audio = RecordedAudio(); val r = runner("Main", audio)
        try {
            val p = r.player()
            r.spawnedEntities.getValue("player").get<RigidBody2DComponent>()!!.body!!.position = Vec2(0f, -400f)
            repeat(4) { p.onPhysicsContact(contact("hazard")) }
            r.stepOnce()
            assertEquals(1, audio.count("respawn.wav"))
            val body = r.spawnedEntities.getValue("player").get<RigidBody2DComponent>()!!.body!!
            assertEquals(p.spawnX, body.position.x, 0.01f)
            assertTrue(body.position.y > 350f)
        } finally { r.dispose() }
    }

    @Test fun acceptedJumpPlaysOnceAndCancellationClearsStickAndJumpEdge() {
        val audio = RecordedAudio(); val r = runner("Main", audio)
        try {
            val p = r.player()
            fun grounded() { p.onPhysicsContact(PhysicsContactEvent("player", "floor", PhysicsContactPhase.STAY, false, false, normal = Vec2(0f,-1f))) }
            grounded(); r.dispatchInput("jump", true); r.stepOnce()
            assertEquals(1, audio.count("jump.wav"))
            r.stepOnce(); assertEquals(1, audio.count("jump.wav"))
            p.onUiAction(UiActionEvent("move-stick", "move_stick", payload = mapOf("x" to UiValue.NumberValue(1.0)), event = UiActionEventType.VALUE_CHANGED))
            p.onInputCancelled(); r.stepOnce()
            assertEquals(0f, r.spawnedEntities.getValue("player").get<RigidBody2DComponent>()!!.body!!.velocity.x)
            r.dispatchInput("jump", false); grounded(); r.dispatchInput("jump", true); r.stepOnce()
            assertEquals(2, audio.count("jump.wav"))
        } finally { r.dispose() }
    }

    @Test fun volumeMuteAndPreferencesSurviveSceneChangesAndRespectBusGains() {
        val audio = RecordedAudio(); val routes = Routes(); val r = runner("Settings", audio, routes)
        try {
            r.dispatchInput("master_down", true); r.dispatchInput("master_down", false)
            r.dispatchInput("sfx_down", true); r.dispatchInput("sfx_down", false)
            r.dispatchInput("test_sfx", true)
            assertEquals(0.81f, audio.plays.last().second, 0.001f)
            assertEquals(0.9f, AudioSettings.load().gain(AudioBus.SFX), 0.001f)
            r.dispatchInput("mute_audio", true); r.dispatchInput("test_sfx", false); r.dispatchInput("test_sfx", true)
            assertEquals(0f, audio.plays.last().second, "Muted voice has zero output gain")
            r.dispatchInput("pause", true); assertEquals(listOf("back"), routes.triggered)
        } finally { r.dispose() }
        val main = runner("Main")
        try { assertTrue(main.audioMixer.isMuted(AudioBus.MASTER)); assertEquals(0.9f, main.audioMixer.gain(AudioBus.SFX), 0.001f) }
        finally { main.dispose() }
    }

    @Test fun corruptPreferencesUseBackupAndFullyInvalidPreferencesUseDefaults() {
        val file = File(temporary, "prefs.bin")
        assertTrue(AudioSettings.save(AudioMixerSettings.DEFAULT.withGain(AudioBus.SFX, 0.4f), file))
        assertTrue(AudioSettings.save(AudioMixerSettings.DEFAULT.withGain(AudioBus.SFX, 0.6f), file))
        file.writeText("invalid")
        assertEquals(0.4f, AudioSettings.load(file).gain(AudioBus.SFX), 0.001f)
        File(temporary,"prefs.bin.bak").writeText("invalid")
        assertEquals(AudioMixerSettings.DEFAULT, AudioSettings.load(file))
    }

    @Test fun runtimeMenuPointerClickRequestsUiSoundAndSettingsAction() {
        val audio = RecordedAudio(); val routes = Routes(); val r = runner("Menu", audio, routes)
        try {
            r.render(RecordingRenderer2D(), 960, 540)
            val bounds = r.uiAccessibilityNodes().single { it.semantics.label == "Settings" }.bounds
            val x = (bounds.x + bounds.width / 2).toInt(); val y = 540 - (bounds.y + bounds.height / 2).toInt()
            assertTrue(r.pointerDown(0, x, y)); assertTrue(r.pointerUp(0,x,y))
            assertTrue("settings" in routes.triggered)
            assertEquals(1, audio.count("button.wav"))
            assertTrue(r.diagnostics.none { it.severity == DiagnosticSeverity.ERROR }, r.diagnostics.toString())
        } finally { r.dispose() }
    }

    @Test fun actualEntrypointOwnsDispatcherAndSettingsPauseResumePreservesGameplay() {
        val game = Platformer2DGame()
        try {
            game.start()
            assertNotNull(game.navigationDispatcher, "Routes must be attached during start, before Engine.currentGame exists")
            game.update(10f)
            val menu = assertIs<RuntimeScene>(game.getScene())
            assertEquals("Menu", menu.sceneDef.name)
            assertTrue(menu.runner.sceneNavigator.trigger("play")); game.update(0f)
            val main = assertIs<RuntimeScene>(game.getScene())
            assertEquals("Main", main.sceneDef.name)
            main.runner.player().onPhysicsContact(contact("gem-1"))
            assertFalse(game.back(), "Back/Escape opens settings instead of exiting gameplay"); game.update(0f)
            val settings = assertIs<RuntimeScene>(game.getScene())
            assertEquals("Settings", settings.sceneDef.name); assertTrue(main.runner.isPaused)
            assertTrue(settings.runner.sceneNavigator.trigger("back")); game.update(0f)
            assertSame(main, game.getScene()); assertFalse(main.runner.isPaused)
            assertFalse(main.runner.spawnedEntities.getValue("gem-1").get<Sprite2DComponent>()!!.isEnabled)
        } finally { game.dispose() }
    }

    @Test fun liveMixerIsNotOverwrittenByStaleSaveDuringSceneStartup() {
        val mixer = AudioMixer()
        AudioSettings.apply(mixer)
        mixer.setGain(AudioBus.SFX, 0.2f)
        AudioSettings.apply(mixer)
        assertEquals(0.2f, mixer.gain(AudioBus.SFX), 0.001f)
    }
    @Test fun nativePointerRoutesThroughTouchHandlerIntoVisibleMenuButton() {
        val engine = com.jumbox.game.core.Engine.game
        engine.resize(960, 540)
        val game = Platformer2DGame()
        try {
            game.start(); game.update(10f); game.update(0f)
            val menu = assertIs<RuntimeScene>(game.getScene())
            menu.runner.render(RecordingRenderer2D(), 960, 540)
            val bounds = menu.runner.uiAccessibilityNodes().single { it.semantics.label == "Settings" }.bounds
            engine.input.touch((bounds.x + bounds.width / 2).toInt(), (bounds.y + bounds.height / 2).toInt())
            assertTrue(engine.input.down(), "Visible Settings button must accept host pointer")
            assertTrue(engine.input.up()); game.update(0f)
            assertEquals("Settings", assertIs<RuntimeScene>(game.getScene()).sceneDef.name)
            fun tap(label: String) {
                val scene = assertIs<RuntimeScene>(game.getScene())
                scene.runner.render(RecordingRenderer2D(), 960, 540)
                val hit = scene.runner.uiAccessibilityNodes().single { it.semantics.label == label }.bounds
                engine.input.touch((hit.x + hit.width / 2).toInt(), (hit.y + hit.height / 2).toInt())
                assertTrue(engine.input.down(), "Host pointer down: $label")
                assertTrue(engine.input.up(), "Host pointer up: $label")
                game.update(0f)
            }
            tap("Back")
            assertSame(menu, game.getScene())
            tap("Play")
            val main = assertIs<RuntimeScene>(game.getScene())
            assertEquals("Main", main.sceneDef.name)
            assertFalse(game.back()); game.update(0f)
            tap("Back")
            assertSame(main, game.getScene())
            assertFalse(main.runner.isPaused)
            repeat(2) {
                game.pause()
                assertTrue(main.runner.isPaused)
                assertNull(engine.input.touchController, "App pause restores pointer ownership")
                game.show()
                assertFalse(main.runner.isPaused)
            }
            assertFalse(game.back()); game.update(0f)
            tap("Back")
            assertSame(main, game.getScene())
            assertTrue(game.navigationDispatcher?.lastDiagnostic == null)
        } finally { game.dispose() }
        assertNull(engine.input.touchController, "Disposal restores the host pointer route")
    }

    @Test fun enemyStompIsCountedOnceAndRestartRestoresPatrolAndHud() {
        val r = runner("Main")
        try {
            val p = r.player()
            val enemyEntity = r.spawnedEntities.getValue("enemy-1")
            val enemy = enemyEntity.get<BehaviorComponent>()!!.allInstances().filterIsInstance<EnemyBehavior>().single()
            val body = r.spawnedEntities.getValue("player").get<RigidBody2DComponent>()!!.body!!
            val enemyBody = enemyEntity.get<RigidBody2DComponent>()!!.body!!
            body.position = Vec2(enemyBody.position.x, enemyBody.position.y + 64f)
            body.velocity = Vec2(0f, -120f)
            repeat(3) { p.onPhysicsContact(contact("enemy-1")) }
            assertTrue(enemy.isDefeated)
            assertEquals("1", p.gameplayValue("enemies"))
            assertEquals("3", p.gameplayValue("health"))
            assertTrue(body.velocity.y > 0f)
            assertFalse(enemyEntity.get<AnimatedSprite2DComponent>()!!.isEnabled)
            r.dispatchInput("restart", true); r.stepOnce()
            assertFalse(enemy.isDefeated)
            assertTrue(enemyEntity.get<AnimatedSprite2DComponent>()!!.isEnabled)
            assertEquals("0", p.gameplayValue("enemies"))
            assertEquals("3", p.gameplayValue("health"))
            assertTrue(r.diagnostics.none { it.severity == DiagnosticSeverity.ERROR }, r.diagnostics.toString())
        } finally { r.dispose() }
    }
    @Test fun enemyDamageHasGracePeriodAndDeathRespawnsOnceWithFullHealth() {
        val r = runner("Main")
        try {
            val p = r.player()
            repeat(4) { p.onPhysicsContact(contact("enemy-1", PhysicsContactPhase.STAY)) }
            assertEquals("2", p.gameplayValue("health"))
            assertFalse(p.applyDamage(1f, "enemy-1"))
            p.onFixedUpdate(1.3f)
            assertTrue(p.applyDamage(1f, "enemy-1")); assertEquals("1", p.gameplayValue("health"))
            p.onFixedUpdate(1.3f)
            assertTrue(p.applyDamage(1f, "enemy-1")); assertEquals("0", p.gameplayValue("health"))
            assertFalse(p.applyDamage(1f, "enemy-1"))
            r.stepOnce()
            assertEquals("3", p.gameplayValue("health"))
            assertEquals("1", p.gameplayValue("falls"))
            assertFalse(p.applyDamage(1f, "enemy-1"), "Respawn must give grace time")
        } finally { r.dispose() }
    }
    @Test fun kinematicEnemiesStayInsidePatrolBoundsAndDefeatedEnemiesStop() {
        val r = runner("Main")
        try {
            val initialX = r.spawnedEntities.getValue("enemy-1").get<RigidBody2DComponent>()!!.body!!.position.x
            repeat(90) { r.stepOnce() }
            assertNotEquals(initialX, r.spawnedEntities.getValue("enemy-1").get<RigidBody2DComponent>()!!.body!!.position.x)
            for (i in 1..4) {
                val e = r.spawnedEntities.getValue("enemy-$i")
                val behavior = e.get<BehaviorComponent>()!!.allInstances().filterIsInstance<EnemyBehavior>().single()
                val body = e.get<RigidBody2DComponent>()!!.body!!
                body.position = Vec2(behavior.maxX, body.position.y)
                behavior.onFixedUpdate(1f / 60)
                assertTrue(body.position.x < behavior.maxX)
                body.position = Vec2(behavior.minX, body.position.y)
                behavior.onFixedUpdate(1f / 60)
                assertTrue(body.position.x > behavior.minX)
                assertTrue(behavior.defeat()); assertFalse(behavior.defeat())
                behavior.onFixedUpdate(1f / 60)
                assertEquals(Vec2(0f, 0f), body.velocity)
            }
        } finally { r.dispose() }
    }

    @Test fun runtimeHudButtonsAndLiveCountersWorkAtLandscapeSizes() {
        for ((w,h) in listOf(960 to 540,1280 to 720,854 to 480)) {
            com.jumbox.game.core.Engine.game.resize(w,h)
            val routes = Routes(); val r = runner("Main", routes = routes)
            try {
                r.render(RecordingRenderer2D(), w,h)
                val p = r.player()
                p.onPhysicsContact(contact("gem-1")); p.applyDamage(1f,"enemy-1"); p.onUpdate(2f)
                r.render(RecordingRenderer2D(), w,h)
                assertEquals("2", p.gameplayValue("health"))
                assertEquals("1", p.gameplayValue("gems"))
                assertTrue(r.setUiText("hud", "health-value", "2 / 3"))
                assertTrue(r.setUiText("hud", "gems-value", "1 / 6"))
                assertTrue(r.setUiText("hud", "timer-value", "00:02"))
                for (label in listOf("SETTINGS","MENU","Jump")) {
                    val bounds = r.uiAccessibilityNodes().single { it.semantics.label == label }.bounds
                    assertTrue(bounds.x >= 0 && bounds.y >= 0 && bounds.x + bounds.width <= w + 1 && bounds.y + bounds.height <= h + 1, "$label outside $w x $h: $bounds")
                    // The pinned runner consumes framebuffer coordinates; the native host converts separately.
                    val x=(bounds.x+bounds.width/2).toInt(); val y=(bounds.y+bounds.height/2).toInt()
                    assertTrue(r.pointerDown(0,x,y), "$label pointer down at $x,$y on $w x $h")
                    assertTrue(r.pointerUp(0,x,y), "$label pointer up")
                }
                assertTrue("settings" in routes.triggered)
                assertTrue("menu" in routes.triggered)
                assertTrue(r.diagnostics.none { it.message.contains("font", true) && it.severity == DiagnosticSeverity.WARNING }, r.diagnostics.toString())
            } finally { r.dispose() }
        }
    }

    @Test fun nativeMainHudJumpAndSettingsAcceptVisibleTouchPositions() {
        val engine = com.jumbox.game.core.Engine.game
        engine.resize(960,540)
        val game = Platformer2DGame()
        try {
            game.start(); game.update(10f); game.update(0f)
            val menu = assertIs<RuntimeScene>(game.getScene())
            assertTrue(menu.runner.sceneNavigator.trigger("play")); game.update(0f)
            val main = assertIs<RuntimeScene>(game.getScene())
            main.runner.render(RecordingRenderer2D(),960,540)
            for (label in listOf("Jump", "SETTINGS")) {
                val b = main.runner.uiAccessibilityNodes().single { it.semantics.label == label }.bounds
                engine.input.touch((b.x+b.width/2).toInt(),540-(b.y+b.height/2).toInt())
                assertTrue(engine.input.down(), "Native $label down")
                assertTrue(engine.input.up(), "Native $label up")
                game.update(0f)
            }
            assertEquals("Settings", assertIs<RuntimeScene>(game.getScene()).sceneDef.name)
            assertTrue(main.runner.isPaused)
        } finally { game.dispose() }
    }

}
