package com.michallves.ships.ship.gameplay;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.michallves.ships.piloting.PilotSession;
import com.michallves.ships.ship.ShipController;

import java.lang.reflect.Method;

public final class ShipPilotTeleportController {

    private static final Method TELEPORT_WITH_HEAD_ROTATION = resolveTeleportWithHeadRotation();
    private static final double SNAP_DIST = 0.75;
    private static final double SOFT_DIST = 0.12;
    private static final double MAX_CORRECTION_SPEED = 4.0;

    private static Method resolveTeleportWithHeadRotation() {
        try {
            return Teleport.class.getMethod("withHeadRotation", Vector3f.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public void teleportPilot(PilotSession session,
                              ShipController ship,
                              CommandBuffer<EntityStore> commandBuffer,
                              World world,
                              Vector3d playerPos,
                              Velocity velocity,
                              float dt) {
        Vector3d anchor = ship.computePilotAnchorPosition();
        double dx = anchor.getX() - playerPos.getX();
        double dy = anchor.getY() - playerPos.getY();
        double dz = anchor.getZ() - playerPos.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > SNAP_DIST * SNAP_DIST) {
            Teleport teleport = new Teleport(world, anchor, Vector3f.NaN).withoutVelocityReset();
            commandBuffer.putComponent(session.getPlayerRef(), Teleport.getComponentType(), teleport);
            session.setAnchorPosition(anchor);
            return;
        }

        if (distSq > SOFT_DIST * SOFT_DIST && velocity != null) {
            double dist = Math.sqrt(distSq);
            double scale = Math.min(MAX_CORRECTION_SPEED, dist / Math.max(1.0e-6, dt));
            Vector3d correction = new Vector3d(
                    dx / dist * scale,
                    dy / dist * scale,
                    dz / dist * scale
            );
            Velocity updated = new Velocity(velocity);
            updated.addInstruction(correction, null, ChangeVelocityType.Add);
            commandBuffer.putComponent(session.getPlayerRef(), Velocity.getComponentType(), updated);
        }

        session.setAnchorPosition(anchor);
    }
}
