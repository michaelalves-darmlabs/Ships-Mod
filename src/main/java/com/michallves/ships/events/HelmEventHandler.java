package com.michallves.ships.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityUseBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.michallves.ships.piloting.PilotManager;
import com.michallves.ships.piloting.PilotSession;
import com.michallves.ships.ship.ShipController;
import com.michallves.ships.ship.ShipManager;
import com.michallves.ships.ship.assembly.ShipAssembler;
import com.michallves.ships.ship.assembly.ShipStructure;
import com.michallves.ships.utils.ShipLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@SuppressWarnings("removal")
public final class HelmEventHandler {

    private static HelmEventHandler instance;

    private final JavaPlugin plugin;
    private final PilotManager pilotManager;
    private final ShipManager shipManager;

    private static final Logger LOGGER = Logger.getLogger("ShipsMod");

    private static final String HELM_ASSET_ID = "Helm";
    private static final int MAX_SHIP_BLOCKS = 6000;

    // anti-spam
    private static final long USE_COOLDOWN_MS = 250;
    private final ConcurrentHashMap<UUID, Long> lastUseMs = new ConcurrentHashMap<>();

    // raycast
    private static final double MAX_USE_DISTANCE = 6.0;
    private static final double RAY_STEP = 0.25;
    private static final double EYE_HEIGHT = 1.62;
    private static final double HELM_NEAR_DISTANCE = 6.0;

    private final List<EventRegistration<?, ?>> registrations = new ArrayList<>();

    private HelmEventHandler(JavaPlugin plugin, PilotManager pilotManager, ShipManager shipManager) {
        this.plugin = plugin;
        this.pilotManager = pilotManager;
        this.shipManager = shipManager;
    }

    public static HelmEventHandler getInstance(JavaPlugin plugin, PilotManager pilotManager, ShipManager shipManager) {
        if (instance == null) {
            instance = new HelmEventHandler(plugin, pilotManager, shipManager);
        }
        return instance;
    }

    public void registerHelmListener() {
        registrations.add(plugin.getEventRegistry().registerGlobal(
                EventPriority.FIRST,
                LivingEntityUseBlockEvent.class,
                this::onLivingUseBlock
        ));

        registrations.add(plugin.getEventRegistry().registerGlobal(
                EventPriority.FIRST,
                PlayerDisconnectEvent.class,
                this::onPlayerDisconnect
        ));

        ShipLogger.success("[SHIPS] HelmEventHandler listeners registrados (LivingEntityUseBlockEvent)!");
    }

    public void shutdown() {
        for (EventRegistration<?, ?> reg : registrations) {
            try { reg.unregister(); } catch (Throwable ignored) {}
        }
        registrations.clear();
        instance = null;
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        if (uuid == null) return;

        PilotSession stopped = pilotManager.stop(uuid);
        if (stopped != null) {
            shipManager.releasePilot(stopped.getShipId(), uuid);
        }
    }

    private void onLivingUseBlock(LivingEntityUseBlockEvent event) {
        String blockType = event.getBlockType();
        if (!HELM_ASSET_ID.equals(blockType)) {
            return;
        }

        handleHelmUse(event.getRef());
    }

    private void handleHelmUse(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        if (store == null) return;

        EntityStore external = store.getExternalData();
        if (external == null) return;

        World world = external.getWorld();
        if (world == null) return;

        // UUID via component (não depende de Player.getUuid)
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        UUID uuid = uuidComponent.getUuid();
        if (uuid == null) return;

        // anti-spam
        long now = System.currentTimeMillis();
        long last = lastUseMs.getOrDefault(uuid, 0L);
        if (now - last < USE_COOLDOWN_MS) return;
        lastUseMs.put(uuid, now);

        // Se já está pilotando: F solicita docking (mesmo sem posição do helm)
        PilotSession current = pilotManager.get(uuid);
        if (current != null) {
            current.requestDocking();
            ShipLogger.info("[SHIPS] F enquanto pilotando -> docking solicitado. UUID=" + uuid
                    + " shipId=" + current.getShipId());
            return;
        }

        // Precisamos achar a POSIÇÃO do helm (evento não fornece)
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (transform == null || head == null) return;

        Vector3d pos = transform.getPosition();
        Vector3f rot = head.getRotation();

        Vector3i helmPos = findHelmByRaycast(world, pos, rot.getYaw(), rot.getPitch());
        if (helmPos == null) {
            // fallback: busca perto do player
            helmPos = findNearestHelmAround(world, pos, 6);
        }

        String worldName = world.getName();
        Vector3d searchPos = pos;
        if (helmPos != null) {
            searchPos = new Vector3d(helmPos.x + 0.5, helmPos.y + 0.5, helmPos.z + 0.5);
        }

        ShipController ship = null;

        if (helmPos != null) {
            // Confirma de novo que o bloco é helm
            int blockId = getBlockIfLoaded(world, helmPos.x, helmPos.y, helmPos.z);
            BlockType type = BlockType.getAssetMap().getAsset(blockId);
            if (type != null && HELM_ASSET_ID.equals(type.getId())) {
                ship = shipManager.getByHelm(worldName, helmPos);
            }
        }

        if (ship == null) {
            ShipController nearby = findNearestShipByHelm(worldName, searchPos, HELM_NEAR_DISTANCE);
            if (nearby != null) {
                ship = nearby;
                Vector3d helmWorld = ship.getHelmWorldPosition();
                helmPos = new Vector3i(
                        (int) Math.floor(helmWorld.getX()),
                        (int) Math.floor(helmWorld.getY()),
                        (int) Math.floor(helmWorld.getZ())
                );
            }
        }

        if (ship == null && helmPos == null) {
            ShipLogger.error("[SHIPS] Use no HELM detectado, mas não achei a posição do Helm (raycast+fallback). UUID="
                    + uuid + " pos=" + pos + " yaw=" + rot.getYaw() + " pitch=" + rot.getPitch());
            return;
        }

        if (ship == null) {
            int blockId = getBlockIfLoaded(world, helmPos.x, helmPos.y, helmPos.z);
            BlockType type = BlockType.getAssetMap().getAsset(blockId);
            if (type == null || !HELM_ASSET_ID.equals(type.getId())) {
                ShipLogger.error("[SHIPS] Evento disse Helm, mas o bloco no mundo não é Helm em " + helmPos + ". UUID=" + uuid);
                return;
            }
        }

        if (ship == null) {
            ship = shipManager.getByHelm(worldName, helmPos);
        }
        if (ship == null) {
            int helmOri = getRotationIndex(world, helmPos);

            ShipLogger.info("[SHIPS] Criando navio novo a partir do Helm em " + helmPos + " ...");

            ShipStructure structure = ShipAssembler.assembleConnected(
                    world,
                    helmPos,
                    helmOri,
                    MAX_SHIP_BLOCKS
            );

            ship = shipManager.createShip(worldName, helmPos, helmOri, structure);

            ShipLogger.success("[SHIPS] Navio criado: shipId=" + ship.getShipId()
                    + " blocos=" + structure.size());
        }

        if (!shipManager.tryAssignPilot(ship.getShipId(), uuid)) {
            ShipLogger.warn("[SHIPS] Helm em uso: já existe piloto. shipId=" + ship.getShipId());
            return;
        }

        float headYaw = rot.getYaw();

        ship.requestActivation();

        Vector3d anchor = ship.computePilotAnchorPosition();

        PilotSession session = new PilotSession(
                uuid,
                ref,
                ship.getShipId(),
                worldName,
                helmPos,
                anchor,
                headYaw
        );

        pilotManager.start(session);

        Vector3i finalHelmPos = helmPos;
        LOGGER.info(() -> "[Ships] Player pegou o HELM (LivingEntityUseBlockEvent) -> UUID=" + uuid
                + " helm=" + finalHelmPos + " anchor=" + anchor + " world=" + worldName);
    }

