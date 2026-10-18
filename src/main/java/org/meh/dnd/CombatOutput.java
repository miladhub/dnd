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
        List<AvailableAction> availableActions,
        List<Combatant> combatants,
        String activeCombatant
)
        implements PlayerOutput
{
    public CombatOutput(boolean playerTurn, AvailableActions playerAvailableActions,
                        AvailableActions opponentAvailableActions, GameChar opponent,
                        List<String> log, boolean playerWon, boolean enemyWon,
                        int distance, List<AvailableAction> availableActions) {
        this(playerTurn, playerAvailableActions, opponentAvailableActions, opponent,
                log, playerWon, enemyWon, distance, availableActions, List.of(), "player");
    }
}
