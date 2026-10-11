package com.example.platformer

import com.jumbox.game.core.scene.*
import com.jumbox.game.core.physics2d.*
import kotlin.math.sin

/** A single physics owner: kinematic patrol with sensor contacts, animated in world space. */
class EnemyBehavior : SchemaBehavior() {
    override val supportedDimensions = setOf(SceneDimension.TWO_D)
    override val requiredComponents = listOf("Transform2D", "RigidBody", "PhysicsCollider", "AnimatedSprite2D")
    var speed: Float by floatProperty("speed", "Patrol Speed", 64f)
    var minX: Float by floatProperty("minX", "Left Bound", 560f)
    var maxX: Float by floatProperty("maxX", "Right Bound", 760f)
    var floatHeight: Float by floatProperty("floatHeight", "Flight Bob Height", 0f)
    var canStomp: Boolean by boolProperty("canStomp", "Can Be Stomped", true)
    var defeatedSound: String by assetRefProperty("defeatedSound", "Defeat Sound", "AUDIO", "sfx/coin.wav")
    var isDefeated = false
        private set
    private var direction = 1f
    private var age = 0f
    private var spawn = Vec2(0f, 0f)

    override fun onStart() {
        spawn = entity.get<RigidBody2DComponent>()?.body?.position ?: Vec2(transform?.x ?: 0f, transform?.y ?: 0f)
    }
    override fun onFixedUpdate(delta: Float) {
        val body = entity.get<RigidBody2DComponent>()?.body ?: return
        if (isDefeated) { body.velocity = Vec2(0f, 0f); return }
        if (maxX <= minX || speed <= 0f) { body.velocity = Vec2(0f, 0f); return }
        age += delta.coerceAtLeast(0f)
        if (body.position.x >= maxX) direction = -1f
        if (body.position.x <= minX) direction = 1f
        val nextX = (body.position.x + direction * speed * delta).coerceIn(minX, maxX)
        val nextY = spawn.y + sin(age * 2f) * floatHeight
        body.wake()
        // This engine snapshot integrates dynamic bodies only. Drive the kinematic pose
        // explicitly so the rendered sprite and the sensor occupy the same position.
        body.position = Vec2(nextX, nextY)
        body.velocity = Vec2(0f, 0f)
        context.runner.setEntityWorldPosition(entity, nextX, nextY)
        entity.get<AnimatedSprite2DComponent>()?.let { it.flipX = direction > 0f; it.playing = true }
    }
    fun defeat(): Boolean {
        if (isDefeated) return false
        isDefeated = true
        entity.get<AnimatedSprite2DComponent>()?.let { it.isEnabled = false; it.playing = false }
        entity.get<RigidBody2DComponent>()?.body?.velocity = Vec2(0f, 0f)
        if (defeatedSound.isNotBlank()) sound.oneShot(defeatedSound)
        return true
    }
    fun reset() {
        isDefeated = false; direction = 1f; age = 0f
        entity.get<RigidBody2DComponent>()?.body?.let { it.position = spawn; it.velocity = Vec2(0f, 0f); it.wake() }
        context.runner.setEntityWorldPosition(entity, spawn.x, spawn.y)
        entity.get<AnimatedSprite2DComponent>()?.let { it.isEnabled = true; it.playing = true }
    }
}
