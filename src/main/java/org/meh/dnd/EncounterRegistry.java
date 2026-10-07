package org.meh.dnd;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the encounter attached to an action while actions travel through the channels. */
final class EncounterRegistry {
    private static final Map<String, List<AiEntities.NPC>> ENCOUNTERS = new ConcurrentHashMap<>();

    private EncounterRegistry() {}

    static void register(String target, List<AiEntities.NPC> encounter) {
        ENCOUNTERS.put(target, List.copyOf(encounter));
    }

    static List<AiEntities.NPC> get(String target) {
        return ENCOUNTERS.getOrDefault(target, List.of());
    }
}
