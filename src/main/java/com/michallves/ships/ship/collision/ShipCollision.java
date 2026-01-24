package com.michallves.ships.ship.collision;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.michallves.ships.ship.assembly.ShipBlock;
import com.michallves.ships.ship.assembly.ShipStructure;
import com.michallves.ships.utils.ShipLogger;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;

public final class ShipCollision {

    // --- Configurações Físicas ---
    private static final double COLLISION_EPS = 1.0e-4;
    private static final double SNAP_DISTANCE = 0.6;
    private static final double PLAYER_DECK_TOLERANCE = 0.35;
    private static final int CHUNK_NOT_LOADED = Integer.MIN_VALUE;
    
    // --- Otimização Espacial ---
    private static final int LOCAL_BIAS = 1 << 20;
    private static final long LOCAL_MASK = 0x1FFFFFL;

    private final ShipStructure structure;
    private final Vector3d centerOffset;
    private final LongOpenHashSet blockIndex = new LongOpenHashSet();

    // Cache de limites
    private double minLocalX, maxLocalX;
    private double minLocalZ, maxLocalZ;
    private int minDy, maxDy;
    private double collisionRadius;
    
    private int debugTick = 0;

    public ShipCollision(ShipStructure structure, Vector3d centerOffset) {
        this.structure = structure;
        this.centerOffset = centerOffset;
        buildCollisionIndex();
    }

    // ============================================================================================
    // LÓGICA 1: COLISÃO JOGADOR vs NAVIO (Anti-Jitter + Physics)
    // ============================================================================================

