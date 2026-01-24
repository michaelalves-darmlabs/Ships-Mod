package com.michallves.ships.ship.core;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.michallves.ships.ship.assembly.ShipBlock;
import com.michallves.ships.ship.assembly.ShipStructure;

public final class ShipGeometry {

    private final ShipState state;
    private final Vector3d centerOffset = new Vector3d();
    private final Vector3i helmBlockWorld;

    public ShipGeometry(ShipStructure structure, ShipState state, Vector3i helmBlock, float yawDeg) {
        this.state = state;
        this.helmBlockWorld = new Vector3i(helmBlock);
        computeCenterOffset(structure);
        state.setYawDeg(yawDeg);
        state.setLastYawDeg(yawDeg);
        updateOriginFromHelm(helmBlock, yawDeg);
    }

    public Vector3d getOrigin() {
        return state.getOrigin();
    }

    public Vector3d getCenterOffset() {
        return centerOffset;
    }

    public Vector3i getHelmBlockWorld() {
        return helmBlockWorld;
    }

    public Vector3d computePilotAnchorPosition(float yawDeg) {
        Vector3d helmWorld = computeHelmWorldPosition(yawDeg);
        Vector3d helmCenter = new Vector3d(helmWorld.getX() + 0.5, helmWorld.getY(), helmWorld.getZ() + 0.5);

        double yawRad = Math.toRadians(yawDeg);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        double radius = 0.85;
        Vector3d anchor = new Vector3d(
                helmCenter.getX() - fx * radius,
                helmCenter.getY(),
                helmCenter.getZ() - fz * radius
        );

        anchor.setX(Math.round(anchor.getX() * 16.0) / 16.0);
        anchor.setZ(Math.round(anchor.getZ() * 16.0) / 16.0);

        return anchor;
    }

    public Vector3d computeWorldPosContinuous(int dx, int dy, int dz, float yawDeg) {
        double yawRad = Math.toRadians(yawDeg);

        double x = dx - centerOffset.getX();
        double z = dz - centerOffset.getZ();

        double rx = x * Math.cos(yawRad) - z * Math.sin(yawRad);
        double rz = x * Math.sin(yawRad) + z * Math.cos(yawRad);

        Vector3d origin = state.getOrigin();
        return new Vector3d(
                origin.getX() + rx,
                origin.getY() + dy,
                origin.getZ() + rz
        );
    }

    public void updateOriginFromHelm(Vector3i helmBlock, float yawDeg) {
        Vector3d offset = rotateLocal(centerOffset.getX(), centerOffset.getZ(), yawDeg);
        Vector3d origin = state.getOrigin();
        origin.setX(helmBlock.x + offset.getX());
        origin.setY(helmBlock.y);
        origin.setZ(helmBlock.z + offset.getZ());
    }

    public Vector3d computeHelmWorldPosition(float yawDeg) {
        Vector3d offset = rotateLocal(-centerOffset.getX(), -centerOffset.getZ(), yawDeg);
        Vector3d origin = state.getOrigin();
        return new Vector3d(origin.getX() + offset.getX(), origin.getY(), origin.getZ() + offset.getZ());
    }

    private static Vector3d rotateLocal(double x, double z, float yawDeg) {
        double yawRad = Math.toRadians(yawDeg);
        double rx = x * Math.cos(yawRad) - z * Math.sin(yawRad);
        double rz = x * Math.sin(yawRad) + z * Math.cos(yawRad);
        return new Vector3d(rx, 0.0, rz);
    }

    private void computeCenterOffset(ShipStructure structure) {
        boolean first = true;
        double minX = 0.0;
        double maxX = 0.0;
        double minZ = 0.0;
        double maxZ = 0.0;

        for (ShipBlock b : structure.getBlocks()) {
            if (first) {
                minX = maxX = b.dx;
                minZ = maxZ = b.dz;
                first = false;
                continue;
            }

            minX = Math.min(minX, b.dx);
            maxX = Math.max(maxX, b.dx);
            minZ = Math.min(minZ, b.dz);
            maxZ = Math.max(maxZ, b.dz);
        }

        centerOffset.setX((minX + maxX) * 0.5);
        centerOffset.setY(0.0);
        centerOffset.setZ((minZ + maxZ) * 0.5);
    }
}
