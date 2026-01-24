package com.michallves.ships.piloting;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PilotManager {

    private final ConcurrentHashMap<UUID, PilotSession> sessions = new ConcurrentHashMap<>();

    public boolean isPiloting(UUID playerUuid) {
        return sessions.containsKey(playerUuid);
    }

    public PilotSession get(UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public Collection<PilotSession> allSessions() {
        return sessions.values();
    }

    public void start(PilotSession session) {
        sessions.put(session.getPlayerUuid(), session);
    }

    public PilotSession stop(UUID playerUuid) {
        return sessions.remove(playerUuid);
    }

    public void clear() {
        sessions.clear();
    }
}
