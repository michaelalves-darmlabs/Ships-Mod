package com.michallves.ships.ship;

import com.hypixel.hytale.math.vector.Vector3i;
import com.michallves.ships.ship.assembly.ShipStructure;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShipManager {

    private final Map<UUID, ShipController> ships = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, UUID>> helmIndex = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> shipPilot = new ConcurrentHashMap<>();

    public ShipController createShip(String worldName, Vector3i helmBlock, int helmOri, ShipStructure structure) {
        UUID id = UUID.randomUUID();
        ShipController ship = new ShipController(id, worldName, helmBlock, helmOri, structure);
        ships.put(id, ship);
        indexHelm(worldName, helmBlock, id);
        return ship;
    }

    public ShipController get(UUID shipId) {
        return ships.get(shipId);
    }

    public ShipController getByHelm(String worldName, Vector3i helmBlock) {
        Map<Long, UUID> map = helmIndex.get(worldName);
        if (map == null) return null;

        UUID id = map.get(pack(helmBlock.x, helmBlock.y, helmBlock.z));
        if (id == null) return null;
        return ships.get(id);
    }

    public boolean tryAssignPilot(UUID shipId, UUID pilotUuid) {
        return shipPilot.putIfAbsent(shipId, pilotUuid) == null;
    }

    public void releasePilot(UUID shipId, UUID pilotUuid) {
        shipPilot.remove(shipId, pilotUuid);
    }

    public UUID getPilot(UUID shipId) {
        return shipPilot.get(shipId);
    }

    public Collection<ShipController> getAllShips() {
        return ships.values();
    }

    private void indexHelm(String worldName, Vector3i helmBlock, UUID shipId) {
        helmIndex.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(pack(helmBlock.x, helmBlock.y, helmBlock.z), shipId);
    }

    public void clear() {
        ships.clear();
        helmIndex.clear();
        shipPilot.clear();
    }

    private static final int WORLD_BIAS = 1 << 25;
    private static final long MASK_26 = 0x3FFFFFFL;
    private static final long MASK_12 = 0xFFFL;

    private static long pack(int x, int y, int z) {
        long xx = ((long) (x + WORLD_BIAS)) & MASK_26;
        long zz = ((long) (z + WORLD_BIAS)) & MASK_26;
        long yy = ((long) y) & MASK_12;
        return (xx << 38) | (zz << 12) | yy;
    }
}