    private static Vector3i findHelmByRaycast(World world, Vector3d feetPos, float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        double cosPitch = Math.cos(pitch);

        double dx = -Math.sin(yaw) * cosPitch;
        double dy = -Math.sin(pitch);
        double dz =  Math.cos(yaw) * cosPitch;

        Vector3d start = new Vector3d(feetPos.getX(), feetPos.getY() + EYE_HEIGHT, feetPos.getZ());

        int lastBx = Integer.MIN_VALUE, lastBy = Integer.MIN_VALUE, lastBz = Integer.MIN_VALUE;

        for (double t = 0.5; t <= MAX_USE_DISTANCE; t += RAY_STEP) {
            double px = start.getX() + dx * t;
            double py = start.getY() + dy * t;
            double pz = start.getZ() + dz * t;

            int bx = (int) Math.floor(px);
            int by = (int) Math.floor(py);
            int bz = (int) Math.floor(pz);

            if (bx == lastBx && by == lastBy && bz == lastBz) continue;
            lastBx = bx; lastBy = by; lastBz = bz;

            int id = getBlockIfLoaded(world, bx, by, bz);
            if (id == BlockType.EMPTY_ID) continue;

            BlockType bt = BlockType.getAssetMap().getAsset(id);
            if (bt != null && HELM_ASSET_ID.equals(bt.getId())) {
                return new Vector3i(bx, by, bz);
            }
        }

        return null;
    }

    private static Vector3i findNearestHelmAround(World world, Vector3d pos, int radius) {
        int cx = (int) Math.floor(pos.getX());
        int cy = (int) Math.floor(pos.getY());
        int cz = (int) Math.floor(pos.getZ());

        Vector3i best = null;
        int bestD2 = Integer.MAX_VALUE;

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - 2; y <= cy + 2; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    int id = getBlockIfLoaded(world, x, y, z);
                    if (id == BlockType.EMPTY_ID) continue;

                    BlockType bt = BlockType.getAssetMap().getAsset(id);
                    if (bt == null || !HELM_ASSET_ID.equals(bt.getId())) continue;

                    int dx = x - cx, dy = y - cy, dz = z - cz;
                    int d2 = dx*dx + dy*dy + dz*dz;

                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = new Vector3i(x, y, z);
                    }
                }
            }
        }

        return best;
    }

    private static int getRotationIndex(World world, Vector3i pos) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) return 0;

        int lx = ChunkUtil.localCoordinate(pos.x);
        int lz = ChunkUtil.localCoordinate(pos.z);
        return chunk.getRotationIndex(lx, pos.y, lz);
    }

    private static int getBlockIfLoaded(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk != null) {
            int lx = ChunkUtil.localCoordinate(x);
            int lz = ChunkUtil.localCoordinate(z);
            return chunk.getBlock(lx, y, lz);
        }

        // fallback: para o evento de interação, o chunk deve estar carregado mesmo assim
        return world.getBlock(x, y, z);
    }

    private ShipController findNearestShipByHelm(String worldName, Vector3d pos, double radius) {
        double bestDistSq = radius * radius;
        ShipController best = null;

        for (ShipController ship : shipManager.getAllShips()) {
            if (!worldName.equals(ship.getWorldName())) continue;

            Vector3d helmWorld = ship.getHelmWorldPosition();
            double dx = helmWorld.getX() - pos.getX();
            double dy = helmWorld.getY() - pos.getY();
            double dz = helmWorld.getZ() - pos.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = ship;
            }
        }

        return best;
    }
}
