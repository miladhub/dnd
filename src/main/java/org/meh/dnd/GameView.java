package org.meh.dnd;

import java.util.List;

public record GameView(
        String description,
        List<ActionView> choices,
        String background,
        List<QuestGoalView> goals,
        String location,
        boolean questDone
)
{
}
