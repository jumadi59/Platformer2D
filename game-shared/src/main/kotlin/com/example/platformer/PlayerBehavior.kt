package com.example.platformer

import com.jumbox.game.core.scene.AnimatedSprite2DComponent
import com.jumbox.game.core.scene.Sprite2DComponent
import com.jumbox.game.core.scene.PhysicsContactPhase
import com.jumbox.game.core.scene.SchemaBehavior
import com.jumbox.game.core.physics2d.BodyType
import com.jumbox.game.core.physics2d.RigidBody2DComponent
import com.jumbox.game.core.physics2d.Vec2
import com.jumbox.game.core.scene.DiagnosticSeverity
import com.jumbox.game.core.scene.PhysicsContactEvent
import com.jumbox.game.core.scene.PointLight2DComponent
import com.jumbox.game.core.scene.RuntimeDiagnostic
import com.jumbox.game.core.scene.SceneDimension
import com.jumbox.game.core.ui.document.UiActionEvent
import com.jumbox.game.core.ui.document.UiValue

class PlayerBehavior : SchemaBehavior(), com.jumbox.game.core.scene.DamageReceiver2D, com.jumbox.game.core.scene.GameplayValueSource2D {
    override val supportedDimensions = setOf(SceneDimension.TWO_D)
    override val requiredComponents = listOf("Transform2D", "RigidBody", "PhysicsCollider")
    override val requiredActions = listOf("move_left", "move_right", "jump")
    var moveLeftAction: String by stringProperty("moveLeftAction", "Move Left Action", "move_left")
    var moveRightAction: String by stringProperty("moveRightAction", "Move Right Action", "move_right")
    var joystickAction: String by stringProperty("joystickAction", "Knob Controller Action", "move_stick")
    var jumpAction: String by stringProperty("jumpAction", "Jump Action", "jump")
    var moveSpeed: Float by floatProperty("moveSpeed", "Move Speed", 180f)
    var jumpSpeed: Float by floatProperty("jumpSpeed", "Jump Speed", 360f)
    var groundNormalThreshold: Float by floatProperty("groundNormalThreshold", "Ground Normal Threshold", 0.55f)
    var jumpBufferSeconds: Float by floatProperty("jumpBufferSeconds", "Jump Buffer (Seconds)", 0.12f)
    var coyoteTimeSeconds: Float by floatProperty("coyoteTimeSeconds", "Coyote Time (Seconds)", 0.1f)
    var spawnX: Float by floatProperty("spawnX", "Spawn X", 140f)
    var spawnY: Float by floatProperty("spawnY", "Spawn Y", 120f)
    var deathY: Float by floatProperty("deathY", "Fall Limit", -200f)
    var requiredGems: Int by intProperty("requiredGems", "Required Gems", 6)
    var jumpSound: String by assetRefProperty("jumpSound", "Jump Sound", "AUDIO", "sfx/jump.wav")
    var coinSound: String by assetRefProperty("coinSound", "Gem Sound", "AUDIO", "sfx/coin.wav")
    var checkpointSound: String by assetRefProperty("checkpointSound", "Checkpoint Sound", "AUDIO", "sfx/health.wav")
    var respawnSound: String by assetRefProperty("respawnSound", "Respawn Sound", "AUDIO", "sfx/respawn.wav")
    var finishSound: String by assetRefProperty("finishSound", "Finish Sound", "AUDIO", "sfx/finish.wav")
    private var checkpointX = 140f
    private var checkpointY = 120f
    private val collected = linkedSetOf<String>()
    private var won = false
    private var respawnRequested = false
    private var restartRequested = false
    private var deaths = 0
    var maxHealth: Int by intProperty("maxHealth", "Maximum Health", 3)
    var damageCooldown: Float by floatProperty("damageCooldown", "Damage Grace (Seconds)", 1.2f)
    private var health = 3
    private var invulnerability = 0f
    private var elapsed = 0f
    private var enemiesDefeated = 0
    private val hudValues = mutableMapOf<String, String>()
    private var lastHudSecond = -1
    private var grounded = false
    private var jumpRequested = false
    private var jumpHeld = false
    private var jumpBufferRemaining = 0f
    private var coyoteTimeRemaining = 0f
    private var joystickX = 0f
    private var checkpointActivated = false
    private var atGoal = false

    override fun onUiAction(event: UiActionEvent) {
        if (joystickAction.isBlank() || event.action != joystickAction || PauseMenuBehavior.isOpen(context.runner)) return
        joystickX = ((event.payload["x"] as? UiValue.NumberValue)?.value?.toFloat() ?: 0f).coerceIn(-1f, 1f)
    }

