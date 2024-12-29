package org.meh.dnd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.meh.dnd.ViewEncoderDecoder.cleanString;

public class ResponseParser
{
    public record ParsedExploreResponse(String description, List<NPC> npcs, List<Place> places, String storyLine) {}
    public record ParsedDialogueResponse(String phrase, List<Actions> answers) {}
    public record NPC(String name, NpcType type, boolean hostile) {}
    public record Place(String name) {}

    public static ParsedDialogueResponse parseDialogueResponse(String content) {
        String[] split = content
                .replaceAll("\\Q<new line>\\E", "")
                .split("\\Q*** ANSWERS ***\\E");
        String phrase = split[0];
        String answersContent = split[1];
        List<Actions> answers = new ArrayList<>(Arrays.stream(answersContent.split("\n"))
                .filter(c -> !c.trim().isBlank())
                .map(c -> cleanString(c.substring(2)))
                .map(ResponseParser::parseDialogueAction)
                .toList());
        return new ParsedDialogueResponse(phrase.trim(), answers);
    }

    private static Actions parseDialogueAction(String content) {
        if (!content.contains("=>")) {
            return new Say(content);
        } else {
            String phraseStr = content.substring(0, content.indexOf("=>"));
            String goalStr = content.substring(phraseStr.length() + 2).trim();
            QuestGoal goal = parseQuestGoal(goalStr);
            return new EndDialogue(phraseStr.trim(), goal);
        }
    }

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
