package org.meh.dnd;

public record Combatant(
        String id,
        GameChar character,
        boolean ally,
        int initiative,
        int position,
        AvailableActions actions,
        GridPosition gridPosition
) {
    public Combatant(String id, GameChar character, boolean ally, int initiative,
                     int position, AvailableActions actions) {
        this(id, character, ally, initiative, position, actions,
                GridPosition.fromLegacyDistance(position));
    }

    public Combatant(String id, GameChar character, boolean ally, int initiative,
                     GridPosition gridPosition, AvailableActions actions) {
        this(id, character, ally, gridPosition.column() * GridPosition.CELL_FEET,
                gridPosition.column() * GridPosition.CELL_FEET, actions, gridPosition);
    }

    public Combatant withCharacter(GameChar character) {
        return new Combatant(id, character, ally, initiative, position, actions, gridPosition);
    }

    public Combatant withPosition(int position) {
        return new Combatant(id, character, ally, initiative, position, actions,
                GridPosition.fromLegacyDistance(position));
    }

    public Combatant withGridPosition(GridPosition position) {
        return new Combatant(id, character, ally, initiative,
                position.column() * GridPosition.CELL_FEET, actions, position);
    }

    public Combatant withActions(AvailableActions actions) {
        return new Combatant(id, character, ally, initiative, position, actions, gridPosition);
    }
}
