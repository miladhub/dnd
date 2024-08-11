package org.meh.dnd;

import java.util.List;

public record Fight(
        boolean playerTurn,
        GameChar opponent,
        List<String> log,
        int distance,
        FightStatus outcome,
        AvailableActions playerActions,
        AvailableActions opponentActions
)
        implements CombatStatus
{
    public Fight withOpponentActions(AvailableActions opponentActions) {
        return new Fight(playerTurn, opponent, log, distance, outcome, playerActions, opponentActions);
    }
}
