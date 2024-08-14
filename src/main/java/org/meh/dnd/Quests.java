package org.meh.dnd;

import java.util.List;

import static org.meh.dnd.FightOutcome.ENEMY_WON;
import static org.meh.dnd.QuestGoalType.*;
import static org.meh.dnd.QuestGoalType.EXPLORE;

public class Quests
{
    public static List<QuestGoal> updateQuestFromFight(
            List<QuestGoal> q,
            Fight f
    ) {
        if (f.outcome() == ENEMY_WON)
            return q;
        else
            return q.stream()
                    .map(g -> {
                        if (g.type() == KILL && targetMatches(f.opponent().name(), g)) {
                            return new QuestGoal(KILL, f.opponent().name(), true);
                        } else {
                            return g;
                        }
                    })
                    .toList();
    }

    public static List<QuestGoal> updateQuestFromTalking(
            List<QuestGoal> q,
            String target
    ) {
        return q.stream()
                .map(g -> {
                    if (g.type() == TALK && targetMatches(target, g)) {
                        return new QuestGoal(TALK, g.target(), true);
                    } else {
                        return g;
                    }
                })
                .toList();
    }

    private static boolean targetMatches(
            String target,
            QuestGoal g
    ) {
        return g.target().toLowerCase().trim().equals(target.toLowerCase().trim());
    }

    public static List<QuestGoal> updateQuestFromExploring(
            List<QuestGoal> q,
            String place
    ) {
        return q.stream()
                .map(g -> {
                    if (g.type() != EXPLORE || !placeMatches(place, g)) {
                        return g;
                    } else {
                        return new QuestGoal(EXPLORE, g.target(), true);
                    }
                })
                .toList();
    }

    private static boolean placeMatches(
            String place,
            QuestGoal g
    ) {
        String target = g.target().toLowerCase().trim().replaceAll("\\Qthe \\E", "");
        String cleanedPlace = place.toLowerCase().trim().replaceAll("\\Qthe \\E", "");
        return target.equals(cleanedPlace);
    }
}
