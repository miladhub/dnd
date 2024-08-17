package org.meh.dnd;

import java.util.List;

public record Fight(
        boolean playerTurn,
        GameChar opponent,
        List<String> log,
        int distance,
        FightOutcome outcome,
        AvailableActions playerActions,
        AvailableActions opponentActions,
        int xp
) implements CombatStatus {}
