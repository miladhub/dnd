package org.meh.dnd;

import java.util.List;

public record GameSave(
        GameMode mode,
        GameChar playerChar,
        String background,
        String place,
        List<String> diary,
        List<QuestGoalSave> quest
)
{
}
