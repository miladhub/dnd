package org.meh.dnd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiDMTest
{
    @Test
    void parse_output_explore_rest() {
        String response = """
                You find yourselves in a dimly lit cavern, the sound of dripping water echoing through the tunnels. As you cautiously make your way forward, you notice a faint glow up ahead. As you approach, you see a small underground river flowing through the middle of the cavern, its water shimmering with an ethereal blue light. The source of the light appears to be a cluster of glowing mushrooms growing along the riverbank. The air is thick with moisture, and the floor is slick with moss and algae.

                *** CHOICES ***

                1. Explore
                2. Rest""";
        ExploreOutput exploreOutput = new ExploreOutput(
                "You find yourselves in a dimly lit cavern, the sound of dripping water echoing through the tunnels. As you cautiously make your way forward, you notice a faint glow up ahead. As you approach, you see a small underground river flowing through the middle of the cavern, its water shimmering with an ethereal blue light. The source of the light appears to be a cluster of glowing mushrooms growing along the riverbank. The air is thick with moisture, and the floor is slick with moss and algae.",
                List.of(new Explore(""), new Rest())
        );
        assertEquals(exploreOutput, AiDM.parseExploreOutput(response));
    }

    @Test
    void parse_output_explore_some_place() {
        String response = """
                Some description.

                *** CHOICES ***

                1. Explore cavern
                2. Attack goblin
                3. Dialogue elf
                4. Rest""";
        ExploreOutput exploreOutput = new ExploreOutput(
                "Some description.",
                List.of(
                        new Explore("cavern"),
                        new Attack("goblin"),
                        new Dialogue("elf"),
                        new Rest())
        );
        assertEquals(exploreOutput, AiDM.parseExploreOutput(response));
    }

    @Test
    void parse_rest() {
        String response = """
                As you continue your exploration through the dense\
                 forest, the air is filled with the sounds of chirping birds and rustling leaves. Sunlight filters through the canopy, casting playful shadows on the ground. Suddenly, you stumble upon a hidden glade, where a small, shimmering pond reflects the sky. The atmosphere is serene, and you notice colorful flowers blooming around the water's edge.\s

                However, the tranquility is disrupted by the distant sound of laughter and chatter. It seems like a group of travelers or perhaps a gathering of forest dwellers is nearby.\s

                <new line>
                *** CHOICES ***
                <new line>

                1. Explore the pond \s
                2. Explore the source of the laughter \s
                3. Rest \s""";

        ExploreOutput exploreOutput = new ExploreOutput(
                """
                        As you continue your exploration through the dense\
                         forest, the air is filled with the sounds of chirping birds and rustling leaves. Sunlight filters through the canopy, casting playful shadows on the ground. Suddenly, you stumble upon a hidden glade, where a small, shimmering pond reflects the sky. The atmosphere is serene, and you notice colorful flowers blooming around the water's edge.\s

                        However, the tranquility is disrupted by the distant sound of laughter and chatter. It seems like a group of travelers or perhaps a gathering of forest dwellers is nearby.""",
                List.of(
                        new Explore("the pond"),
                        new Explore("the source of the laughter"),
                        new Rest())
        );
        assertEquals(exploreOutput, AiDM.parseExploreOutput(response));
    }
}