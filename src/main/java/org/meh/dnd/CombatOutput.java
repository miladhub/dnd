package org.meh.dnd;

import java.util.List;

public record CombatOutput(
        boolean playerTurn,
        AvailableActions playerAvailableActions,
        AvailableActions opponentAvailableActions,
        GameChar opponent,
        List<String> log,
        boolean playerWon,
        boolean enemyWon,
        int distance,
        List<AvailableAction> availableActions
)
        implements PlayerOutput
{
}
