package com.michallves.ships.piloting;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

public final class PilotSession {

    private final UUID playerUuid;
    private final Ref<EntityStore> playerRef;

    private final UUID shipId;
    private final String worldName;

    private final Vector3i helmBlock;

    // Posição alvo do player (hard lock)
    private final Vector3d anchorPosition;

    // Wheel + yaw delta
    private float lastHeadYaw;
    private float wheelAngle;

    // CTRL edge-detect
    private boolean lastCrouching;
    private boolean lastForwardPressed;
    private boolean lastBackwardPressed;
    private int throttleStep;

    // Solicitações
    private boolean dockingRequested;

    public PilotSession(
            UUID playerUuid,
            Ref<EntityStore> playerRef,
            UUID shipId,
            String worldName,
            Vector3i helmBlock,
            Vector3d anchorPosition,
            float initialHeadYaw
    ) {
        this.playerUuid = playerUuid;
        this.playerRef = playerRef;
        this.shipId = shipId;
        this.worldName = worldName;
        this.helmBlock = new Vector3i(helmBlock);

        this.anchorPosition = new Vector3d(anchorPosition);
        this.lastHeadYaw = initialHeadYaw;
        this.wheelAngle = 0.0f;

        this.lastCrouching = false;
        this.lastForwardPressed = false;
        this.lastBackwardPressed = false;
        this.throttleStep = 0;
        this.dockingRequested = false;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public Ref<EntityStore> getPlayerRef() {
        return playerRef;
    }

    public UUID getShipId() {
        return shipId;
    }

    public String getWorldName() {
        return worldName;
    }

    public Vector3i getHelmBlock() {
        return helmBlock;
    }

    public Vector3d getAnchorPosition() {
        return anchorPosition;
    }

    public void setAnchorPosition(Vector3d pos) {
        this.anchorPosition.assign(pos);
    }

    public float getLastHeadYaw() {
        return lastHeadYaw;
    }

    public void setLastHeadYaw(float lastHeadYaw) {
        this.lastHeadYaw = lastHeadYaw;
    }

    public float getWheelAngle() {
        return wheelAngle;
    }

    public void setWheelAngle(float wheelAngle) {
        this.wheelAngle = wheelAngle;
    }

    public boolean wasCrouchingLastTick() {
        return lastCrouching;
    }

    public void setLastCrouching(boolean v) {
        this.lastCrouching = v;
    }

    public boolean wasForwardPressedLastTick() {
        return lastForwardPressed;
    }

    public void setLastForwardPressed(boolean v) {
        this.lastForwardPressed = v;
    }

    public boolean wasBackwardPressedLastTick() {
        return lastBackwardPressed;
    }

    public void setLastBackwardPressed(boolean v) {
        this.lastBackwardPressed = v;
    }

    public int getThrottleStep() {
        return throttleStep;
    }

    public void setThrottleStep(int throttleStep) {
        this.throttleStep = throttleStep;
    }

    public boolean isDockingRequested() {
        return dockingRequested;
    }

    public void requestDocking() {
        this.dockingRequested = true;
    }

    public void clearDockingRequest() {
        this.dockingRequested = false;
    }
}
