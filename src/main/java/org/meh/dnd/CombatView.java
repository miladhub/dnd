package org.meh.dnd;

import java.util.List;

public record CombatView(
        boolean yourTurn,
        String character,
        String enemy,
        List<String> events,
        List<ActionView> actions
)
{
}
