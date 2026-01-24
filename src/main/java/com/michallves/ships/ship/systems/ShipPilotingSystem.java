package com.michallves.ships.ship.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.ReadWriteArchetypeQuery;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.michallves.ships.piloting.PilotManager;
import com.michallves.ships.piloting.PilotSession;
import com.michallves.ships.ship.ShipController;
import com.michallves.ships.ship.ShipManager;
import com.michallves.ships.ship.gameplay.ShipPilotController;
import com.michallves.ships.ship.gameplay.ShipPilotDockingHandler;
import com.michallves.ships.ship.gameplay.ShipPilotExitHandler;
import com.michallves.ships.ship.gameplay.ShipPilotTeleportController;
import com.michallves.ships.utils.ShipLogger;

import java.util.UUID;

public final class ShipPilotingSystem extends EntityTickingSystem<EntityStore> {

    private final PilotManager pilotManager;
    private final ShipManager shipManager;
    private final ShipPilotController pilotController;
    private final ShipPilotExitHandler exitHandler;
    private final ShipPilotDockingHandler dockingHandler;
    private final ShipPilotTeleportController teleportController;

    private final ReadWriteArchetypeQuery<EntityStore> query;

    public ShipPilotingSystem(PilotManager pilotManager, ShipManager shipManager) {
        this.pilotManager = pilotManager;
        this.shipManager = shipManager;
        this.pilotController = new ShipPilotController();
        this.exitHandler = new ShipPilotExitHandler();
        this.dockingHandler = new ShipPilotDockingHandler();
        this.teleportController = new ShipPilotTeleportController();

        Archetype<EntityStore> read = Archetype.of(
                UUIDComponent.getComponentType(),
                TransformComponent.getComponentType(),
                HeadRotation.getComponentType(),
                Velocity.getComponentType(),
                MovementStatesComponent.getComponentType()
        );

        this.query = new ReadWriteArchetypeQuery<>() {
            @Override
            public Archetype<EntityStore> getReadArchetype() {
                return read;
            }

            @Override
            public Archetype<EntityStore> getWriteArchetype() {
                // nao exigimos nada para write (usamos CommandBuffer)
                return Archetype.of();
            }
        };
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt,
                     int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {

        UUIDComponent uuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        UUID uuid = uuidComponent.getUuid();
        if (uuid == null) return;

        PilotSession session = pilotManager.get(uuid);
        if (session == null) return;

        if (session.getPlayerRef() == null || !session.getPlayerRef().isValid() || session.getPlayerRef().getStore() != store) {
            pilotManager.stop(uuid);
            shipManager.releasePilot(session.getShipId(), uuid);
            return;
        }

        EntityStore external = store.getExternalData();
        if (external == null) return;

        World world = external.getWorld();
        if (world == null) return;

        ShipController ship = shipManager.get(session.getShipId());
        if (ship == null) {
            pilotManager.stop(uuid);
            shipManager.releasePilot(session.getShipId(), uuid);
            ShipLogger.warn("[SHIPS] Sessao sem navio. UUID=" + uuid + " shipId=" + session.getShipId());
            return;
        }

        commandBuffer.tryRemoveComponent(session.getPlayerRef(), MountedComponent.getComponentType());

        MovementStatesComponent msc = chunk.getComponent(index, MovementStatesComponent.getComponentType());
        if (exitHandler.handleCrouchExit(session, msc, commandBuffer, pilotManager, shipManager, uuid)) {
            return;
        }

        dockingHandler.requestDockingIfNeeded(session, ship);

        Velocity vel = chunk.getComponent(index, Velocity.getComponentType());
        HeadRotation hr = chunk.getComponent(index, HeadRotation.getComponentType());
        if (vel == null || hr == null) return;

        ShipPilotController.PilotInput input = pilotController.updateInput(session, vel.getClientVelocity(), hr.getRotation().getYaw());

        ship.applyControls(dt, input.throttle, input.rudder, 0.0f);
        ship.tick(world, store, commandBuffer, dt);

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform != null) {
            teleportController.teleportPilot(session, ship, commandBuffer, world, transform.getPosition(), vel, dt);
        }

        if (dockingHandler.handleDockingOutcome(session, ship, pilotManager, shipManager, uuid)) {
            return;
        }
    }
}
