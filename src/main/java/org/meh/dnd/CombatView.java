package org.meh.dnd;

import java.util.List;

public record CombatView(
        boolean yourTurn,
        boolean playerWon,
        boolean enemyWon,
        boolean fightOver,
        CharacterView character,
        CharacterView enemy,
        String lastAction,
        int distance,
        List<ActionView> actions
)
{
}
