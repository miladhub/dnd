package org.meh.dnd;

public record CombatOutput(
        boolean playerTurn,
        GameChar opponent,
        String lastAction
)
        implements PlayerOutput
{
}
