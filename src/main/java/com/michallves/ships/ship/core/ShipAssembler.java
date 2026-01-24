package com.michallves.ships.ship.core;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class ShipAssembler {

    private static final int PACK_BIAS = 1 << 20;
    private static final long PACK_MASK = 0x1FFFFFL;

    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y = 320;

    private static long pack(int x, int y, int z) {
        long xx = ((long) (x + PACK_BIAS)) & PACK_MASK;
        long zz = ((long) (z + PACK_BIAS)) & PACK_MASK;
        long yy = ((long) (y + PACK_BIAS)) & PACK_MASK;
        return (xx << 42) | (zz << 21) | yy;
    }

    private static int unpackX(long packed) {
        return (int) ((packed >>> 42) & PACK_MASK) - PACK_BIAS;
    }

    private static int unpackZ(long packed) {
        return (int) ((packed >>> 21) & PACK_MASK) - PACK_BIAS;
    }

    private static int unpackY(long packed) {
        return (int) (packed & PACK_MASK) - PACK_BIAS;
    }

    private ShipAssembler() {
    }

    public static ShipStructure assemble(World world, Vector3i helmWorldPos, ShipBlockBase.Registry registry, int maxBlocks) {
        ShipStructure structure = new ShipStructure(maxBlocks);
        if (world == null || helmWorldPos == null || registry == null || maxBlocks <= 0) {
            return structure;
        }

        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet visited = new LongOpenHashSet(Math.max(1024, maxBlocks * 2));

        long start = pack(helmWorldPos.x, helmWorldPos.y, helmWorldPos.z);
        queue.enqueue(start);
        visited.add(start);

        while (!queue.isEmpty() && structure.size() < maxBlocks) {
            long packed = queue.dequeueLong();
            int wx = unpackX(packed);
            int wy = unpackY(packed);
            int wz = unpackZ(packed);

            if (wy < WORLD_MIN_Y || wy >= WORLD_MAX_Y) {
                continue;
            }

            int blockId = world.getBlock(wx, wy, wz);
            if (blockId == BlockType.EMPTY_ID) {
                continue;
            }

            ShipBlockBase block = registry.get(blockId);
            if (block == null) {
                continue;
            }

            int lx = wx - helmWorldPos.x;
            int ly = wy - helmWorldPos.y;
            int lz = wz - helmWorldPos.z;
            structure.addBlock(new Vector3i(lx, ly, lz), block);

            enqueueNeighbor(queue, visited, wx + 1, wy, wz);
            enqueueNeighbor(queue, visited, wx - 1, wy, wz);
            enqueueNeighbor(queue, visited, wx, wy + 1, wz);
            enqueueNeighbor(queue, visited, wx, wy - 1, wz);
            enqueueNeighbor(queue, visited, wx, wy, wz + 1);
            enqueueNeighbor(queue, visited, wx, wy, wz - 1);
        }

        return structure;
    }

    private static void enqueueNeighbor(LongArrayFIFOQueue queue, LongOpenHashSet visited, int x, int y, int z) {
        if (y < WORLD_MIN_Y || y >= WORLD_MAX_Y) {
            return;
        }
        long packed = pack(x, y, z);
        if (visited.add(packed)) {
            queue.enqueue(packed);
        }
    }
}
