package org.meh.dnd;

public sealed interface QuestGoal
    permits KillGoal, ExploreGoal, TalkGoal
{
    boolean reached();
    String target();
}
