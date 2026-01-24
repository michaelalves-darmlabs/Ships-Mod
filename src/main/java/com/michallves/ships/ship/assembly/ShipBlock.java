// File: src/main/java/com/michallves/ships/ship/assembly/ShipBlock.java
package com.michallves.ships.ship.assembly;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

public final class ShipBlock {

    public final int dx;
    public final int dy;
    public final int dz;

    public final int blockId;
    public final BlockType type;
    public final int rotationIndex;

    public ShipBlock(int dx, int dy, int dz, int blockId, BlockType type, int rotationIndex) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.blockId = blockId;
        this.type = type;
        this.rotationIndex = rotationIndex;
    }
}
