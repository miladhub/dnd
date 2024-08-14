package org.meh.dnd;

public record ExploreGoal(
        String target,
        boolean reached
) implements QuestGoal
{
}
