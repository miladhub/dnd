package org.meh.dnd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.meh.dnd.ResponseParser.*;

class ResponseParserTest
{
    @Test
    void parse_npcs_and_places() {
        String response = """
                As you step into the quaint village of Eldergrove, the air is thick with the scent of blooming wildflowers and the distant sound of laughter. Villagers bustle about, tending to their daily tasks, while curious eyes glance your way, wondering who you are and what brings you to their peaceful hamlet. However, there’s an undercurrent of tension in the air, as rumors swirl of a dark presence lurking in the nearby woods. You sense that there are stories waiting to be uncovered, and potential allies or adversaries to encounter.

                <new line>
                *** NPCs ***
                <new line>
                * friendly warrior Villager
                * hostile warrior Bandit
                * friendly magic Herbalist
                * hostile beast Dire Wolf

                <new line>
                *** PLACES ***
                <new line>
                * Village Square
                * Herbalist's Hut
                * Darkwood Forest
                * Abandoned Bandit Camp
                
                <new line>
                *** STORYLINE ***
                <new line>
                
                Something happened.""";

        ParsedExploreResponse ParsedExploreResponse = new ParsedExploreResponse(
                "As you step into the quaint village of Eldergrove, the air is thick with the scent of blooming wildflowers and the distant sound of laughter. Villagers bustle about, tending to their daily tasks, while curious eyes glance your way, wondering who you are and what brings you to their peaceful hamlet. However, there’s an undercurrent of tension in the air, as rumors swirl of a dark presence lurking in the nearby woods. You sense that there are stories waiting to be uncovered, and potential allies or adversaries to encounter.",
                List.of(
                        new NPC("Villager", NpcType.WARRIOR, false),
                        new NPC("Bandit", NpcType.WARRIOR, true),
                        new NPC("Herbalist", NpcType.MAGIC, false),
                        new NPC("Dire Wolf", NpcType.BEAST, true)
                ),
                List.of(
                        new Place("Village Square"),
                        new Place("Herbalist's Hut"),
                        new Place("Darkwood Forest"),
                        new Place("Abandoned Bandit Camp")
                ),
                "Something happened."
        );
        assertEquals(ParsedExploreResponse, parseExploreResponse(response));
    }

    @Test
    void parse_npcs_only() {
        String response = """
                As you step into the quaint village of Eldergrove, the air is thick with the scent of blooming wildflowers and the distant sound of laughter. Villagers bustle about, tending to their daily tasks, while curious eyes glance your way, wondering who you are and what brings you to their peaceful hamlet. However, there’s an undercurrent of tension in the air, as rumors swirl of a dark presence lurking in the nearby woods. You sense that there are stories waiting to be uncovered, and potential allies or adversaries to encounter.

                <new line>
                *** NPCs ***
                <new line>
                * friendly magic Villager
                * hostile warrior Bandit
                * friendly magic Herbalist
                * hostile beast Dire Wolf

                <new line>
                *** PLACES ***
                <new line>
                
                <new line>
                *** STORYLINE ***
                <new line>
                
                Something happened.
                """;

        ParsedExploreResponse ParsedExploreResponse = new ParsedExploreResponse(
                "As you step into the quaint village of Eldergrove, the air is thick with the scent of blooming wildflowers and the distant sound of laughter. Villagers bustle about, tending to their daily tasks, while curious eyes glance your way, wondering who you are and what brings you to their peaceful hamlet. However, there’s an undercurrent of tension in the air, as rumors swirl of a dark presence lurking in the nearby woods. You sense that there are stories waiting to be uncovered, and potential allies or adversaries to encounter.",
                List.of(
                        new NPC("Villager", NpcType.MAGIC, false),
                        new NPC("Bandit", NpcType.WARRIOR, true),
                        new NPC("Herbalist", NpcType.MAGIC, false),
                        new NPC("Dire Wolf", NpcType.BEAST, true)
                ),
                List.of(),
                "Something happened."
        );
        assertEquals(ParsedExploreResponse, parseExploreResponse(response));
    }

    @Test
    void parse_places_only() {
        String response = """
                As you step into the quaint village of Eldergrove, the air is thick with the scent of blooming wildflowers and the distant sound of laughter. Villagers bustle about, tending to their daily tasks, while curious eyes glance your way, wondering who you are and what brings you to their peaceful hamlet. However, there’s an undercurrent of tension in the air, as rumors swirl of a dark presence lurking in the nearby woods. You sense that there are stories waiting to be uncovered, and potential allies or adversaries to encounter.

                <new line>
                *** NPCs ***
                <new line>

                <new line>
                *** PLACES ***
                <new line>
                
                * Village Square
                * Herbalist's Hut
                * Darkwood Forest
                * Abandoned Bandit Camp
                
                <new line>
                *** STORYLINE ***
                <new line>
                
                Something happened.
                """;

        ParsedExploreResponse ParsedExploreResponse = new ParsedExploreResponse(
                "As you step into the quaint village of Eldergrove, the air is thick with the scent of blooming wildflowers and the distant sound of laughter. Villagers bustle about, tending to their daily tasks, while curious eyes glance your way, wondering who you are and what brings you to their peaceful hamlet. However, there’s an undercurrent of tension in the air, as rumors swirl of a dark presence lurking in the nearby woods. You sense that there are stories waiting to be uncovered, and potential allies or adversaries to encounter.",
                List.of(
                ),
                List.of(
                        new Place("Village Square"),
                        new Place("Herbalist's Hut"),
                        new Place("Darkwood Forest"),
                        new Place("Abandoned Bandit Camp")
                ),
                "Something happened."
        );
        assertEquals(ParsedExploreResponse, parseExploreResponse(response));
    }

    @Test
    void parse_quest() {
        String response = """
                * explore Dark Dungeon
                * kill beast The Red Dragon
                * talk magic Elf Sage
                * kill warrior Orc Chief
                """;
        List<QuestGoal> goals = ResponseParser.parseQuest(response);
        assertEquals(
                List.of(
                        new ExploreGoal("Dark Dungeon", false),
                        new KillGoal(NpcType.BEAST, "The Red Dragon", false),
                        new TalkGoal(NpcType.MAGIC, "Elf Sage", false),
                        new KillGoal(NpcType.WARRIOR, "Orc Chief", false)
                ),
                goals
        );
    }
}