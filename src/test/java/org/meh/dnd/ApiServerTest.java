package org.meh.dnd;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ApiServerTest {
    @Test
    void rest_adapter_creates_a_renderer_neutral_state() {
        given().contentType("application/json").body("""
                {"name":"REST Hero","class":"fighter","place":"The forest","background":"An adventure","maxHp":12,"armorClass":15,"strength":16}
                """).when().post("/api/v1/character")
                .then().statusCode(200).body("player.name", is("REST Hero"));

        given().when().get("/api/v1/state").then().statusCode(200)
                .body("mode", is("EXPLORING"))
                .body("player.name", is("REST Hero"))
                .body("player.charClass", is("fighter"));
    }

    @Test
    void rest_adapter_rejects_invalid_commands() {
        given().contentType("application/json").body("{\"type\":\"unknown\"}")
                .when().post("/api/v1/commands")
                .then().statusCode(400).body("accepted", is(false));
    }

    @Test
    void rest_adapter_creates_a_quest_for_an_existing_character() {
        createCharacter("Quest REST Hero");

        given().contentType("application/json")
                .body("{\"background\":\"A haunted past\",\"place\":\"The ruins\"}")
                .when().post("/api/v1/quest")
                .then().statusCode(200)
                .body("place", is("The ruins"))
                .body("background", is("A haunted past"));
    }

    @Test
    void rest_adapter_keeps_the_character_level_when_xp_is_insufficient() {
        createCharacter("Level REST Hero");

        given().when().post("/api/v1/level-up")
                .then().statusCode(200).body("player.level", is(1));
    }

    @Test
    void rest_adapter_saves_and_loads_json_game_state() {
        createCharacter("Save REST Hero");
        String save = given().when().get("/api/v1/save")
                .then().statusCode(200)
                .header("Content-Disposition", containsString("game.json"))
                .extract().asString();
        assertTrue(save.contains("Save REST Hero"));

        given().contentType("application/json").body(save)
                .when().post("/api/v1/load")
                .then().statusCode(200).body("player.name", is("Save REST Hero"));
    }

    private static void createCharacter(String name) {
        given().contentType("application/json").body("""
                {"name":"%s","class":"fighter","place":"The forest","background":"An adventure","maxHp":12,"armorClass":15,"strength":16}
                """.formatted(name)).when().post("/api/v1/character")
                .then().statusCode(200);
    }
}
