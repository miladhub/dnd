package org.meh.dnd;

import java.util.List;

public record CombatView(
        boolean yourTurn,
        boolean playerWon,
        boolean enemyWon,
        boolean fightOver,
        AvailableActions availableActions,
        CharacterView character,
        CharacterView enemy,
        String combatLog,
        int distance,
        List<CombatActionView> actions,
        String location
)
{
}
