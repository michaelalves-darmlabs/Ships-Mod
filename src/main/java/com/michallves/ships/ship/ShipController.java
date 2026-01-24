package com.michallves.ships.ship;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.michallves.ships.ship.assembly.ShipStructure;
import com.michallves.ships.ship.collision.ShipCollision;
import com.michallves.ships.ship.core.ShipGeometry;
import com.michallves.ships.ship.core.ShipState;
import com.michallves.ships.ship.runtime.ShipEntityLifecycle;
import com.michallves.ships.ship.simulation.ShipPhysics;
import com.michallves.ships.utils.ShipLogger;

import java.util.UUID;

public final class ShipController {

    private final UUID shipId;
    private final String worldName;

    private final ShipState state;
    private final ShipGeometry geometry;
    private final ShipCollision collision;
    private final ShipPhysics physics;
    private final ShipEntityLifecycle lifecycle;

    private boolean activationRequested = false;
    private boolean dockingRequested = false;

    private int tickCounter = 0;

    public ShipController(UUID shipId, String worldName, Vector3i helmBlock, int helmOri, ShipStructure structure) {
        this.shipId = shipId;
        this.worldName = worldName;

        float yawDeg = (helmOri & 3) * 90.0f;
        this.state = new ShipState(yawDeg);
        this.geometry = new ShipGeometry(structure, state, helmBlock, yawDeg);
        this.collision = new ShipCollision(structure, geometry.getCenterOffset());
        this.physics = new ShipPhysics(state);
        this.lifecycle = new ShipEntityLifecycle(structure);
    }

    public UUID getShipId() {
        return shipId;
    }

    public String getWorldName() {
        return worldName;
    }

    public float getYawDeg() {
        return state.getYawDeg();
    }

    public float getSpeed() {
        return state.getSpeed();
    }

    public float getMaxSpeed() {
        return physics.getMaxSpeed();
    }

    public boolean isActiveEntity() {
        return lifecycle.isActiveEntity();
    }

    public Vector3d getOrigin() {
        return state.getOrigin();
    }

    public double getLastMoveX() {
        return state.getLastMoveX();
    }

    public double getLastMoveZ() {
        return state.getLastMoveZ();
    }

    public float getLastYawDelta() {
        return state.getLastYawDelta();
    }

    public void requestActivation() {
        this.activationRequested = true;
    }

    public void requestDocking() {
        this.dockingRequested = true;
    }

    public Vector3d computePilotAnchorPosition() {
        return geometry.computePilotAnchorPosition(state.getYawDeg());
    }

    public Vector3d getHelmWorldPosition() {
        return geometry.computeHelmWorldPosition(state.getYawDeg());
    }

    public void applyControls(float dt, float throttle, float rudder, float wheel01) {
        physics.applyControls(dt, throttle, rudder, wheel01);
    }

    public void tick(World world,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer,
                     float dt) {

        if (activationRequested && !lifecycle.isActiveEntity()) {
            activationRequested = false;
            lifecycle.activate(world, store, commandBuffer, geometry, state.getYawDeg(), shipId);
        }

        if (!lifecycle.isActiveEntity()) return;

        physics.tickMovement(world, geometry, collision, dt);
        lifecycle.updateTransforms(commandBuffer, geometry, state.getYawDeg());

        tickCounter++;
        if ((tickCounter % 20) == 0) {
            ShipLogger.debug("[Ship] shipId=" + shipId + " origin=" + state.getOrigin()
                    + " yaw=" + state.getYawDeg() + " speed=" + state.getSpeed());
        }

        if (dockingRequested) {
            dockingRequested = false;
            lifecycle.tryDock(world, commandBuffer, geometry, physics, shipId);
        }
    }

    public Vector3d resolvePlayerCollision(Vector3d playerPos, Vector3d playerDelta, Box playerBox) {
        return collision.resolvePlayerCollision(
                playerPos,
                playerDelta,
                playerBox,
                state.getOrigin(),
                state.getYawDeg(),
                state.getLastMoveX(),
                state.getLastMoveZ(),
                state.getLastYawDelta(),
                lifecycle.isActiveEntity()
        );
    }
}
