package org.meh.dnd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.meh.dnd.NpcType.*;
import static org.meh.dnd.ResponseParser.*;

class ResponseParserTest
{
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
                        new KillGoal(BEAST, "The Red Dragon", false),
                        new TalkGoal(MAGIC, "Elf Sage", false),
                        new KillGoal(WARRIOR, "Orc Chief", false)
                ),
                goals
        );
    }

    @Test
    void parse_dialogue_with_endings() {
        String response = """
                "I can create an illusion of a fire in the forest to draw them away from the camp. While they're distracted, we can sneak in."

                *** ANSWERS ***

                * Hello there!
                * Farewell, and good luck. => explore Dark Dungeon
                * Good luck defeating the dragon! => kill beast The Red Dragon
                * Tell my old Elf friend that I sent you. => talk magic Elf Sage""";

        ParsedDialogueResponse output = new ParsedDialogueResponse(
                "\"I can create an illusion of a fire in the forest to draw them away from the camp. While they're distracted, we can sneak in.\"",
                List.of(
                        new Say("Hello there!"),
                        new EndDialogue("Farewell, and good luck.",
                                new ExploreGoal("Dark Dungeon", false)),
                        new EndDialogue("Good luck defeating the dragon!",
                                new KillGoal(BEAST, "The Red Dragon", false)),
                        new EndDialogue("Tell my old Elf friend that I sent you.",
                                new TalkGoal(MAGIC, "Elf Sage", false))
                )
        );
        assertEquals(output, parseDialogueResponse(response));
    }

    @Test
    void parse_dialogue_no_endings() {
        String response = """
                "I can create an illusion of a fire in the forest to draw them away from the camp. While they're distracted, we can sneak in."

                *** ANSWERS ***

                * "What kind of illusion can you create?"
                * Where are you going?""";

        ParsedDialogueResponse output = new ParsedDialogueResponse(
                "\"I can create an illusion of a fire in the forest to draw them away from the camp. While they're distracted, we can sneak in.\"",
                List.of(
                        new Say("What kind of illusion can you create?"),
                        new Say("Where are you going?")
                )
        );
        assertEquals(output, parseDialogueResponse(response));
    }
}