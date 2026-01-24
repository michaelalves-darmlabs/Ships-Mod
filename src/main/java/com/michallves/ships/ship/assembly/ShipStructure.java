package com.michallves.ships.ship.assembly;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.michallves.ships.utils.ShipMath;

public final class ShipStructure {

    private static final int WORLD_BIAS = 1 << 25;
    private static final long MASK_26 = 0x3FFFFFFL;
    private static final long MASK_12 = 0xFFFL;

    private static long packWorld(int x, int y, int z) {
        long xx = ((long) (x + WORLD_BIAS)) & MASK_26;
        long zz = ((long) (z + WORLD_BIAS)) & MASK_26;
        long yy = ((long) y) & MASK_12;
        return (xx << 38) | (zz << 12) | yy;
    }

    private final ShipBlock[] blocks;

    public ShipStructure(ShipBlock[] blocks) {
        this.blocks = blocks;
    }

    public int size() {
        return blocks.length;
    }

    public ShipBlock[] getBlocks() {
        return blocks;
    }

    public boolean canPlace(World world, Vector3i origin, int orientation) {
        for (ShipBlock b : blocks) {
            Vector3i o = rotateOffset(b.dx, b.dy, b.dz, orientation);
            int wx = origin.x + o.x;
            int wy = origin.y + o.y;
            int wz = origin.z + o.z;

            int existing = world.getBlock(wx, wy, wz);
            if (existing != BlockType.EMPTY_ID) {
                return false;
            }
        }
        return true;
    }

    public void clearFromWorld(World world, Vector3i origin, int orientation) {
        for (ShipBlock b : blocks) {
            Vector3i o = rotateOffset(b.dx, b.dy, b.dz, orientation);
            setBlockFast(world, origin.x + o.x, origin.y + o.y, origin.z + o.z,
                    BlockType.EMPTY_ID, BlockType.EMPTY, 0);
        }
    }

    public void placeIntoWorld(World world, Vector3i origin, int orientation) {
        for (ShipBlock b : blocks) {
            Vector3i o = rotateOffset(b.dx, b.dy, b.dz, orientation);

            int rot = (b.rotationIndex + (orientation & 3)) & 3;

            setBlockFast(world, origin.x + o.x, origin.y + o.y, origin.z + o.z,
                    b.blockId, b.type, rot);
        }
    }

    public static int nearestOrientationFromYaw(float yawDeg) {
        float yaw = (yawDeg % 360.0f + 360.0f) % 360.0f;
        int ori = Math.round(yaw / 90.0f) & 3;
        return ori;
    }

    public static Vector3i rotateOffset(int dx, int dy, int dz, int orientation) {
        return switch (orientation & 3) {
            case 0 -> new Vector3i(dx, dy, dz);
            case 1 -> new Vector3i(-dz, dy, dx);
            case 2 -> new Vector3i(-dx, dy, -dz);
            default -> new Vector3i(dz, dy, -dx);
        };
    }

    private static void setBlockFast(World world, int x, int y, int z, int id, BlockType type, int rotationIndex) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return;

        int lx = ChunkUtil.localCoordinate(x);
        int lz = ChunkUtil.localCoordinate(z);

        chunk.setBlock(lx, y, lz, id, type, rotationIndex, 0, 0);
    }

    public static Vector3i rotateOffsetStatic(int x, int y, int z, int orientation) {
        orientation = ShipMath.floorMod(orientation, 4);

        switch (orientation) {
            case 0: // norte
                return new Vector3i(x, y, z);
            case 1: // leste
                return new Vector3i(-z, y, x);
            case 2: // sul
                return new Vector3i(-x, y, -z);
            case 3: // oeste
                return new Vector3i(z, y, -x);
            default:
                return new Vector3i(x, y, z);
        }
    }

}
