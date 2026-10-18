package org.meh.dnd;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class RestServerTest
{
    @Test
    @Order(1)
    void testHelloEndpoint() {
        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .body(is("Hello from Quarkus REST"));
    }

    @Test
    @Order(2)
    void creating_a_quest_without_a_character_redirects_to_character_creation() {
        given()
                .formParam("background", "")
                .formParam("place", "Dark Forest")
                .when().post("/quest")
                .then()
                .statusCode(200)
                .header("HX-Redirect", is("/create.html"));
    }

    @Test
    @Order(3)
    void api_character_creation_returns_a_renderer_neutral_state() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "name":"Godot Hero",
                          "class":"fighter",
                          "place":"The forest",
                          "background":"A shared adventure",
                          "maxHp":12,
                          "armorClass":15,
                          "strength":16
                        }
                        """)
                .when().post("/api/v1/character")
                .then()
                .statusCode(200)
                .body("player.name", is("Godot Hero"))
                .body("player.charClass", is("fighter"))
                .body("place", is("The forest"));
    }

}
