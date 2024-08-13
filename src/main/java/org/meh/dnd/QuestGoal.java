package org.meh.dnd;

public record QuestGoal(
        QuestGoalType type,
        String target,
        boolean reached
)
{
}
