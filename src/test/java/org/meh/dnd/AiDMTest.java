package org.meh.dnd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiDMTest
{
    @Test
    void parse_npcs_and_places_with_hostiles() {
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
                * Village Square
                * Herbalist's Hut
                * Darkwood Forest
                * Abandoned Bandit Camp""";

        ExploreOutput exploreOutput = new ExploreOutput(
                "village of Eldergrove",
                "As you step into the quaint village of Eldergrove, the air is thick with the scent of blooming wildflowers and the distant sound of laughter. Villagers bustle about, tending to their daily tasks, while curious eyes glance your way, wondering who you are and what brings you to their peaceful hamlet. However, there’s an undercurrent of tension in the air, as rumors swirl of a dark presence lurking in the nearby woods. You sense that there are stories waiting to be uncovered, and potential allies or adversaries to encounter.",
                List.of(
                        new Attack("Bandit", NpcType.WARRIOR),
                        new Attack("Dire Wolf", NpcType.BEAST))
        );
        assertEquals(exploreOutput, AiDM.parseExploreOutput(response, "village of Eldergrove"));
    }

    @Test
    void parse_npcs_and_places_no_hostile() {
        String response = """
                As you step into the quaint village of Eldergrove, the air is thick with the scent of blooming wildflowers and the distant sound of laughter. Villagers bustle about, tending to their daily tasks, while curious eyes glance your way, wondering who you are and what brings you to their peaceful hamlet. However, there’s an undercurrent of tension in the air, as rumors swirl of a dark presence lurking in the nearby woods. You sense that there are stories waiting to be uncovered, and potential allies or adversaries to encounter.

                <new line>
                *** NPCs ***
                <new line>
                * friendly magic Villager
                * friendly warrior Bandit
                * friendly magic Herbalist
                * friendly beast Dire Wolf

                <new line>
                *** PLACES ***
                <new line>
                * Village Square
                * Herbalist's Hut
                * Darkwood Forest
                * Abandoned Bandit Camp""";

        ExploreOutput exploreOutput = new ExploreOutput(
                "village of Eldergrove",
                "As you step into the quaint village of Eldergrove, the air is thick with the scent of blooming wildflowers and the distant sound of laughter. Villagers bustle about, tending to their daily tasks, while curious eyes glance your way, wondering who you are and what brings you to their peaceful hamlet. However, there’s an undercurrent of tension in the air, as rumors swirl of a dark presence lurking in the nearby woods. You sense that there are stories waiting to be uncovered, and potential allies or adversaries to encounter.",
                List.of(
                        new Attack("Villager", NpcType.MAGIC),
                        new Attack("Bandit", NpcType.WARRIOR),
                        new Attack("Herbalist", NpcType.MAGIC),
                        new Attack("Dire Wolf", NpcType.BEAST),
                        new Dialogue("Villager", NpcType.MAGIC),
                        new Dialogue("Bandit", NpcType.WARRIOR),
                        new Dialogue("Herbalist", NpcType.MAGIC),
                        new Dialogue("Dire Wolf", NpcType.BEAST),
                        new Explore("Village Square"),
                        new Explore("Herbalist's Hut"),
                        new Explore("Darkwood Forest"),
                        new Explore("Abandoned Bandit Camp"),
                        new Rest())
        );
        assertEquals(exploreOutput, AiDM.parseExploreOutput(response, "village of Eldergrove"));
    }
}