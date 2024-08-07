package org.meh.dnd;

public record Fight(
        boolean playerTurn,
        GameChar opponent,
        String lastAction,
        FightStatus outcome
)
        implements CombatStatus
{
}
