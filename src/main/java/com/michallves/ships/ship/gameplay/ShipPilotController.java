package com.michallves.ships.ship.gameplay;

import com.hypixel.hytale.math.vector.Vector3d;
import com.michallves.ships.piloting.PilotSession;
import com.michallves.ships.utils.ShipMath;

public final class ShipPilotController {

    private static final int MAX_THROTTLE_STEP = 4;
    private static final float INPUT_DEADZONE = 0.25f;

    public PilotInput updateInput(PilotSession session, Vector3d clientVel, float headYaw) {
        double forwardInput = 0.0;
        double rightInput = 0.0;
        if (clientVel != null) {
            forwardInput = clientVel.getZ();
            rightInput = clientVel.getX();
        }

        boolean forwardPressed = forwardInput > INPUT_DEADZONE;
        boolean backwardPressed = forwardInput < -INPUT_DEADZONE;

        if (forwardPressed && !session.wasForwardPressedLastTick()) {
            session.setThrottleStep(Math.min(session.getThrottleStep() + 1, MAX_THROTTLE_STEP));
        }
        if (backwardPressed && !session.wasBackwardPressedLastTick()) {
            session.setThrottleStep(Math.max(session.getThrottleStep() - 1, -MAX_THROTTLE_STEP));
        }

        session.setLastForwardPressed(forwardPressed);
        session.setLastBackwardPressed(backwardPressed);
        session.setLastHeadYaw(headYaw);

        float throttle = session.getThrottleStep() / (float) MAX_THROTTLE_STEP;
        float rudder = (float) ShipMath.clamp(rightInput, -1.0, 1.0);

        return new PilotInput(throttle, rudder, headYaw);
    }

    public static final class PilotInput {
        public final float throttle;
        public final float rudder;
        public final float headYaw;

        private PilotInput(float throttle, float rudder, float headYaw) {
            this.throttle = throttle;
            this.rudder = rudder;
            this.headYaw = headYaw;
        }
    }
}
