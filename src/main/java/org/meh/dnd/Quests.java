package org.meh.dnd;

import java.util.ArrayList;
import java.util.List;

import static org.meh.dnd.FightOutcome.ENEMY_WON;

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
                        if (g instanceof KillGoal kg && targetMatches(f.opponent().name(), g)) {
                            return new KillGoal(kg.type(), f.opponent().name(), true);
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
                    if (g instanceof TalkGoal tg && targetMatches(target, g)) {
                        return new TalkGoal(tg.type(), tg.target(), true);
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
                    if (g instanceof ExploreGoal && placeMatches(place, g)) {
                        return new ExploreGoal(g.target(), true);
                    } else {
                        return g;
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

    public static boolean matchesQuestGoal(
            Actions action,
            List<QuestGoal> goals
    ) {
        return goals.stream().anyMatch(g -> matchesQuestGoal(action, g));
    }

    private static boolean matchesQuestGoal(
            Actions action,
            QuestGoal g
    ) {
        return switch (g) {
            case KillGoal ignored ->
                    action instanceof Attack a && targetMatches(a.target(), g);
            case ExploreGoal ignored ->
                    action instanceof Explore e && targetMatches(e.place(), g);
            case TalkGoal ignored ->
                    action instanceof Dialogue d && targetMatches(d.target(), g);
        };
    }

    public static List<Actions> addQuestGoal(
            List<Actions> actions,
            List<QuestGoal> goals
    ) {
        if (actions.stream().anyMatch(a -> matchesQuestGoal(a, goals))) {
            return actions;
        } else {
            QuestGoal goal = goals.stream()
                    .filter(g -> !g.reached())
                    .filter(g -> actions.stream().noneMatch(a -> matchesQuestGoal(a, g)))
                    .findFirst().orElseThrow();
            Actions action = switch (goal) {
                case ExploreGoal eg -> new Explore(eg.target());
                case KillGoal kg -> new Attack(kg.target(), kg.type());
                case TalkGoal tg -> new Dialogue(tg.target(), tg.type());
            };
            List<Actions> newActions = new ArrayList<>(actions);
            newActions.add(action);
            return newActions;
        }
    }
}
