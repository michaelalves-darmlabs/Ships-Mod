package com.michallves.ships.ship.runtime;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.michallves.ships.ship.assembly.ShipBlock;
import com.michallves.ships.ship.assembly.ShipStructure;
import com.michallves.ships.ship.core.ShipGeometry;
import com.michallves.ships.ship.simulation.ShipPhysics;
import com.michallves.ships.utils.ShipLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ShipEntityLifecycle {

    private final ShipStructure structure;
    private final List<ShipPart> parts = new ArrayList<>();
    private boolean activeEntity = false;

    public ShipEntityLifecycle(ShipStructure structure) {
        this.structure = structure;
    }

    public boolean isActiveEntity() {
        return activeEntity;
    }

    public void activate(World world,
                         Store<EntityStore> store,
                         CommandBuffer<EntityStore> commandBuffer,
                         ShipGeometry geometry,
                         float yawDeg,
                         UUID shipId) {
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d helmWorld = geometry.computeHelmWorldPosition(yawDeg);
        int intOx = (int) Math.floor(helmWorld.getX());
        int intOy = (int) Math.floor(helmWorld.getY());
        int intOz = (int) Math.floor(helmWorld.getZ());
        Vector3i intOrigin = new Vector3i(intOx, intOy, intOz);

        int baseOri = ShipStructure.nearestOrientationFromYaw(yawDeg);

        structure.clearFromWorld(world, intOrigin, baseOri);

        parts.clear();

        Vector3f rot = new Vector3f(0.0f, yawDeg, 0.0f);

        for (ShipBlock b : structure.getBlocks()) {
            Vector3d pos = geometry.computeWorldPosContinuous(b.dx, b.dy, b.dz, yawDeg);

            Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(time, b.type.getId(), pos);
            holder.removeComponent(DespawnComponent.getComponentType());
            Ref<EntityStore> ref = commandBuffer.addEntity(holder, AddReason.SPAWN);

            commandBuffer.putComponent(ref, TransformComponent.getComponentType(), new TransformComponent(pos, rot));
            parts.add(new ShipPart(b, ref));
        }

        activeEntity = true;
        ShipLogger.success("[SHIPS] Ship ativado (modo entidade) shipId=" + shipId + " partes=" + parts.size());
    }

    public boolean tryDock(World world,
                           CommandBuffer<EntityStore> commandBuffer,
                           ShipGeometry geometry,
                           ShipPhysics physics,
                           UUID shipId) {
        int newOri = ShipStructure.nearestOrientationFromYaw(physics.getYawDeg());

        Vector3d helmWorld = geometry.computeHelmWorldPosition(physics.getYawDeg());
        int ox = (int) Math.round(helmWorld.getX());
        int oy = (int) Math.round(helmWorld.getY());
        int oz = (int) Math.round(helmWorld.getZ());

        Vector3i newOrigin = new Vector3i(ox, oy, oz);

        if (!structure.canPlace(world, newOrigin, newOri)) {
            ShipLogger.warn("[SHIPS] Docking bloqueado (colisao). shipId=" + shipId + " origin=" + newOrigin + " ori=" + newOri);
            return false;
        }

        for (ShipPart p : parts) {
            commandBuffer.removeEntity(p.ref, RemoveReason.REMOVE);
        }
        parts.clear();

        structure.placeIntoWorld(world, newOrigin, newOri);

        float newYaw = newOri * 90.0f;
        geometry.updateOriginFromHelm(newOrigin, newYaw);
        physics.resetForDocking(newYaw);

        activeEntity = false;

        ShipLogger.success("[SHIPS] Dock concluido (voltou a blocos). shipId=" + shipId + " origin=" + newOrigin + " ori=" + newOri);
        return true;
    }

    public void updateTransforms(CommandBuffer<EntityStore> commandBuffer, ShipGeometry geometry, float yawDeg) {
        Vector3f rot = new Vector3f(0.0f, yawDeg, 0.0f);

        for (ShipPart p : parts) {
            ShipBlock b = p.block;
            Vector3d pos = geometry.computeWorldPosContinuous(b.dx, b.dy, b.dz, yawDeg);
            commandBuffer.putComponent(p.ref, TransformComponent.getComponentType(), new TransformComponent(pos, rot));
        }
    }

    private static final class ShipPart {
        private final ShipBlock block;
        private final Ref<EntityStore> ref;

        private ShipPart(ShipBlock block, Ref<EntityStore> ref) {
            this.block = block;
            this.ref = ref;
        }
    }
}
