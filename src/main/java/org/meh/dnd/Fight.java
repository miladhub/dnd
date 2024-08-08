package org.meh.dnd;

public record Fight(
        boolean playerTurn,
        GameChar opponent,
        String lastAction,
        int distance,
        FightStatus outcome
)
        implements CombatStatus
{
}
