package com.michallves.ships;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.michallves.ships.events.HelmEventHandler;
import com.michallves.ships.piloting.PilotManager;
import com.michallves.ships.ship.ShipManager;
import com.michallves.ships.ship.systems.ShipPilotingSystem;
import com.michallves.ships.ship.systems.ShipPlayerCollisionSystem;
import com.michallves.ships.utils.ShipLogger;

public class Ships extends JavaPlugin {

    private static Ships INSTANCE;

    private PilotManager pilotManager;
    private ShipManager shipManager;

    private HelmEventHandler helmEventHandler;
    private ShipPilotingSystem shipPilotingSystem;
    private ShipPlayerCollisionSystem shipPlayerCollisionSystem;

    public Ships(JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    @Override
    protected void start() {
        ShipLogger.info("[SHIPS] Iniciando Plugin Ships (entity core)");

        try {
            pilotManager = new PilotManager();
            shipManager = new ShipManager();

            shipPilotingSystem = new ShipPilotingSystem(pilotManager, shipManager);
            getEntityStoreRegistry().registerSystem(shipPilotingSystem);
            ShipLogger.success("[SHIPS] ShipPilotingSystem registrado.");

            shipPlayerCollisionSystem = new ShipPlayerCollisionSystem(shipManager);
            getEntityStoreRegistry().registerSystem(shipPlayerCollisionSystem);
            ShipLogger.success("[SHIPS] ShipPlayerCollisionSystem registrado.");

            helmEventHandler = HelmEventHandler.getInstance(this, pilotManager, shipManager);
            helmEventHandler.registerHelmListener();

            ShipLogger.success("[SHIPS] Plugin pronto!");
        } catch (Exception e) {
            ShipLogger.error("[SHIPS] Erro critico ao inicializar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void shutdown() {
        ShipLogger.info("[SHIPS] Plugin desligando...");

        try {
            if (helmEventHandler != null) {
                helmEventHandler.shutdown();
                helmEventHandler = null;
            }
        } catch (Throwable t) {
            ShipLogger.error("[SHIPS] Erro ao desligar HelmEventHandler: " + t.getMessage());
            t.printStackTrace();
        }

        try {
            if (pilotManager != null) pilotManager.clear();
            if (shipManager != null) shipManager.clear();
        } catch (Throwable t) {
            ShipLogger.error("[SHIPS] Erro ao limpar managers: " + t.getMessage());
            t.printStackTrace();
        }

        shipPilotingSystem = null;
        shipPlayerCollisionSystem = null;
        pilotManager = null;
        shipManager = null;

        INSTANCE = null;
        ShipLogger.success("[SHIPS] Plugin desligado!");
    }

    public static Ships getInstance() {
        return INSTANCE;
    }
}
