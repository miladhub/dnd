package org.meh.dnd;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiMapperTest {
    @Test
    void maps_an_exploration_state_without_exposing_domain_polymorphism() throws Exception {
        GameChar character = new GameChar(
                "Aria", 1, CharClass.FIGHTER, 10, 10, 14, 0, 300,
                new Stats(16, 12, 14, 10, 10, 10), DndCombat.FIGHTER_WEAPONS,
                List.of(), new AvailableActions(1, 1, 30), DndCombat.spellSlots(CharClass.FIGHTER, 1));
        Game game = GameSaveLoad.createGameFrom("A quest", "The forest", character);

        ApiModels.State state = ApiMapper.state(game);
        String json = new ObjectMapper().writeValueAsString(state);

        assertEquals("EXPLORING", state.mode());
        assertEquals("Aria", state.player().name());
        assertNotNull(state.narrative());
        assertEquals("explore", state.narrative().type());
        assertEquals("Aria", new ObjectMapper().readTree(json).path("player").path("name").asText());
    }
}