    public Vector3d resolvePlayerCollision(Vector3d playerPos,
                                           Vector3d playerDelta,
                                           Box playerBox,
                                           Vector3d origin,
                                           float yawDeg,
                                           double lastMoveX,
                                           double lastMoveZ,
                                           float lastYawDelta,
                                           boolean activeEntity) {
        if (!activeEntity || playerDelta == null) return null;

        Vector3d safePlayerPos = new Vector3d(playerPos);
        Vector3d safeDelta = new Vector3d(playerDelta);

        double dx = safePlayerPos.getX() - origin.getX();
        double dz = safePlayerPos.getZ() - origin.getZ();
        double maxR = collisionRadius + 2.0;
        if (dx * dx + dz * dz > maxR * maxR) return null;

        double yawRad = Math.toRadians(yawDeg);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        double halfX = (playerBox.max.getX() - playerBox.min.getX()) * 0.5;
        double halfY = (playerBox.max.getY() - playerBox.min.getY()) * 0.5;
        double halfZ = (playerBox.max.getZ() - playerBox.min.getZ()) * 0.5;
        
        Vector3d worldCenter = new Vector3d(safePlayerPos).add(
            (playerBox.min.getX() + playerBox.max.getX()) * 0.5,
            (playerBox.min.getY() + playerBox.max.getY()) * 0.5,
            (playerBox.min.getZ() + playerBox.max.getZ()) * 0.5
        );

        Vector3d localCenter = toLocal(worldCenter, origin, cos, sin);

        double absCos = Math.abs(cos) + COLLISION_EPS;
        double absSin = Math.abs(sin) + COLLISION_EPS;
        double localHalfX = absCos * halfX + absSin * halfZ;
        double localHalfZ = absSin * halfX + absCos * halfZ;

        if (isOutsideBounds(localCenter, localHalfX, localHalfZ)) {
            return null;
        }

        boolean onDeck = isPlayerOnDeck(localCenter, localHalfX, localHalfZ, safePlayerPos, playerBox, origin.getY());
        Vector3d movedCenter = worldCenter;
        boolean movedByShip = false;

        if (onDeck && (Math.abs(lastMoveX) > 1.0e-6 || Math.abs(lastMoveZ) > 1.0e-6 || Math.abs(lastYawDelta) > 0.001f)) {
            movedCenter = applyShipMotion(worldCenter, origin, lastMoveX, lastMoveZ, lastYawDelta);
            movedByShip = true;
        }

        boolean snapped = false;
        if (onDeck && safeDelta.getY() <= 0.01) {
             Vector3d snappedPos = trySnapToDeck(movedCenter, origin, cos, sin, localHalfX, halfY, localHalfZ);
             if (snappedPos != null) {
                 movedCenter = snappedPos;
                 snapped = true;
                 safeDelta = new Vector3d(safeDelta.getX(), 0, safeDelta.getZ());
             }
        }

        Vector3d baseCenter = new Vector3d(movedCenter);
        Vector3d oldCenter = new Vector3d(baseCenter).subtract(safeDelta);
        
        Vector3d oldLocalCenter = toLocal(oldCenter, origin, cos, sin);
        Vector3d newLocalCenter = toLocal(baseCenter, origin, cos, sin);
        
        Aabb oldBox = Aabb.fromCenter(oldLocalCenter, localHalfX, halfY, localHalfZ);
        Aabb newBox = Aabb.fromCenter(newLocalCenter, localHalfX, halfY, localHalfZ);

        List<Aabb> obstacles = trajectoryQuery(oldBox, newBox);
        
        if (obstacles.isEmpty() && !movedByShip && !snapped) {
            return null;
        }

        double originalDx = newLocalCenter.getX() - oldLocalCenter.getX();
        double originalDy = newLocalCenter.getY() - oldLocalCenter.getY();
        double originalDz = newLocalCenter.getZ() - oldLocalCenter.getZ();

        double dy = originalDy;
        for (Aabb obstacle : obstacles) dy = calculateYOffset(oldBox, dy, obstacle);
        dy = applyBackoff(dy, originalDy);
        oldBox = oldBox.offset(0.0, dy, 0.0);

        double dxLocal = originalDx;
        for (Aabb obstacle : obstacles) dxLocal = calculateXOffset(oldBox, dxLocal, obstacle);
        dxLocal = applyBackoff(dxLocal, originalDx);
        oldBox = oldBox.offset(dxLocal, 0.0, 0.0);

        double dzLocal = originalDz;
        for (Aabb obstacle : obstacles) dzLocal = calculateZOffset(oldBox, dzLocal, obstacle);
        dzLocal = applyBackoff(dzLocal, originalDz);
        oldBox = oldBox.offset(0.0, 0.0, dzLocal);

        boolean collided = Math.abs(dxLocal - originalDx) > 1.0e-9 
                        || Math.abs(dy - originalDy) > 1.0e-9 
                        || Math.abs(dzLocal - originalDz) > 1.0e-9;

        if (!collided && !movedByShip && !snapped) {
            return null;
        }

        Vector3d resolvedCenterWorld = toWorld(oldBox.center(), origin, cos, sin);
        
        double centerOffsetX = (playerBox.min.getX() + playerBox.max.getX()) * 0.5;
        double centerOffsetY = (playerBox.min.getY() + playerBox.max.getY()) * 0.5;
        double centerOffsetZ = (playerBox.min.getZ() + playerBox.max.getZ()) * 0.5;

        return new Vector3d(
                resolvedCenterWorld.getX() - centerOffsetX,
                resolvedCenterWorld.getY() - centerOffsetY,
                resolvedCenterWorld.getZ() - centerOffsetZ
        );
    }

    // ============================================================================================
    // LÓGICA 2: COLISÃO NAVIO vs MUNDO (Corrigida para ignorar Água)
    // ============================================================================================

    public boolean collidesAt(World world, double originX, double originY, double originZ, float yawDeg) {
        if (world == null || structure.size() == 0) return false;

        double yawRad = Math.toRadians(yawDeg);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        // Otimização: Não precisamos alocar objetos aqui se possível
        for (ShipBlock b : structure.getBlocks()) {
            // Coordenadas locais (centralizadas)
            double lx = (b.dx + 0.5) - centerOffset.getX();
            double lz = (b.dz + 0.5) - centerOffset.getZ();

            // Rotacionar
            double rx = lx * cos - lz * sin;
            double rz = lx * sin + lz * cos;

            // Converter para Mundo
            double wx = originX + rx;
            double wy = originY + b.dy + 0.5;
            double wz = originZ + rz;

            int bx = (int) Math.floor(wx);
            int by = (int) Math.floor(wy);
            int bz = (int) Math.floor(wz);
            
            // Check rápido: Chunk carregado?
            int id = getBlockIfLoaded(world, bx, by, bz);
            if (id == CHUNK_NOT_LOADED) {
                return true;
            }
            if (id == BlockType.EMPTY_ID) continue;

            BlockType type = BlockType.getAssetMap().getAsset(id);
            if (type == null) continue;

            // Colide somente com blocos sólidos (material Solid).
            if (type.getMaterial() == BlockMaterial.Solid) {
                 return true;
            }
        }
        return false;
    }

