package com.michallves.ships.ship.gameplay;

import com.michallves.ships.piloting.PilotManager;
import com.michallves.ships.piloting.PilotSession;
import com.michallves.ships.ship.ShipController;
import com.michallves.ships.ship.ShipManager;
import com.michallves.ships.utils.ShipLogger;

import java.util.UUID;

public final class ShipPilotDockingHandler {

    public void requestDockingIfNeeded(PilotSession session, ShipController ship) {
        if (session.isDockingRequested()) {
            ship.requestDocking();
        }
    }

    public boolean handleDockingOutcome(PilotSession session,
                                        ShipController ship,
                                        PilotManager pilotManager,
                                        ShipManager shipManager,
                                        UUID uuid) {
        if (!session.isDockingRequested()) return false;

        if (!ship.isActiveEntity()) {
            pilotManager.stop(uuid);
            shipManager.releasePilot(session.getShipId(), uuid);
            ShipLogger.success("[SHIPS] Dock concluido (piloto solto). UUID=" + uuid + " shipId=" + session.getShipId());
            return true;
        }

        session.clearDockingRequest();
        ShipLogger.warn("[SHIPS] Docking falhou (colisao) -> continuando pilotagem. UUID=" + uuid + " shipId=" + session.getShipId());
        return false;
    }
}
