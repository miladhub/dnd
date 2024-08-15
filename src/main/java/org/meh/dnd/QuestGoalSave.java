package org.meh.dnd;

public record QuestGoalSave(
        String goalType,
        String type,
        String target,
        boolean reached
)
{
}
