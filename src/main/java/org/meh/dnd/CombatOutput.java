package org.meh.dnd;

public record CombatOutput(
        boolean playerTurn,
        GameChar opponent,
        String lastAction,
        boolean playerWon,
        boolean enemyWon,
        int distance
)
        implements PlayerOutput
{
}
