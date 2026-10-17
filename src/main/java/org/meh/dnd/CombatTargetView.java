package org.meh.dnd;

import java.util.List;

public record CombatTargetView(String id, String name, String portraitUrl, int hp, int maxHp,
                               int distance, List<CombatActionView> actions) {
    public CombatTargetView(String id, String name) {
        this(id, name, "", 0, 0, 0, List.of());
    }

    public boolean hasActions() { return !actions.isEmpty(); }
}