    override fun onStart() {
        health = maxHealth.coerceAtLeast(1)
        AudioSettings.apply(sound.mixer)
        listOf(jumpSound, coinSound, checkpointSound, respawnSound, finishSound).filter(String::isNotBlank).forEach(sound::preload)
        checkpointX = spawnX; checkpointY = spawnY
        if (entity.get<RigidBody2DComponent>()?.bodyType != BodyType.DYNAMIC) {
            context.runner.reportBehaviorDiagnostic(RuntimeDiagnostic(
                DiagnosticSeverity.ERROR,
                "PlatformerController2D requires a dynamic RigidBody.", entity.id.toString(), "RigidBody", "bodyType"
            ))
        }
        if (context.input.hasBindings()) {
            val unbound = listOf(moveLeftAction, moveRightAction, jumpAction).distinct().filter { !context.input.hasBinding(it) }
            if (unbound.isNotEmpty()) {
                context.runner.reportBehaviorDiagnostic(
                    RuntimeDiagnostic(
                        DiagnosticSeverity.WARNING,
                        "PlatformerController2D actions have no configured key or axis binding: ${unbound.joinToString()}",
                        entity.id.toString(), "Behavior", "moveLeftAction"
                    )
                )
            }
        }
    }
    override fun onInput(action: String, isDown: Boolean) {
        // Pause is owned by PauseMenuBehavior; Retry stays available from its dialog.
        if (action == "restart" && isDown) restartRequested = true
        if (action != jumpAction || (isDown && PauseMenuBehavior.isOpen(context.runner))) return
        val pressed = isDown && !jumpHeld
        jumpHeld = isDown
        if (pressed) jumpRequested = true
    }
    override fun onPhysicsContact(event: PhysicsContactEvent) {
        if (event.otherEntityId == "goal") atGoal = event.phase != PhysicsContactPhase.EXIT
        if (event.phase == PhysicsContactPhase.ENTER && !won) {
            val id = event.otherEntityId
            when {
                id.startsWith("gem-") && collected.add(id) -> {
                    context.runner.spawnedEntities[id]?.get<Sprite2DComponent>()?.isEnabled = false
                    context.runner.spawnedEntities[id]?.get<PointLight2DComponent>()?.intensity = 0f
                    play(coinSound)
                }
                id == "checkpoint" && !checkpointActivated -> context.runner.spawnedEntities[id]?.let { flag ->
                    val position = context.runner.computeWorldTransform(flag)
                    checkpointX = position.x; checkpointY = position.y + 10f
                    checkpointActivated = true
                    play(checkpointSound)
                }
                id.startsWith("hazard") -> requestRespawn()
            }
        }
        if (!won && !respawnRequested && event.phase != PhysicsContactPhase.EXIT && event.otherEntityId.startsWith("enemy-")) {
            val other = context.runner.spawnedEntities[event.otherEntityId]
            val enemy = other?.get<com.jumbox.game.core.scene.BehaviorComponent>()?.allInstances()?.filterIsInstance<EnemyBehavior>()?.firstOrNull()
            if (enemy != null && !enemy.isDefeated) {
                val body = entity.get<RigidBody2DComponent>()?.body
                val otherY = other.get<RigidBody2DComponent>()?.body?.position?.y ?: context.runner.computeWorldTransform(other).y
                if (enemy.canStomp && body != null && body.velocity.y < -30f && body.position.y > otherY + 32f) {
                    if (enemy.defeat()) { enemiesDefeated++; body.wake(); body.velocity = Vec2(body.velocity.x, jumpSpeed * 0.65f) }
                } else applyDamage(1f, event.otherEntityId)
            }
        }
        if (!won && atGoal && collected.size >= requiredGems.coerceAtLeast(1)) { won = true; play(finishSound) }
        if (event.isSensor) return
        val normalY = event.normal?.y ?: return
        if (!event.selfIsSensor && event.phase != PhysicsContactPhase.EXIT && normalY < -groundNormalThreshold) grounded = true
    }
    override fun onFixedUpdate(delta: Float) {
        invulnerability = (invulnerability - delta.coerceAtLeast(0f)).coerceAtLeast(0f)
        val bodyForReset = entity.get<RigidBody2DComponent>()?.body
        if (restartRequested) {
            for (id in collected) context.runner.spawnedEntities[id]?.get<Sprite2DComponent>()?.isEnabled = true
            context.runner.spawnedEntities.values.forEach { it.get<com.jumbox.game.core.scene.BehaviorComponent>()?.allInstances()?.filterIsInstance<EnemyBehavior>()?.forEach(EnemyBehavior::reset) }
            collected.clear(); won = false; deaths = 0; checkpointActivated = false; atGoal = false
            health = maxHealth.coerceAtLeast(1); invulnerability = 0f; elapsed = 0f; enemiesDefeated = 0; hudValues.clear()
            onInputCancelled()
            checkpointX = spawnX; checkpointY = spawnY; restartRequested = false
            respawnRequested = true
        } else if (!won && (bodyForReset?.position?.y ?: spawnY) < deathY) {
            requestRespawn()
        }
        if (respawnRequested && bodyForReset != null) {
            bodyForReset.position = Vec2(checkpointX, checkpointY)
            bodyForReset.velocity = Vec2(0f, 0f)
            bodyForReset.wake()
            context.runner.setEntityWorldPosition(entity, checkpointX, checkpointY)
            grounded = false; coyoteTimeRemaining = 0f; jumpBufferRemaining = 0f; jumpRequested = false
            atGoal = false
            health = maxHealth.coerceAtLeast(1); invulnerability = damageCooldown.coerceAtLeast(0f)
            respawnRequested = false
            play(respawnSound)
        }
        val wasGrounded = grounded
        grounded = false
        coyoteTimeRemaining = if (wasGrounded) coyoteTimeSeconds.coerceAtLeast(0f)
            else (coyoteTimeRemaining - delta).coerceAtLeast(0f)
        if (jumpRequested) jumpBufferRemaining = jumpBufferSeconds.coerceAtLeast(0f)
        val def = entity.get<RigidBody2DComponent>()
        if (def?.bodyType != BodyType.DYNAMIC) { jumpRequested = false; jumpBufferRemaining = 0f; return }
        val body = def.body ?: return
        val axis = if (won) 0f else (context.input.value(moveRightAction) - context.input.value(moveLeftAction) + joystickX).coerceIn(-1f, 1f)
        val canJump = !won && jumpBufferRemaining > 0f && (wasGrounded || coyoteTimeRemaining > 0f)
        val y = if (canJump) jumpSpeed else body.velocity.y
        // Velocity writes alone do not wake a resting physics body.
        if (kotlin.math.abs(axis) > 0.0001f || canJump) body.wake()
        body.velocity = Vec2(axis * moveSpeed, y)
        if (canJump) {
            play(jumpSound)
            jumpBufferRemaining = 0f
            coyoteTimeRemaining = 0f
        } else {
            jumpBufferRemaining = (jumpBufferRemaining - delta).coerceAtLeast(0f)
        }
        jumpRequested = false
        entity.get<AnimatedSprite2DComponent>()?.let { sprite ->
            sprite.animation = if (!wasGrounded && kotlin.math.abs(body.velocity.y) > 15f) "jump" else if (kotlin.math.abs(axis) > 0.05f) "walk" else "idle"
            sprite.playing = true
            if (kotlin.math.abs(axis) > 0.05f) sprite.flipX = axis < 0f
        }
    }
    override fun onUpdate(delta: Float) {
        if (!won) elapsed += delta.coerceAtLeast(0f)
        updateHudText("status", when {
            won -> "LEVEL COMPLETE!  Restart to play again"
            atGoal -> "Collect all $requiredGems gems first!"
            else -> if (checkpointActivated) "CHECKPOINT SAVED / Reach the green flag" else "Collect $requiredGems gems / Reach the green flag"
        })
        updateHudText("health-value", "$health/${maxHealth.coerceAtLeast(1)}")
        updateHudText("gems-value", "${collected.size}/$requiredGems")
        val enemyCount = context.runner.spawnedEntities.values.sumOf {
            it.get<com.jumbox.game.core.scene.BehaviorComponent>()?.allInstances()?.count { behavior -> behavior is EnemyBehavior } ?: 0
        }
        updateHudText("enemies-value", "$enemiesDefeated/$enemyCount")
        val second = elapsed.toInt()
        if (second != lastHudSecond || "timer-value" !in hudValues) {
            val minutes = (second / 60).toString().padStart(2, '0')
            val seconds = (second % 60).toString().padStart(2, '0')
            updateHudText("timer-value", "$minutes:$seconds")
            lastHudSecond = second
        }
        updateHudText("falls-value", "FALLS $deaths")
    }
    private fun updateHudText(id: String, value: String) {
        if (hudValues[id] != value && context.runner.setUiText("hud", id, value)) hudValues[id] = value
    }
    override fun applyDamage(amount: Float, sourceEntityId: String?): Boolean {
        if (won || respawnRequested || invulnerability > 0f || !amount.isFinite() || amount <= 0f) return false
        health = (health - kotlin.math.ceil(amount).toInt()).coerceAtLeast(0)
        invulnerability = damageCooldown.coerceAtLeast(0f)
        if (health == 0) requestRespawn() else play(respawnSound)
        return true
    }
    override fun gameplayValue(key: String): String? = when (key) {
        "health" -> health.toString()
        "gems" -> collected.size.toString()
        "enemies" -> enemiesDefeated.toString()
        "falls" -> deaths.toString()
        "won" -> won.toString()
        else -> null
    }

    override fun onInputCancelled() {
        joystickX = 0f; jumpHeld = false; jumpRequested = false; jumpBufferRemaining = 0f
    }

    private fun requestRespawn() {
        if (!respawnRequested) { respawnRequested = true; deaths++ }
    }

    private fun play(asset: String) { if (asset.isNotBlank()) sound.oneShot(asset) }
}
