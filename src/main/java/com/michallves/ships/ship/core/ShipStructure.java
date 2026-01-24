package com.michallves.ships.ship.core;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collection;
import java.util.HashMap;

public final class ShipStructure {

    private static final int PACK_BIAS = 1 << 20;
    private static final long PACK_MASK = 0x1FFFFFL;

    private static long pack(int x, int y, int z) {
        long xx = ((long) (x + PACK_BIAS)) & PACK_MASK;
        long zz = ((long) (z + PACK_BIAS)) & PACK_MASK;
        long yy = ((long) (y + PACK_BIAS)) & PACK_MASK;
        return (xx << 42) | (zz << 21) | yy;
    }

    private final HashMap<Vector3i, ShipBlockData> blocks;
    private final Long2ObjectOpenHashMap<ShipBlockData> blockIndex;

    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private int minZ;
    private int maxZ;
    private boolean boundsDirty;

    private double totalMass;
    private double massMomentX;
    private double massMomentY;
    private double massMomentZ;

    public ShipStructure(int expectedBlocks) {
        int cap = Math.max(16, expectedBlocks);
        this.blocks = new HashMap<>(cap * 2);
        this.blockIndex = new Long2ObjectOpenHashMap<>(cap * 2);
        this.boundsDirty = true;
    }

    public int size() {
        return blockIndex.size();
    }

    public Collection<ShipBlockData> getBlocks() {
        return blocks.values();
    }

    public ShipBlockData getBlockAt(int x, int y, int z) {
        return blockIndex.get(pack(x, y, z));
    }

    public boolean contains(int x, int y, int z) {
        return blockIndex.containsKey(pack(x, y, z));
    }

    public ShipBlockData addBlock(Vector3i localPos, ShipBlockBase block) {
        Vector3i key = new Vector3i(localPos);
        ShipBlockData data = new ShipBlockData(localPos.x, localPos.y, localPos.z, block.getBlockId(), (byte) 0, block, key);

        long packed = pack(localPos.x, localPos.y, localPos.z);
        ShipBlockData previous = blockIndex.put(packed, data);
        ShipBlockData removed = blocks.put(key, data);

        if (previous != null) {
            removeMass(previous);
        }
        if (removed != null && removed != previous) {
            removeMass(removed);
        }

        addMass(data);
        updateBoundsOnAdd(data);
        return previous;
    }

    public ShipBlockData removeBlock(Vector3i localPos) {
        long packed = pack(localPos.x, localPos.y, localPos.z);
        ShipBlockData removed = blockIndex.remove(packed);
        if (removed != null) {
            blocks.remove(removed.key);
            removeMass(removed);
            boundsDirty = true;
        }
        return removed;
    }

    public Vector3d calculateCenterOfMass() {
        return calculateCenterOfMass(new Vector3d());
    }

    public Vector3d calculateCenterOfMass(Vector3d out) {
        if (totalMass <= 0.0) {
            return out.assign(0.0, 0.0, 0.0);
        }
        return out.assign(massMomentX / totalMass, massMomentY / totalMass, massMomentZ / totalMass);
    }

    public int getMinX() {
        ensureBounds();
        return minX;
    }

    public int getMaxX() {
        ensureBounds();
        return maxX;
    }

    public int getMinY() {
        ensureBounds();
        return minY;
    }

    public int getMaxY() {
        ensureBounds();
        return maxY;
    }

    public int getMinZ() {
        ensureBounds();
        return minZ;
    }

    public int getMaxZ() {
        ensureBounds();
        return maxZ;
    }

    private void addMass(ShipBlockData data) {
        float mass = data.block.getMass();
        if (mass <= 0.0f) return;
        totalMass += mass;
        massMomentX += (data.x + 0.5) * mass;
        massMomentY += (data.y + 0.5) * mass;
        massMomentZ += (data.z + 0.5) * mass;
    }

    private void removeMass(ShipBlockData data) {
        float mass = data.block.getMass();
        if (mass <= 0.0f) return;
        totalMass -= mass;
        massMomentX -= (data.x + 0.5) * mass;
        massMomentY -= (data.y + 0.5) * mass;
        massMomentZ -= (data.z + 0.5) * mass;
    }

    private void updateBoundsOnAdd(ShipBlockData data) {
        if (boundsDirty) {
            ensureBounds();
            return;
        }
        if (size() == 1) {
            minX = maxX = data.x;
            minY = maxY = data.y;
            minZ = maxZ = data.z;
            return;
        }
        minX = Math.min(minX, data.x);
        maxX = Math.max(maxX, data.x);
        minY = Math.min(minY, data.y);
        maxY = Math.max(maxY, data.y);
        minZ = Math.min(minZ, data.z);
        maxZ = Math.max(maxZ, data.z);
    }

    private void ensureBounds() {
        if (!boundsDirty) return;
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
        for (ShipBlockData data : blockIndex.values()) {
            minX = Math.min(minX, data.x);
            maxX = Math.max(maxX, data.x);
            minY = Math.min(minY, data.y);
            maxY = Math.max(maxY, data.y);
            minZ = Math.min(minZ, data.z);
            maxZ = Math.max(maxZ, data.z);
        }
        if (blockIndex.isEmpty()) {
            minX = maxX = minY = maxY = minZ = maxZ = 0;
        }
        boundsDirty = false;
    }
}
