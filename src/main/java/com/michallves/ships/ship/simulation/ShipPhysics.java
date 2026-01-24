package com.michallves.ships.ship.simulation;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;
import com.michallves.ships.ship.collision.ShipCollision;
import com.michallves.ships.ship.core.ShipGeometry;
import com.michallves.ships.ship.core.ShipState;
import com.michallves.ships.utils.ShipMath;

public final class ShipPhysics {

    private static final float MAX_SPEED = 4.0f;
    private static final float ACCEL = 5.0f;
    private static final float DRAG_BASE = 0.985f;
    private static final float MIN_STEER_SPEED = 0.15f;
    private static final float COLLISION_BOUNCE = 0.1f;
    private static final float COLLISION_DAMPING = 0.35f;
    private static final float COLLISION_YAW_DAMPING = 0.6f;

    private final ShipState state;

    public ShipPhysics(ShipState state) {
        this.state = state;
    }

    public float getYawDeg() {
        return state.getYawDeg();
    }

    public float getSpeed() {
        return state.getSpeed();
    }

    public float getMaxSpeed() {
        return MAX_SPEED;
    }

    public double getLastMoveX() {
        return state.getLastMoveX();
    }

    public double getLastMoveZ() {
        return state.getLastMoveZ();
    }

    public float getLastYawDelta() {
        return state.getLastYawDelta();
    }

    public void applyControls(float dt, float throttle, float rudder, float wheel01) {
        float yawDeg = state.getYawDeg();
        double velX = state.getVelX();
        double velZ = state.getVelZ();
        float yawVel = state.getYawVel();

        double yawRad = Math.toRadians(yawDeg);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        float drag = (float) Math.pow(DRAG_BASE, Math.max(1.0, dt * 60.0));

        double forwardSpeed = velX * fx + velZ * fz;
        float targetSpeed = throttle * MAX_SPEED;
        double speedError = targetSpeed - forwardSpeed;
        double accelStep = ShipMath.clamp(speedError * 2.0, -ACCEL, ACCEL);

        velX += fx * accelStep * dt;
        velZ += fz * accelStep * dt;

        velX *= drag;
        velZ *= drag;

        double speedMag = Math.sqrt(velX * velX + velZ * velZ);
        if (speedMag > MAX_SPEED) {
            double scale = MAX_SPEED / speedMag;
            velX *= scale;
            velZ *= scale;
            speedMag = MAX_SPEED;
        }

        forwardSpeed = velX * fx + velZ * fz;
        float speed = (float) forwardSpeed;

        float speedAbs = Math.abs(speed);
        float turnFactor = (float) ShipMath.clamp(speedAbs / MAX_SPEED, 0.0, 1.0);
        if (speedAbs < MIN_STEER_SPEED) {
            turnFactor = 0.0f;
        }
        float turnSign = forwardSpeed < 0.0 ? -1.0f : 1.0f;

        float desiredYawVel = (rudder * 80.0f + wheel01 * 120.0f) * turnFactor * turnSign;
        float yawAccel = 6.0f;
        yawVel += (desiredYawVel - yawVel) * ShipMath.clamp(yawAccel * dt, 0f, 1f);
        yawVel *= (float) Math.pow(0.92, Math.max(1.0, dt * 60.0));

        yawDeg = ShipMath.wrapDegrees(yawDeg + yawVel * dt);

        state.setVelX(velX);
        state.setVelZ(velZ);
        state.setYawVel(yawVel);
        state.setYawDeg(yawDeg);
        state.setSpeed(speed);
    }

    public void tickMovement(World world, ShipGeometry geometry, ShipCollision collision, float dt) {
        Vector3d origin = geometry.getOrigin();

        float yawDeg = state.getYawDeg();
        float lastYawDeg = state.getLastYawDeg();
        double velX = state.getVelX();
        double velZ = state.getVelZ();
        float yawVel = state.getYawVel();

        double prevX = origin.getX();
        double prevZ = origin.getZ();
        float prevYaw = yawDeg;

        double dx = velX * dt;
        double dz = velZ * dt;

        boolean hasRotation = Math.abs(ShipMath.wrapDegrees(yawDeg - lastYawDeg)) > 0.01f;
        boolean hasTranslation = Math.abs(dx) > 1.0e-5 || Math.abs(dz) > 1.0e-5;

        if (hasRotation && collision.collidesAt(world, origin.getX(), origin.getY(), origin.getZ(), yawDeg)) {
            yawDeg = lastYawDeg;
            yawVel = 0.0f;
        }

        if (!hasTranslation) {
            lastYawDeg = yawDeg;
        } else if (!collision.collidesAt(world, origin.getX() + dx, origin.getY(), origin.getZ() + dz, yawDeg)) {
            origin.setX(origin.getX() + dx);
            origin.setZ(origin.getZ() + dz);
        } else {
            boolean moveX = !collision.collidesAt(world, origin.getX() + dx, origin.getY(), origin.getZ(), yawDeg);
            boolean moveZ = !collision.collidesAt(world, origin.getX(), origin.getY(), origin.getZ() + dz, yawDeg);

            double newX = origin.getX();
            double newZ = origin.getZ();

            if (moveX) {
                newX += dx;
            } else {
                velX = -velX * COLLISION_BOUNCE;
            }
            if (moveZ) {
                newZ += dz;
            } else {
                velZ = -velZ * COLLISION_BOUNCE;
            }

            origin.setX(newX);
            origin.setZ(newZ);

            velX *= COLLISION_DAMPING;
            velZ *= COLLISION_DAMPING;
            yawVel *= COLLISION_YAW_DAMPING;

            if (collision.collidesAt(world, origin.getX(), origin.getY(), origin.getZ(), yawDeg)) {
                origin.setX(prevX);
                origin.setZ(prevZ);
                velX = 0.0;
                velZ = 0.0;
                yawVel = 0.0f;
            }
        }

        double yawRad = Math.toRadians(yawDeg);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        float speed = (float) (velX * fx + velZ * fz);

        state.setYawDeg(yawDeg);
        state.setLastYawDeg(lastYawDeg);
        state.setVelX(velX);
        state.setVelZ(velZ);
        state.setYawVel(yawVel);
        state.setSpeed(speed);
        state.setLastMoveX(origin.getX() - prevX);
        state.setLastMoveZ(origin.getZ() - prevZ);
        state.setLastYawDelta(ShipMath.wrapDegrees(yawDeg - prevYaw));
    }

    public void resetForDocking(float yawDeg) {
        state.setYawDeg(yawDeg);
        state.setLastYawDeg(yawDeg);
        state.setSpeed(0.0f);
        state.setYawVel(0.0f);
        state.setVelX(0.0);
        state.setVelZ(0.0);
        state.setLastMoveX(0.0);
        state.setLastMoveZ(0.0);
        state.setLastYawDelta(0.0f);
    }
}
