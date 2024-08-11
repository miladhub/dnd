package org.meh.dnd;

import java.util.Arrays;
import java.util.List;

public class ResponseParser
{
    public record ParsedResponse(String description, List<NPC> npcs, List<Place> places) {}
    public record NPC(String name, Race race, boolean hostile) {}
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

    private static NPC parseNpc(String raw) {
        // "hostile beast Dire Wolf"
        String hostileStr = raw.substring(0, raw.indexOf(" "));
        String rest = raw.substring(hostileStr.length() + 1);
        String raceStr = rest.substring(0, rest.indexOf(" "));
        String name = rest.substring(raceStr.length() + 1);
        return new NPC(name, Race.valueOf(raceStr.toUpperCase()), hostileStr.toLowerCase().contains("hostile"));
    }
}