    // ============================================================================================
    // MÉTODOS AUXILIARES
    // ============================================================================================

    private Vector3d trySnapToDeck(Vector3d worldCenter, Vector3d origin, double cos, double sin, 
                                   double halfX, double halfY, double halfZ) {
        Vector3d localCenter = toLocal(worldCenter, origin, cos, sin);
        double feetY = localCenter.getY() - halfY;
        
        int minDx = (int) Math.floor(localCenter.getX() - halfX + centerOffset.getX());
        int maxDx = (int) Math.floor(localCenter.getX() + halfX + centerOffset.getX());
        int minDz = (int) Math.floor(localCenter.getZ() - halfZ + centerOffset.getZ());
        int maxDz = (int) Math.floor(localCenter.getZ() + halfZ + centerOffset.getZ());
        
        int checkY = (int) Math.floor(feetY);
        double highestBlockY = -99999;
        boolean found = false;

        for (int dy = checkY - 1; dy <= checkY; dy++) {
             for (int dx = minDx; dx <= maxDx; dx++) {
                for (int dz = minDz; dz <= maxDz; dz++) {
                    if (blockIndex.contains(packLocal(dx, dy, dz))) {
                        double topY = dy + 1.0; 
                        if (topY > highestBlockY) {
                            highestBlockY = topY;
                            found = true;
                        }
                    }
                }
            }
        }

        if (found) {
            double dist = feetY - highestBlockY;
            if (Math.abs(dist) < SNAP_DISTANCE) {
                double newLocalY = highestBlockY + halfY + COLLISION_EPS;
                return toWorld(new Vector3d(localCenter.getX(), newLocalY, localCenter.getZ()), origin, cos, sin);
            }
        }
        return null;
    }

    private static int getBlockIfLoaded(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return CHUNK_NOT_LOADED;
        }

