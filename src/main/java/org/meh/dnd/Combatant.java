package org.meh.dnd;

public record Combatant(
        String id,
        GameChar character,
        boolean ally,
        int initiative,
        int position,
        AvailableActions actions
) {
    public Combatant withCharacter(GameChar character) {
        return new Combatant(id, character, ally, initiative, position, actions);
    }

    public Combatant withPosition(int position) {
        return new Combatant(id, character, ally, initiative, position, actions);
    }

    public Combatant withActions(AvailableActions actions) {
        return new Combatant(id, character, ally, initiative, position, actions);
    }
}
