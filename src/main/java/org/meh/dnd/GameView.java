package org.meh.dnd;

import java.util.List;

public record GameView(
        String charName,
        int level,
        String charClass,
        String description,
        List<ActionView> choices,
        String background,
        List<QuestGoalView> goals,
        String location,
        boolean questDone,
        boolean canLevelUp
)
{
}
