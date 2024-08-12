package org.meh.dnd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.meh.dnd.ActionParser.cleanString;

public class ResponseParser
{
    public record ParsedResponse(String description, List<NPC> npcs, List<Place> places) {}

    public record NPC(String name, NpcType type, boolean hostile) {}
    public record Place(String name) {}
    public static ParsedResponse parseExploreResponse(String response) {
        String[] descrTail = response
                .replaceAll("\\Q<new line>\\E", "")
                .split("\\Q*** NPCs ***\\E");
        String[] npcsPlaces = descrTail[1]
                .split("\\Q*** PLACES ***\\E");
        String npcsText = npcsPlaces[0];
        String placesText = npcsPlaces[1];
        String description = descrTail[0].trim();
        List<NPC> npcs = Arrays.stream(npcsText.split("\n"))
                .filter(l -> !l.isBlank())
                .map(s -> s.substring(2))
                .map(ResponseParser::parseNpc)
                .toList();
        List<Place> places = Arrays.stream(placesText.split("\n"))
                .filter(l -> !l.isBlank())
                .map(s -> s.substring(2))
                .map(Place::new)
                .toList();
        return new ParsedResponse(
                description,
                npcs,
                places
        );
    }

    public static DialogueOutput parseDialogueOutput(
            String content,
            String target,
            NpcType type
    ) {
        String[] split = content
                .replaceAll("\\Q<new line>\\E", "")
                .split("\\Q*** CHOICES ***\\E");
        String phrase = split[0];
        String answersContent = split[1];
        List<Actions> answers = new ArrayList<>(Arrays.stream(answersContent.split("\n"))
                .filter(c -> !c.trim().isBlank())
                .map(c -> cleanString(c.substring(2)))
                .map(c -> (Actions) new Say(c))
                .toList());
        answers.add(new Attack(target, type));
        answers.add(new EndDialogue());
        return new DialogueOutput(target, phrase.trim(), answers);
    }

    private static NPC parseNpc(String raw) {
        // "hostile beast Dire Wolf"
        String hostileStr = raw.substring(0, raw.indexOf(" "));
        String rest = raw.substring(hostileStr.length() + 1);
        String raceStr = rest.substring(0, rest.indexOf(" "));
        String name = rest.substring(raceStr.length() + 1);
        return new NPC(name, NpcType.valueOf(raceStr.toUpperCase()), hostileStr.toLowerCase().contains("hostile"));
    }
}
