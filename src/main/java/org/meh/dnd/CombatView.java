package org.meh.dnd;

import java.util.List;

public record CombatView(
        boolean yourTurn,
        CharacterView character,
        CharacterView enemy,
        String lastAction,
        List<ActionView> actions
)
{
}
