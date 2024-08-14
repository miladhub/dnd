package org.meh.dnd;

public record KillGoal(
        NpcType type,
        String target,
        boolean reached
) implements QuestGoal
{
}
