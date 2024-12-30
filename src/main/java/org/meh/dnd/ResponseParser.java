package org.meh.dnd;

import java.util.Arrays;
import java.util.List;

public class ResponseParser
{
    public record ParsedExploreResponse(String description, List<NPC> npcs, List<Place> places, String storyLine) {}
    public enum DialogueActionType { SAY, END_DIALOGUE }
    public enum GoalType { KILL, EXPLORE, TALK }
    public record KillGoalModel(NpcType killNpcType, String killTarget) {}
    public record TalkGoalModel(NpcType talkNpcType, String talkTarget) {}
    public record ExploreGoalModel(String place) {}
    public record SayModel(String what) {}
    public record EndDialogueModel(
            String endDialoguePhrase,
            GoalType goalType,
            KillGoalModel killGoal,
            ExploreGoalModel exploreGoal,
            TalkGoalModel talkGoal
    ) {}
    public record DialogueActionModel(
            DialogueActionType actionType,
            SayModel say,
            EndDialogueModel endDialogue
    ) {}
    public record ParsedDialogueResponse(String phrase, List<DialogueActionModel> answers) {}
    public record NPC(String name, NpcType type, boolean hostile) {}
    public record Place(String name) {}

    public static List<QuestGoal> parseQuest(String content) {
        return Arrays.stream(content.split("\n"))
                .map(r -> r.substring(2).trim())
                .map(ResponseParser::parseQuestGoal)
                .toList();
    }

    private static QuestGoal parseQuestGoal(String content) {
        String actionStr = content.substring(0, content.indexOf(" ")).toLowerCase().trim();
        String info = content.substring(actionStr.length() + 1).trim();
        switch (actionStr) {
            case "kill" -> {
                String typeStr =
                        info.substring(0, info.indexOf(" ")).toLowerCase().trim();
                String target = info.substring(typeStr.length()).trim();
                return new KillGoal(
                        NpcType.valueOf(typeStr.toUpperCase()),
                        target,
                        false
                );
            }
            case "talk" -> {
                String typeStr =
                        info.substring(0, info.indexOf(" ")).toLowerCase().trim();
                String target = info.substring(typeStr.length()).trim();
                return new TalkGoal(
                        NpcType.valueOf(typeStr.toUpperCase()),
                        target,
                        false);
            }
            case "explore" -> {
                return new ExploreGoal(info.trim(), false);
            }
            default -> {
                return new ExploreGoal(info, false);
            }
        }
    }
}
