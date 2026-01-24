package com.michallves.ships.ship.core;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;

public final class ShipEntity {

    private static final double COLLISION_EPS = 1.0e-6;

    private final ShipStructure structure;
    private final Vector3d position = new Vector3d();
    private final ShipQuaternion rotation = new ShipQuaternion();
    private final Vector3d lastTickPos = new Vector3d();
    private boolean hasLastTick = false;
    private float lastTickYaw = 0.0f;
    private double moveDeltaX = 0.0;
    private double moveDeltaZ = 0.0;
    private float yawDelta = 0.0f;

    private final Vector3d tmpWorldCenter = new Vector3d();
    private final Vector3d tmpLocalCenter = new Vector3d();
    private final Vector3d tmpLocalDelta = new Vector3d();
    private final Vector3d tmpLocalResolved = new Vector3d();
    private final Matrix3 tmpMatrix = new Matrix3();

    public ShipEntity(ShipStructure structure) {
        this.structure = structure;
    }

    public ShipStructure getStructure() {
        return structure;
    }

    public Vector3d getPosition() {
        return position;
    }

    public ShipQuaternion getRotation() {
        return rotation;
    }

    public void setPosition(Vector3d pos) {
        this.position.assign(pos);
    }

    public void setRotation(ShipQuaternion q) {
        this.rotation.assign(q);
    }

    public void updateMotionTracking(float currentYawDeg) {
        if (!hasLastTick) {
            lastTickPos.assign(position);
            lastTickYaw = currentYawDeg;
            moveDeltaX = 0.0;
            moveDeltaZ = 0.0;
            yawDelta = 0.0f;
            hasLastTick = true;
            return;
        }

        moveDeltaX = position.getX() - lastTickPos.getX();
        moveDeltaZ = position.getZ() - lastTickPos.getZ();
        yawDelta = wrapDegrees(currentYawDeg - lastTickYaw);

        lastTickPos.assign(position);
        lastTickYaw = currentYawDeg;
    }

    public double getMoveDeltaX() {
        return moveDeltaX;
    }

    public double getMoveDeltaZ() {
        return moveDeltaZ;
    }

    public float getYawDelta() {
        return yawDelta;
    }

