package com.ferraz.jarcomparator;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class JarComparatorResourceTest {

    @Test
    void formPageReturns200() {
        given()
            .when().get("/")
            .then()
                .statusCode(200)
                .contentType(containsString("text/html"));
    }

    @Test
    void compareRejectsSameVersion() {
        given()
            .formParam("oldVersion", "com.example:lib:1.0.0")
            .formParam("newVersion", "com.example:lib:1.0.0")
            .when().post("/compare")
            .then()
                .statusCode(200)
                .body(containsString("same"));
    }
}
