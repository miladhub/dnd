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
        String location,
        List<CombatantView> combatants
)
{
    public boolean hasCombatants() {
        return !combatants.isEmpty();
    }

    public List<Integer> gridCells() {
        return java.util.stream.IntStream.range(0, GridPosition.COLUMNS * GridPosition.ROWS)
                .boxed().toList();
    }

    public CombatView(boolean yourTurn, boolean playerWon, boolean enemyWon,
                      boolean fightOver, AvailableActions availableActions,
                      CharacterView character, CharacterView enemy, String combatLog,
                      int distance, List<CombatActionView> actions, String location) {
        this(yourTurn, playerWon, enemyWon, fightOver, availableActions, character,
                enemy, combatLog, distance, actions, location, List.of());
    }
}
