package org.meh.dnd;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class RestServerTest
{
    @Test
    void testHelloEndpoint() {
        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .body(is("Hello from Quarkus REST"));
    }

    @Test
    void creating_a_quest_without_a_character_redirects_to_character_creation() {
        given()
                .formParam("background", "")
                .formParam("place", "Dark Forest")
                .when().post("/quest")
                .then()
                .statusCode(200)
                .header("HX-Redirect", is("/create.html"));
    }

}