    public boolean checkCollision(Vector3d playerPos, Box playerBox, Vector3d playerDelta, Vector3d outPos) {
        if (outPos == null) {
            outPos = new Vector3d();
        }
        if (structure == null || structure.size() == 0 || playerPos == null || playerBox == null) {
            outPos.assign(playerPos);
            return false;
        }

        double halfX = (playerBox.max.getX() - playerBox.min.getX()) * 0.5;
        double halfY = (playerBox.max.getY() - playerBox.min.getY()) * 0.5;
        double halfZ = (playerBox.max.getZ() - playerBox.min.getZ()) * 0.5;

        double centerOffsetX = (playerBox.min.getX() + playerBox.max.getX()) * 0.5;
        double centerOffsetY = (playerBox.min.getY() + playerBox.max.getY()) * 0.5;
        double centerOffsetZ = (playerBox.min.getZ() + playerBox.max.getZ()) * 0.5;

        tmpWorldCenter.assign(
                playerPos.getX() + centerOffsetX,
                playerPos.getY() + centerOffsetY,
                playerPos.getZ() + centerOffsetZ
        );

        worldToLocal(tmpWorldCenter, tmpLocalCenter);

        rotation.toMatrix(tmpMatrix);
        tmpMatrix.transpose();

        double absM00 = Math.abs(tmpMatrix.m00) + COLLISION_EPS;
        double absM01 = Math.abs(tmpMatrix.m01) + COLLISION_EPS;
        double absM02 = Math.abs(tmpMatrix.m02) + COLLISION_EPS;
        double absM10 = Math.abs(tmpMatrix.m10) + COLLISION_EPS;
        double absM11 = Math.abs(tmpMatrix.m11) + COLLISION_EPS;
        double absM12 = Math.abs(tmpMatrix.m12) + COLLISION_EPS;
        double absM20 = Math.abs(tmpMatrix.m20) + COLLISION_EPS;
        double absM21 = Math.abs(tmpMatrix.m21) + COLLISION_EPS;
        double absM22 = Math.abs(tmpMatrix.m22) + COLLISION_EPS;

        double localHalfX = absM00 * halfX + absM01 * halfY + absM02 * halfZ;
        double localHalfY = absM10 * halfX + absM11 * halfY + absM12 * halfZ;
        double localHalfZ = absM20 * halfX + absM21 * halfY + absM22 * halfZ;

        if (playerDelta != null) {
            worldToLocalDelta(playerDelta, tmpLocalDelta);
        } else {
            tmpLocalDelta.assign(0.0, 0.0, 0.0);
        }

        Vector3d oldLocalCenter = tmpLocalResolved.assign(tmpLocalCenter).subtract(tmpLocalDelta);
        Aabb oldBox = Aabb.fromCenter(oldLocalCenter, localHalfX, localHalfY, localHalfZ);
        Aabb newBox = Aabb.fromCenter(tmpLocalCenter, localHalfX, localHalfY, localHalfZ);

        double unionMinX = Math.min(oldBox.minX, newBox.minX);
        double unionMinY = Math.min(oldBox.minY, newBox.minY);
        double unionMinZ = Math.min(oldBox.minZ, newBox.minZ);
        double unionMaxX = Math.max(oldBox.maxX, newBox.maxX);
        double unionMaxY = Math.max(oldBox.maxY, newBox.maxY);
        double unionMaxZ = Math.max(oldBox.maxZ, newBox.maxZ);

        int minX = Math.max((int) Math.floor(unionMinX), structure.getMinX());
        int maxX = Math.min((int) Math.floor(unionMaxX), structure.getMaxX());
        int minY = Math.max((int) Math.floor(unionMinY), structure.getMinY());
        int maxY = Math.min((int) Math.floor(unionMaxY), structure.getMaxY());
        int minZ = Math.max((int) Math.floor(unionMinZ), structure.getMinZ());
        int maxZ = Math.min((int) Math.floor(unionMaxZ), structure.getMaxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            outPos.assign(playerPos);
            return false;
        }

        double originalDx = newBox.centerX() - oldBox.centerX();
        double originalDy = newBox.centerY() - oldBox.centerY();
        double originalDz = newBox.centerZ() - oldBox.centerZ();

        double dy = originalDy;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (structure.getBlockAt(x, y, z) == null) continue;
                    dy = calculateYOffset(oldBox, dy, x, y, z);
                }
            }
        }
        dy = applyBackoff(dy, originalDy);
        oldBox = oldBox.offset(0.0, dy, 0.0);

        double dx = originalDx;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (structure.getBlockAt(x, y, z) == null) continue;
                    dx = calculateXOffset(oldBox, dx, x, y, z);
                }
            }
        }
        dx = applyBackoff(dx, originalDx);
        oldBox = oldBox.offset(dx, 0.0, 0.0);

        double dz = originalDz;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (structure.getBlockAt(x, y, z) == null) continue;
                    dz = calculateZOffset(oldBox, dz, x, y, z);
                }
            }
        }
        dz = applyBackoff(dz, originalDz);
        oldBox = oldBox.offset(0.0, 0.0, dz);

        boolean moved = Math.abs(dx - originalDx) > COLLISION_EPS
                || Math.abs(dy - originalDy) > COLLISION_EPS
                || Math.abs(dz - originalDz) > COLLISION_EPS;

        if (!moved) {
            outPos.assign(playerPos);
            return false;
        }

        tmpLocalResolved.assign(oldBox.centerX(), oldBox.centerY(), oldBox.centerZ());
        Vector3d resolvedWorldCenter = localToWorld(tmpLocalResolved, tmpWorldCenter);
        outPos.assign(
                resolvedWorldCenter.getX() - centerOffsetX,
                resolvedWorldCenter.getY() - centerOffsetY,
                resolvedWorldCenter.getZ() - centerOffsetZ
        );
        return true;
    }

    private Vector3d worldToLocal(Vector3d world, Vector3d out) {
        out.assign(world).subtract(position);
        rotation.rotateInverse(out, out);
        return out;
    }

    private Vector3d worldToLocalDelta(Vector3d worldDelta, Vector3d out) {
        out.assign(worldDelta);
        rotation.rotateInverse(out, out);
        return out;
    }

    private Vector3d localToWorld(Vector3d local, Vector3d out) {
        rotation.rotate(local, out);
        out.add(position);
        return out;
    }

    private static double calculateYOffset(Aabb box, double dy, int bx, int by, int bz) {
        double bMinX = bx;
        double bMaxX = bx + 1.0;
        double bMinY = by;
        double bMaxY = by + 1.0;
        double bMinZ = bz;
        double bMaxZ = bz + 1.0;

        if (bMaxX <= box.minX || bMinX >= box.maxX || bMaxZ <= box.minZ || bMinZ >= box.maxZ) return dy;
        if (dy > 0 && bMinY >= box.maxY) {
            double d = bMinY - box.maxY;
            if (d < dy) dy = d;
        } else if (dy < 0 && bMaxY <= box.minY) {
            double d = bMaxY - box.minY;
            if (d > dy) dy = d;
        }
        return dy;
    }

    private static double calculateXOffset(Aabb box, double dx, int bx, int by, int bz) {
        double bMinX = bx;
        double bMaxX = bx + 1.0;
        double bMinY = by;
        double bMaxY = by + 1.0;
        double bMinZ = bz;
        double bMaxZ = bz + 1.0;

        if (bMaxY <= box.minY || bMinY >= box.maxY || bMaxZ <= box.minZ || bMinZ >= box.maxZ) return dx;
        if (dx > 0 && bMinX >= box.maxX) {
            double d = bMinX - box.maxX;
            if (d < dx) dx = d;
        } else if (dx < 0 && bMaxX <= box.minX) {
            double d = bMaxX - box.minX;
            if (d > dx) dx = d;
        }
        return dx;
    }

    private static double calculateZOffset(Aabb box, double dz, int bx, int by, int bz) {
        double bMinX = bx;
        double bMaxX = bx + 1.0;
        double bMinY = by;
        double bMaxY = by + 1.0;
        double bMinZ = bz;
        double bMaxZ = bz + 1.0;

        if (bMaxX <= box.minX || bMinX >= box.maxX || bMaxY <= box.minY || bMinY >= box.maxY) return dz;
        if (dz > 0 && bMinZ >= box.maxZ) {
            double d = bMinZ - box.maxZ;
            if (d < dz) dz = d;
        } else if (dz < 0 && bMaxZ <= box.minZ) {
            double d = bMaxZ - box.minZ;
            if (d > dz) dz = d;
        }
        return dz;
    }

    private static double applyBackoff(double d, double original) {
        return (d != 0.0 && d != original) ? (d > 0 ? d - 1.0e-4 : d + 1.0e-4) : d;
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    private static final class Aabb {
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;

        private Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        static Aabb fromCenter(Vector3d c, double hx, double hy, double hz) {
            return new Aabb(c.getX() - hx, c.getY() - hy, c.getZ() - hz, c.getX() + hx, c.getY() + hy, c.getZ() + hz);
        }

        Aabb offset(double x, double y, double z) {
            return new Aabb(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
        }

        double centerX() {
            return (minX + maxX) * 0.5;
        }

        double centerY() {
            return (minY + maxY) * 0.5;
        }

        double centerZ() {
            return (minZ + maxZ) * 0.5;
        }
    }

    public static final class ShipQuaternion {
        private double x;
        private double y;
        private double z;
        private double w = 1.0;

        public ShipQuaternion() {
        }

        public ShipQuaternion assign(ShipQuaternion other) {
            this.x = other.x;
            this.y = other.y;
            this.z = other.z;
            this.w = other.w;
            return this;
        }

        public ShipQuaternion set(double x, double y, double z, double w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
            return this;
        }

        public ShipQuaternion setFromYawPitchRoll(double yaw, double pitch, double roll) {
            double cy = Math.cos(yaw * 0.5);
            double sy = Math.sin(yaw * 0.5);
            double cp = Math.cos(pitch * 0.5);
            double sp = Math.sin(pitch * 0.5);
            double cr = Math.cos(roll * 0.5);
            double sr = Math.sin(roll * 0.5);

            this.w = cr * cp * cy + sr * sp * sy;
            this.x = sr * cp * cy - cr * sp * sy;
            this.y = cr * sp * cy + sr * cp * sy;
            this.z = cr * cp * sy - sr * sp * cy;
            return normalize();
        }

        public ShipQuaternion normalize() {
            double len = Math.sqrt(x * x + y * y + z * z + w * w);
            if (len > 0.0) {
                double inv = 1.0 / len;
                x *= inv;
                y *= inv;
                z *= inv;
                w *= inv;
            }
            return this;
        }

        public Vector3d rotate(Vector3d v, Vector3d out) {
            double vx = v.getX();
            double vy = v.getY();
            double vz = v.getZ();

            double tx = 2.0 * (y * vz - z * vy);
            double ty = 2.0 * (z * vx - x * vz);
            double tz = 2.0 * (x * vy - y * vx);

            out.assign(
                    vx + w * tx + (y * tz - z * ty),
                    vy + w * ty + (z * tx - x * tz),
                    vz + w * tz + (x * ty - y * tx)
            );
            return out;
        }

        public Vector3d rotateInverse(Vector3d v, Vector3d out) {
            double vx = v.getX();
            double vy = v.getY();
            double vz = v.getZ();

            double tx = 2.0 * (-y * vz + z * vy);
            double ty = 2.0 * (-z * vx + x * vz);
            double tz = 2.0 * (-x * vy + y * vx);

            out.assign(
                    vx + w * tx + (-y * tz + z * ty),
                    vy + w * ty + (-z * tx + x * tz),
                    vz + w * tz + (-x * ty + y * tx)
            );
            return out;
        }

        private void toMatrix(Matrix3 out) {
            double xx = x * x;
            double yy = y * y;
            double zz = z * z;
            double xy = x * y;
            double xz = x * z;
            double yz = y * z;
            double wx = w * x;
            double wy = w * y;
            double wz = w * z;

            out.m00 = 1.0 - 2.0 * (yy + zz);
            out.m01 = 2.0 * (xy - wz);
            out.m02 = 2.0 * (xz + wy);

            out.m10 = 2.0 * (xy + wz);
            out.m11 = 1.0 - 2.0 * (xx + zz);
            out.m12 = 2.0 * (yz - wx);

            out.m20 = 2.0 * (xz - wy);
            out.m21 = 2.0 * (yz + wx);
            out.m22 = 1.0 - 2.0 * (xx + yy);
        }
    }

    private static final class Matrix3 {
        double m00, m01, m02;
        double m10, m11, m12;
        double m20, m21, m22;

        void transpose() {
            double t01 = m01;
            double t02 = m02;
            double t12 = m12;
            m01 = m10;
            m02 = m20;
            m10 = t01;
            m12 = m21;
            m20 = t02;
            m21 = t12;
        }
    }
}
