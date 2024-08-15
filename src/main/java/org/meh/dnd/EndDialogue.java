package org.meh.dnd;

public record EndDialogue(
        String phrase,
        QuestGoal goal
) implements Actions
{
}
