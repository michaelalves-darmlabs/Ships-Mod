package com.michallves.ships.ship.core;

import com.hypixel.hytale.math.vector.Vector3i;

public final class ShipBlockData {

    public final int x;
    public final int y;
    public final int z;
    public final int blockId;
    public final byte rotation;
    public final ShipBlockBase block;
    public final Vector3i key;

    public ShipBlockData(int x, int y, int z, int blockId, byte rotation, ShipBlockBase block, Vector3i key) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId;
        this.rotation = rotation;
        this.block = block;
        this.key = key;
    }
}