        int lx = ChunkUtil.localCoordinate(x);
        int lz = ChunkUtil.localCoordinate(z);
        return chunk.getBlock(lx, y, lz);
    }

    private List<Aabb> trajectoryQuery(Aabb oldBox, Aabb newBox) {
        Aabb trajectoryBox = oldBox.union(newBox);
        List<Aabb> obstacles = new ArrayList<>();

        int minDx = (int) Math.floor(trajectoryBox.minX + centerOffset.getX());
        int maxDx = (int) Math.floor(trajectoryBox.maxX + centerOffset.getX());
        int minDyCheck = (int) Math.floor(trajectoryBox.minY);
        int maxDyCheck = (int) Math.ceil(trajectoryBox.maxY); 
        int minDz = (int) Math.floor(trajectoryBox.minZ + centerOffset.getZ());
        int maxDz = (int) Math.floor(trajectoryBox.maxZ + centerOffset.getZ());

        for (int dx = minDx; dx <= maxDx; dx++) {
            for (int dy = minDyCheck; dy <= maxDyCheck; dy++) {
                for (int dz = minDz; dz <= maxDz; dz++) {
                    if (!blockIndex.contains(packLocal(dx, dy, dz))) continue;
                    double bMinX = dx - centerOffset.getX();
                    double bMinY = dy;
                    double bMinZ = dz - centerOffset.getZ();
                    
                    Aabb block = new Aabb(bMinX, bMinY, bMinZ, bMinX + 1.0, bMinY + 1.0, bMinZ + 1.0);
                    if (block.intersects(trajectoryBox)) {
                        obstacles.add(block);
                    }
                }
            }
        }
        return obstacles;
    }

    private boolean isPlayerOnDeck(Vector3d localCenter, double localHalfX, double localHalfZ, 
                                   Vector3d playerPos, Box playerBox, double originY) {
        double footLocalY = (playerPos.getY() + playerBox.min.getY()) - originY;
        
        int minDx = (int) Math.floor(localCenter.getX() - localHalfX + centerOffset.getX());
        int maxDx = (int) Math.floor(localCenter.getX() + localHalfX + centerOffset.getX());
        int minDz = (int) Math.floor(localCenter.getZ() - localHalfZ + centerOffset.getZ());
        int maxDz = (int) Math.floor(localCenter.getZ() + localHalfZ + centerOffset.getZ());
        
        int feetBlockY = (int) Math.floor(footLocalY);

        for (int dy = feetBlockY - 2; dy <= feetBlockY; dy++) {
            for (int dx = minDx; dx <= maxDx; dx++) {
                for (int dz = minDz; dz <= maxDz; dz++) {
                    if (blockIndex.contains(packLocal(dx, dy, dz))) {
                         double top = dy + 1.0;
                         if (footLocalY >= top - 0.5 && footLocalY <= top + PLAYER_DECK_TOLERANCE) {
                             return true;
                         }
                    }
                }
            }
        }
        return false;
    }

    private Vector3d applyShipMotion(Vector3d worldCenter, Vector3d origin, double lmX, double lmZ, float lYaw) {
        double prevOriginX = origin.getX() - lmX;
        double prevOriginZ = origin.getZ() - lmZ;
        Vector3d rel = new Vector3d(worldCenter.getX() - prevOriginX, worldCenter.getY() - origin.getY(), worldCenter.getZ() - prevOriginZ);

        double deltaRad = Math.toRadians(lYaw);
        double rcos = Math.cos(deltaRad);
        double rsin = Math.sin(deltaRad);
        
        return new Vector3d(
                prevOriginX + (rel.getX() * rcos - rel.getZ() * rsin) + lmX,
                worldCenter.getY(),
                prevOriginZ + (rel.getX() * rsin + rel.getZ() * rcos) + lmZ 
        );
    }
    
    private boolean isOutsideBounds(Vector3d local, double hx, double hz) {
        return local.getX() + hx < minLocalX - 1.0 || local.getX() - hx > maxLocalX + 1.0 ||
               local.getZ() + hz < minLocalZ - 1.0 || local.getZ() - hz > maxLocalZ + 1.0;
    }

    private Vector3d toLocal(Vector3d world, Vector3d origin, double cos, double sin) {
        double rx = world.getX() - origin.getX();
        double rz = world.getZ() - origin.getZ();
        return new Vector3d(rx * cos + rz * sin, world.getY() - origin.getY(), -rx * sin + rz * cos);
    }

    private Vector3d toWorld(Vector3d local, Vector3d origin, double cos, double sin) {
        return new Vector3d(
            local.getX() * cos - local.getZ() * sin + origin.getX(),
            local.getY() + origin.getY(),
            local.getX() * sin + local.getZ() * cos + origin.getZ()
        );
    }
    
    private void logDebug(double ox, double oy, double oz, double rx, double ry, double rz, boolean deck, boolean ship, int obs, boolean snap) {
        debugTick++;
        if ((debugTick % 20) == 0) {
            ShipLogger.debug(String.format("[Collision] Snap=%s OnDeck=%s MovedByShip=%s Obstacles=%d", snap, deck, ship, obs));
        }
    }

    // --- Tratamento de Eixos AABB ---
    private static double calculateYOffset(Aabb box, double dy, Aabb other) {
        if (other.maxX <= box.minX || other.minX >= box.maxX || other.maxZ <= box.minZ || other.minZ >= box.maxZ) return dy;
        if (dy > 0 && other.minY >= box.maxY) { double d = other.minY - box.maxY; if (d < dy) dy = d; }
        else if (dy < 0 && other.maxY <= box.minY) { double d = other.maxY - box.minY; if (d > dy) dy = d; }
        return dy;
    }

    private static double calculateXOffset(Aabb box, double dx, Aabb other) {
        if (other.maxY <= box.minY || other.minY >= box.maxY || other.maxZ <= box.minZ || other.minZ >= box.maxZ) return dx;
        if (dx > 0 && other.minX >= box.maxX) { double d = other.minX - box.maxX; if (d < dx) dx = d; }
        else if (dx < 0 && other.maxX <= box.minX) { double d = other.maxX - box.minX; if (d > dx) dx = d; }
        return dx;
    }

    private static double calculateZOffset(Aabb box, double dz, Aabb other) {
        if (other.maxX <= box.minX || other.minX >= box.maxX || other.maxY <= box.minY || other.minY >= box.maxY) return dz;
        if (dz > 0 && other.minZ >= box.maxZ) { double d = other.minZ - box.maxZ; if (d < dz) dz = d; }
        else if (dz < 0 && other.maxZ <= box.minZ) { double d = other.maxZ - box.minZ; if (d > dz) dz = d; }
        return dz;
    }

    private static double applyBackoff(double d, double original) {
        return (d != 0.0 && d != original) ? (d > 0 ? d - 1.0e-3 : d + 1.0e-3) : d;
    }

    private void buildCollisionIndex() {
        blockIndex.clear();
        if (structure.size() == 0) return;

        minDy = Integer.MAX_VALUE; maxDy = Integer.MIN_VALUE;
        int minDx = Integer.MAX_VALUE, maxDx = Integer.MIN_VALUE;
        int minDz = Integer.MAX_VALUE, maxDz = Integer.MIN_VALUE;
        double maxRadiusSq = 0;

        for (ShipBlock b : structure.getBlocks()) {
            blockIndex.add(packLocal(b.dx, b.dy, b.dz));
            minDx = Math.min(minDx, b.dx); maxDx = Math.max(maxDx, b.dx);
            minDz = Math.min(minDz, b.dz); maxDz = Math.max(maxDz, b.dz);
            minDy = Math.min(minDy, b.dy); maxDy = Math.max(maxDy, b.dy);
            
            double lx = b.dx - centerOffset.getX();
            double lz = b.dz - centerOffset.getZ();
            double rSq = lx*lx + lz*lz;
            if(rSq > maxRadiusSq) maxRadiusSq = rSq;
        }
        
        minLocalX = minDx - centerOffset.getX();
        maxLocalX = maxDx - centerOffset.getX() + 1.0;
        minLocalZ = minDz - centerOffset.getZ();
        maxLocalZ = maxDz - centerOffset.getZ() + 1.0;
        collisionRadius = Math.sqrt(maxRadiusSq) + 2.0;
    }

    private static long packLocal(int x, int y, int z) {
        return (((long)(x + LOCAL_BIAS) & LOCAL_MASK) << 42) |
               (((long)(z + LOCAL_BIAS) & LOCAL_MASK) << 21) |
               ((long)(y + LOCAL_BIAS) & LOCAL_MASK);
    }
    
    private static final class Aabb {
        final double minX, minY, minZ, maxX, maxY, maxZ;
        Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        static Aabb fromCenter(Vector3d c, double hx, double hy, double hz) {
            return new Aabb(c.getX()-hx, c.getY()-hy, c.getZ()-hz, c.getX()+hx, c.getY()+hy, c.getZ()+hz);
        }
        Aabb offset(double x, double y, double z) { return new Aabb(minX+x, minY+y, minZ+z, maxX+x, maxY+y, maxZ+z); }
        Aabb union(Aabb o) {
            return new Aabb(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
                            Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
        }
        boolean intersects(Aabb o) {
            return o.maxX > minX && o.minX < maxX && o.maxY > minY && o.minY < maxY && o.maxZ > minZ && o.minZ < maxZ;
        }
        Vector3d center() { return new Vector3d((minX+maxX)*0.5, (minY+maxY)*0.5, (minZ+maxZ)*0.5); }
    }
}
