package org.meh.dnd;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.meh.dnd.NpcType.*;

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
    void parse_end_dialogue()
    throws Exception {
        String json = """
                {
                  "phrase": "You steel yourself for the confrontation, aware of the risks. Gathering more information about the Sorcerer could provide an advantage in this battle.",
                  "answers": [
                    {
                      "actionType": "SAY",
                      "say": {
                        "what": "Let's scout the area and see if we can find any clues about the Sorcerer's whereabouts or weaknesses."
                      }
                    },
                    {
                      "actionType": "END_DIALOGUE",
                      "endDialoguePhrase": "To better prepare for the confrontation, it might be wise to talk to the Knight Commander. They may have crucial information about the Sorcerer’s past and strategies to defeat him.",
                      "goalType": "TALK",
                      "talkGoal": {
                        "talkNpcType": "WARRIOR",
                        "talkTarget": "Knight Commander"
                      }
                    }
                  ]
                }
                """;

        String j2 = """
                {
                  "phrase": "Gathering information about the Sorcerer is crucial to ensure a successful confrontation.",
                  "answers": [
                    {
                      "actionType": "SAY",
                      "say": {
                        "what": "We should investigate the Sorcerer's lair and gather intel on his weaknesses."
                      }
                    },
                    {
                      "actionType": "END_DIALOGUE",
                      "endDialogue": {
                        "endDialoguePhrase": "Exploring the Forbidden Forest might reveal secrets about the Sorcerer's power and his plans.",
                        "goalType": "EXPLORE",
                        "exploreGoal": {
                          "place": "Forbidden Forest"
                        }
                      }
                    }
                  ]
                }
                """;

        try (Jsonb jsonb = JsonbBuilder.create()) {
            ResponseParser.ParsedDialogueResponse r = jsonb.fromJson(json,
                    ResponseParser.ParsedDialogueResponse.class);
            assertNotNull(r);
        }
    }

    @Test
    void end_dialogue_explore()
    throws Exception {
        String json = """
                {
                  "endDialoguePhrase": "Exploring the Forbidden Forest might reveal secrets about the Sorcerer's power and his plans.",
                  "goalType": "EXPLORE",
                  "exploreGoal": {
                    "place": "Forbidden Forest"
                  }
                }
                """;
        try (Jsonb jsonb = JsonbBuilder.create()) {
            ResponseParser.EndDialogueModel r = jsonb.fromJson(json,
                    ResponseParser.EndDialogueModel.class);
            assertNotNull(r);
        }
    }

    @Test
    void end_dialogue_talk()
    throws Exception {
        String json = """
                {
                  "endDialoguePhrase": "Exploring the Forbidden Forest might reveal secrets about the Sorcerer's power and his plans.",
                  "goalType": "EXPLORE",
                  "exploreGoal": {
                    "place": "Forbidden Forest"
                  }
                }
                """;
        try (Jsonb jsonb = JsonbBuilder.create()) {
            ResponseParser.EndDialogueModel r = jsonb.fromJson(json,
                    ResponseParser.EndDialogueModel.class);
            assertNotNull(r);
        }
    }
}