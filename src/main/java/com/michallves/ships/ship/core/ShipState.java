package com.michallves.ships.ship.core;

import com.hypixel.hytale.math.vector.Vector3d;

public final class ShipState {

    private final Vector3d origin = new Vector3d();

    private float yawDeg;
    private float lastYawDeg;

    private float speed = 0.0f;
    private float yawVel = 0.0f;
    private double velX = 0.0;
    private double velZ = 0.0;

    private double lastMoveX = 0.0;
    private double lastMoveZ = 0.0;
    private float lastYawDelta = 0.0f;

    public ShipState(float yawDeg) {
        this.yawDeg = yawDeg;
        this.lastYawDeg = yawDeg;
    }

    public Vector3d getOrigin() {
        return origin;
    }

    public float getYawDeg() {
        return yawDeg;
    }

    public void setYawDeg(float yawDeg) {
        this.yawDeg = yawDeg;
    }

    public float getLastYawDeg() {
        return lastYawDeg;
    }

    public void setLastYawDeg(float lastYawDeg) {
        this.lastYawDeg = lastYawDeg;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getYawVel() {
        return yawVel;
    }

    public void setYawVel(float yawVel) {
        this.yawVel = yawVel;
    }

    public double getVelX() {
        return velX;
    }

    public void setVelX(double velX) {
        this.velX = velX;
    }

    public double getVelZ() {
        return velZ;
    }

    public void setVelZ(double velZ) {
        this.velZ = velZ;
    }

    public double getLastMoveX() {
        return lastMoveX;
    }

    public void setLastMoveX(double lastMoveX) {
        this.lastMoveX = lastMoveX;
    }

    public double getLastMoveZ() {
        return lastMoveZ;
    }

    public void setLastMoveZ(double lastMoveZ) {
        this.lastMoveZ = lastMoveZ;
    }

    public float getLastYawDelta() {
        return lastYawDelta;
    }

    public void setLastYawDelta(float lastYawDelta) {
        this.lastYawDelta = lastYawDelta;
    }
}
