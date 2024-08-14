package org.meh.dnd;

public record TalkGoal(
        NpcType type,
        String target,
        boolean reached
) implements QuestGoal
{
}
