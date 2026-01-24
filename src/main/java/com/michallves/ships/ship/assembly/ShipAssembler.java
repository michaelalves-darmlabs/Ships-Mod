package com.michallves.ships.ship.assembly;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.michallves.ships.utils.ShipLogger;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;

public final class ShipAssembler {

    // LIMITES DE SEGURANÇA (pra não puxar terreno / mundo)
    // Ajuste depois se quiser navios maiores.
    private static final int MAX_RADIUS_XZ = 48;   // alcance horizontal a partir do helm
    private static final int MAX_DOWN_Y   = 10;   // quantos blocos pode descer abaixo do helm
    private static final int MAX_UP_Y     = 40;   // quantos blocos pode subir acima do helm

    private static final int WORLD_BIAS = 1 << 25;
    private static final long MASK_26 = 0x3FFFFFFL;
    private static final long MASK_12 = 0xFFFL;

    private static long pack(int x, int y, int z) {
        long xx = ((long) (x + WORLD_BIAS)) & MASK_26;
        long zz = ((long) (z + WORLD_BIAS)) & MASK_26;
        long yy = ((long) y) & MASK_12;
        return (xx << 38) | (zz << 12) | yy;
    }

    private ShipAssembler() {}

    /**
     * Captura APENAS blocos conectados (6-direções) ao Helm.
     *
     * @param baseOri orientação do helm (0..3) para normalizar offsets locais do navio
     * @param maxBlocks hard cap de segurança
     */
    public static ShipStructure assembleConnected(World world, Vector3i helmBlock, int baseOri, int maxBlocks) {
        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        LongOpenHashSet visited = new LongOpenHashSet(Math.max(1024, maxBlocks * 2));

        queue.add(new Vector3i(helmBlock));
        visited.add(pack(helmBlock.x, helmBlock.y, helmBlock.z));

        ShipBlock[] temp = new ShipBlock[Math.min(maxBlocks, 2048)];
        int count = 0;

        int minX = helmBlock.x, maxX = helmBlock.x;
        int minY = helmBlock.y, maxY = helmBlock.y;
        int minZ = helmBlock.z, maxZ = helmBlock.z;

        final int hx = helmBlock.x;
        final int hy = helmBlock.y;
        final int hz = helmBlock.z;

        // inverso da orientação do helm pra converter mundo -> local
        final int invOri = floorMod(-baseOri, 4);

        while (!queue.isEmpty() && count < maxBlocks) {
            Vector3i p = queue.removeFirst();

            // Limites (evita puxar o mundo)
            if (Math.abs(p.x - hx) > MAX_RADIUS_XZ) continue;
            if (Math.abs(p.z - hz) > MAX_RADIUS_XZ) continue;
            if (p.y < hy - MAX_DOWN_Y) continue;
            if (p.y > hy + MAX_UP_Y) continue;

            int id = world.getBlock(p.x, p.y, p.z);

            // *** FIX PRINCIPAL ***
            // Se é EMPTY, NÃO expande vizinhos.
            if (id == BlockType.EMPTY_ID) {
                continue;
            }

            BlockType type = BlockType.getAssetMap().getAsset(id);
            if (type == null) {
                continue;
            }

            int rot = getRotationIndex(world, p);

            // offsets mundo
            int dxW = p.x - hx;
            int dy = p.y - hy;
            int dzW = p.z - hz;

            // converte para offsets locais (normalizado pela orientação do helm)
            Vector3i local = ShipStructure.rotateOffset(dxW, dy, dzW, invOri);

            // rotação local do bloco relativa ao helm
            int localRot = (rot - baseOri) & 3;

            if (count >= temp.length) {
                // cresce sem passar do maxBlocks
                int newLen = Math.min(maxBlocks, temp.length * 2);
                ShipBlock[] grow = new ShipBlock[newLen];
                System.arraycopy(temp, 0, grow, 0, temp.length);
                temp = grow;
            }

            temp[count++] = new ShipBlock(local.x, local.y, local.z, id, type, localRot);

            // bounds log
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
            minZ = Math.min(minZ, p.z);
            maxZ = Math.max(maxZ, p.z);

            // Só expande vizinhos PORQUE este bloco é sólido (não-empty)
            expand(queue, visited, hx, hy, hz, p.x + 1, p.y, p.z);
            expand(queue, visited, hx, hy, hz, p.x - 1, p.y, p.z);
            expand(queue, visited, hx, hy, hz, p.x, p.y + 1, p.z);
            expand(queue, visited, hx, hy, hz, p.x, p.y - 1, p.z);
            expand(queue, visited, hx, hy, hz, p.x, p.y, p.z + 1);
            expand(queue, visited, hx, hy, hz, p.x, p.y, p.z - 1);
        }

        ShipLogger.info("[SHIPS] ShipAssembler: capturou " + count + " blocos. Bounds=("
                + minX + "," + minY + "," + minZ + ") -> (" + maxX + "," + maxY + "," + maxZ + ")");

        ShipBlock[] blocks = new ShipBlock[count];
        System.arraycopy(temp, 0, blocks, 0, count);
        return new ShipStructure(blocks);
    }

    private static void expand(ArrayDeque<Vector3i> queue,
                               LongOpenHashSet visited,
                               int hx, int hy, int hz,
                               int x, int y, int z) {

        // limites iguais aos do loop (pra não enfileirar lixo)
        if (Math.abs(x - hx) > MAX_RADIUS_XZ) return;
        if (Math.abs(z - hz) > MAX_RADIUS_XZ) return;
        if (y < hy - MAX_DOWN_Y) return;
        if (y > hy + MAX_UP_Y) return;

        if (y < 0 || y >= 320) return;

        long k = pack(x, y, z);
        if (visited.add(k)) {
            queue.add(new Vector3i(x, y, z));
        }
    }

    @SuppressWarnings("removal")
    private static int getRotationIndex(World world, Vector3i pos) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) return 0;

        int lx = ChunkUtil.localCoordinate(pos.x);
        int lz = ChunkUtil.localCoordinate(pos.z);
        return chunk.getRotationIndex(lx, pos.y, lz);
    }

    private static int floorMod(int a, int b) {
        int r = a % b;
        return (r < 0) ? (r + b) : r;
    }
}
