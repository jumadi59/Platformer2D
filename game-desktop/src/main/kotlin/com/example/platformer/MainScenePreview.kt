package com.example.platformer

import com.jumbox.game.core.graphics.Color
import com.jumbox.game.core.ecs.Transform2D
import com.jumbox.game.core.physics2d.*
import com.jumbox.game.core.scene.*
import com.jumbox.game.desktop.DesktopEngineBootstrap
import com.jumbox.game.desktop.OffscreenSceneRenderer
import com.jumbox.generated.ProjectBehaviorInitializer
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Repeatable GPU preview of the authored scene and runtime HUD, using project assets/scripts. */
fun main(args: Array<String>) {
    DesktopEngineBootstrap.initializeTooling()
    val assets = File(args[0])
    val output = File(args[1]).apply { mkdirs() }
    val reports = mutableListOf<String>()
    for ((width, height) in listOf(960 to 540, 1280 to 720, 854 to 480)) {
        com.jumbox.game.core.Engine.game.resize(width, height)
        OffscreenSceneRenderer(width, height).use { gpu ->
            val registry = BehaviorRegistry().also(ProjectBehaviorInitializer::register)
            val r = RuntimeSceneRunner(SceneDocument.parse(File(assets,"scenes/Main.scene").readText()),
                assetResolver = DefaultRuntimeAssetResolver(assets), behaviorRegistry = registry)
            try {
                r.start(); repeat(45) { r.update(1f / 60f) }
                fun snapshot(name: String) {
                    val bytes = gpu.renderFrame(Color(0.4f,0.68f,0.82f,1f)) { r.render(it, width, height) }
                    val image = BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB)
                    for (y in 0 until height) for (x in 0 until width) {
                        val i=(y*width+x)*4
                        image.setRGB(x,y,((bytes[i+3].toInt() and 255) shl 24) or ((bytes[i].toInt() and 255) shl 16) or ((bytes[i+1].toInt() and 255) shl 8) or (bytes[i+2].toInt() and 255))
                    }
                    ImageIO.write(image,"png",File(output,"$name-$width.png"))
                }
                snapshot("main")
                val p = r.spawnedEntities.getValue("player").get<BehaviorComponent>()!!.allInstances().filterIsInstance<PlayerBehavior>().single()
                val player = r.spawnedEntities.getValue("player")
                player.get<RigidBody2DComponent>()!!.body!!.position = Vec2(690f, 393f)
                r.setEntityWorldPosition(player,690f,393f)
                r.spawnedEntities.getValue("main_camera").get<Transform2D>()!!.x = 720f
                p.onPhysicsContact(PhysicsContactEvent("player","gem-1",PhysicsContactPhase.ENTER,true,false))
                p.applyDamage(1f,"enemy-1"); p.onUpdate(0f)
                snapshot("encounter")
                r.dispatchInput("pause", true); r.dispatchInput("pause", false); r.update(1f / 60f)
                check(PauseMenuBehavior.isOpen(r)) { "Pause dialog did not open" }
                snapshot("paused")
                val errors = r.diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
                reports += "$width x $height: errors=$errors; diagnostics=${r.diagnostics}"
                check(errors.isEmpty()) { errors.toString() }
            } finally { r.dispose() }
        }
    }
    File(output,"gpu-results.txt").writeText(reports.joinToString("\n"))
}
