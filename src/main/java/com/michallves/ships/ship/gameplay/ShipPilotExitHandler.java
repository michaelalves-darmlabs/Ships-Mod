package com.michallves.ships.ship.gameplay;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.michallves.ships.piloting.PilotManager;
import com.michallves.ships.piloting.PilotSession;
import com.michallves.ships.ship.ShipManager;
import com.michallves.ships.utils.ShipLogger;

import java.util.UUID;

public final class ShipPilotExitHandler {

    public boolean handleCrouchExit(PilotSession session,
                                    MovementStatesComponent msc,
                                    CommandBuffer<EntityStore> commandBuffer,
                                    PilotManager pilotManager,
                                    ShipManager shipManager,
                                    UUID uuid) {
        if (msc == null) return false;

        MovementStates ms = msc.getMovementStates();
        boolean crouching = ms != null && ms.crouching;

        if (crouching && !session.wasCrouchingLastTick()) {
            commandBuffer.tryRemoveComponent(session.getPlayerRef(), MountedComponent.getComponentType());
            pilotManager.stop(uuid);
            shipManager.releasePilot(session.getShipId(), uuid);
            ShipLogger.info("[SHIPS] CTRL -> saiu da pilotagem. UUID=" + uuid + " shipId=" + session.getShipId());
            session.setLastCrouching(true);
            return true;
        }

        session.setLastCrouching(crouching);
        return false;
    }
}
