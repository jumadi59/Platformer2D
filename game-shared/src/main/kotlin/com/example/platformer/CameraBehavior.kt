package com.example.platformer

import com.jumbox.game.core.scene.DiagnosticSeverity
import com.jumbox.game.core.scene.SchemaBehavior
import com.jumbox.game.core.scene.EntityMetadata2D
import com.jumbox.game.core.scene.EntityReferenceStatus
import com.jumbox.game.core.scene.RuntimeDiagnostic
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.sin

/** Follows a scene entity ID in world space and writes this camera entity's local transform. */
class CameraBehavior : SchemaBehavior() {
    var targetEntityId: String by entityRefProperty("targetEntityId", "Target Entity", "", "Scene entity ID to follow.")
    var offsetX: Float by floatProperty("offsetX", "Offset X", 0f)
    var offsetY: Float by floatProperty("offsetY", "Offset Y", 0f)
    var smoothing: Float by floatProperty("smoothing", "Smoothing", 8f, "Higher values follow faster; 0 snaps immediately.")
    var useBounds: Boolean by boolProperty("useBounds", "Use Bounds", false)
    var minX: Float by floatProperty("minX", "Minimum X", -100000f)
    var maxX: Float by floatProperty("maxX", "Maximum X", 100000f)
    var minY: Float by floatProperty("minY", "Minimum Y", -100000f)
    var maxY: Float by floatProperty("maxY", "Maximum Y", 100000f)
    private var lastMissingTarget: String? = null
    override fun onLateUpdate(delta: Float) {
        val cameraTransform = transform ?: return
        val targetReference = context.runner.resolveEntityReference(entity, targetEntityId)
        val target = targetReference.target
        if (targetReference.status != EntityReferenceStatus.RESOLVED || target == null) {
            if (lastMissingTarget != targetEntityId) {
                lastMissingTarget = targetEntityId
                context.runner.reportBehaviorDiagnostic(
                    RuntimeDiagnostic(
                        DiagnosticSeverity.WARNING,
                        "CameraFollow2D target '$targetEntityId' is missing or was deleted.",
                        entity.id.toString(), "Behavior", "targetEntityId"
                    )
                )
            }
            return
        }
        lastMissingTarget = null
        val targetWorld = context.runner.computeWorldTransform(target)
        var worldX = targetWorld.x + offsetX
        var worldY = targetWorld.y + offsetY
        if (useBounds) {
            worldX = worldX.coerceIn(minOf(minX, maxX), maxOf(minX, maxX))
            worldY = worldY.coerceIn(minOf(minY, maxY), maxOf(minY, maxY))
        }
        val currentWorld = context.runner.computeWorldTransform(entity)
        val alpha = if (smoothing <= 0f) 1f else (1f - exp(-smoothing * delta.coerceAtLeast(0f))).coerceIn(0f, 1f)
        val desiredX = currentWorld.x + (worldX - currentWorld.x) * alpha
        val desiredY = currentWorld.y + (worldY - currentWorld.y) * alpha
        val parentId = entity.get<EntityMetadata2D>()?.parentId
        val parent = parentId?.let(context.runner.spawnedEntities::get)
        if (parent == null) {
            cameraTransform.x = desiredX; cameraTransform.y = desiredY
        } else {
            val parentWorld = context.runner.computeWorldTransform(parent)
            val dx = desiredX - parentWorld.x; val dy = desiredY - parentWorld.y
            val radians = Math.toRadians((-parentWorld.rotation).toDouble()).toFloat()
            val localX = dx * cos(radians) - dy * sin(radians)
            val localY = dx * sin(radians) + dy * cos(radians)
            cameraTransform.x = localX / safeScale(parentWorld.scaleX)
            cameraTransform.y = localY / safeScale(parentWorld.scaleY)
        }
    }
    private fun safeScale(value: Float): Float = when { value >= 0f -> value.coerceAtLeast(0.00001f); else -> value.coerceAtMost(-0.00001f) }
}
