package org.meh.dnd;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiEntitiesTest
{
    @Test
    void parse_end_dialogue()
    throws Exception {
        String json = """
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
                    },
                    {
                      "actionType": "END_DIALOGUE",
                      "endDialogue": {
                        "endDialoguePhrase": "To better prepare for the confrontation, it might be wise to talk to the Knight Commander. They may have crucial information about the Sorcerer’s past and strategies to defeat him.",
                        "goalType": "TALK",
                        "talkGoal": {
                          "talkNpcType": "WARRIOR",
                          "talkTarget": "Knight Commander"
                        }
                      }
                    }
                  ]
                }
                """;

        try (Jsonb jsonb = JsonbBuilder.create()) {
            AiEntities.ParsedDialogueResponse r = jsonb.fromJson(json,
                    AiEntities.ParsedDialogueResponse.class);
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
            AiEntities.EndDialogueModel r = jsonb.fromJson(json,
                    AiEntities.EndDialogueModel.class);
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
            AiEntities.EndDialogueModel r = jsonb.fromJson(json,
                    AiEntities.EndDialogueModel.class);
            assertNotNull(r);
        }
    }
}