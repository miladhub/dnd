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
        int xp,
        List<DelayedEffect> delayedEffects,
        List<Combatant> combatants,
        String activeCombatant
) implements CombatStatus {
    public Fight(
            boolean playerTurn,
            GameChar opponent,
            List<String> log,
            int distance,
            FightOutcome outcome,
            AvailableActions playerActions,
            AvailableActions opponentActions,
            int xp,
            List<DelayedEffect> delayedEffects
    ) {
        this(playerTurn, opponent, log, distance, outcome, playerActions,
                opponentActions, xp, delayedEffects, List.of(), "player");
    }

    public boolean isMultiCombat() {
        return !combatants.isEmpty();
    }
}
