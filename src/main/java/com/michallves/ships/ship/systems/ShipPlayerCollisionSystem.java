package com.michallves.ships.ship.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.ReadWriteArchetypeQuery;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.michallves.ships.ship.ShipController;
import com.michallves.ships.ship.ShipManager;
import com.michallves.ships.utils.ShipLogger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShipPlayerCollisionSystem extends EntityTickingSystem<EntityStore> {

    // Ajuste fino: Se mover menos de 0.5mm, não manda pacote de teleport (reduz jitter de rede)
    private static final double MIN_TELEPORT_DELTA_SQ = 0.0005 * 0.0005; 

    private final ShipManager shipManager;
    private final ReadWriteArchetypeQuery<EntityStore> query;
    private final ConcurrentHashMap<UUID, Vector3d> lastPositions = new ConcurrentHashMap<>();

    public ShipPlayerCollisionSystem(ShipManager shipManager) {
        this.shipManager = shipManager;

        Archetype<EntityStore> read = Archetype.of(
                UUIDComponent.getComponentType(),
                PlayerRef.getComponentType(),
                TransformComponent.getComponentType(),
                BoundingBox.getComponentType()
        );

        this.query = new ReadWriteArchetypeQuery<>() {
            @Override
            public Archetype<EntityStore> getReadArchetype() { return read; }

            @Override
            public Archetype<EntityStore> getWriteArchetype() { return Archetype.of(); }
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

        // 1. Coleta componentes básicos
        UUIDComponent uuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        UUID uuid = uuidComponent.getUuid();
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        BoundingBox boundingBox = chunk.getComponent(index, BoundingBox.getComponentType());
        
        if (transform == null || boundingBox == null) return;

        EntityStore external = store.getExternalData();
        if (external == null) return;
        World world = external.getWorld();
        if (world == null) return;

        Vector3d playerPos = new Vector3d(transform.getPosition());
        Box playerBox = boundingBox.getBoundingBox();
        
        // Inicializa lastPosition se for primeira vez
        Vector3d lastPos = lastPositions.computeIfAbsent(uuid, k -> new Vector3d(playerPos));

        // 2. Calcula Delta do Player (O quanto ele tentou se mover)
        Vector3d delta = new Vector3d(playerPos).subtract(lastPos);

        // 3. Verifica colisão com todos os navios próximos
        Vector3d resolvedPos = playerPos;
        boolean collided = false;
        
        // Loop otimizado: Só checa navios no mesmo mundo
        for (ShipController ship : shipManager.getAllShips()) {
            if (!ship.isActiveEntity()) continue;
            // Assumindo que ShipController tem verificação rápida de mundo
            if (!ship.getWorldName().equals(world.getName())) continue; 

            Vector3d next = ship.resolvePlayerCollision(resolvedPos, delta, playerBox);
            if (next != null) {
                resolvedPos = next;
                collided = true;
                // Não damos break, pois ele pode estar pulando de um navio para outro (borda)
            }
        }

        // 4. Aplica Correção (Teleporte)
        if (collided) {
            double dx = resolvedPos.getX() - playerPos.getX();
            double dy = resolvedPos.getY() - playerPos.getY();
            double dz = resolvedPos.getZ() - playerPos.getZ();
            double distSq = dx*dx + dy*dy + dz*dz;

            // Só teleporta se a correção for relevante (evita spam de pacotes e micro-jitter)
            if (distSq > MIN_TELEPORT_DELTA_SQ) {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                // withoutVelocityReset é crucial para manter o momentum
                Teleport teleport = new Teleport(world, resolvedPos, Vector3f.NaN).withoutVelocityReset();
                commandBuffer.putComponent(ref, Teleport.getComponentType(), teleport);
                
                // Atualiza lastPos para a posição CORRIGIDA
                lastPositions.put(uuid, new Vector3d(resolvedPos));
                return;
            }
        }

        // Se não houve colisão ou foi micro-movimento, atualiza para posição atual
        lastPositions.put(uuid, new Vector3d(playerPos));
    }
}
