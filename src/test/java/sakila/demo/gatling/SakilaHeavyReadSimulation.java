package sakila.demo.gatling;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Stress-oriented Gatling test that repeatedly hits the heaviest read-only pages of the Sakila
 * application (film list, category list, customer list and rentals search).
 *
 * <p>Run only this simulation with:
 * {@code mvn gatling:test -Dgatling.simulationClass=com.example.demo.gatling.SakilaHeavyReadSimulation}
 */
public class SakilaHeavyReadSimulation extends Simulation {

    private static final String BASE_URL =
            System.getProperty("gatling.baseUrl", "http://localhost:8080");

    private static final int USERS_PER_SEC = Integer.getInteger("gatling.rate", 20);
    private static final int DURATION_SECONDS = Integer.getInteger("gatling.durationSeconds", 60);

    private static final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .userAgentHeader("Gatling Load Test")
            .disableUrlEncoding()
            .check(status().is(200));

    private static final ScenarioBuilder scn = scenario("Heavy reads")
            .exec(http("Film list").get("/film-list"))
            .exec(http("Category list").get("/category-list"))
            .exec(http("Customer list").get("/customer-list"))
            .exec(http("Rentals by date")
                    .get("/rental-list?startDate=2005/05/01&endDate=2005/08/31"))
            .pause(Duration.ofMillis(200));

    private static final Assertion[] assertions = {
            global().responseTime().percentile(95).lt(3000),
            global().successfulRequests().percent().gt(95.0)
    };

    {
        setUp(scn.injectOpen(
                constantUsersPerSec(USERS_PER_SEC).during(Duration.ofSeconds(DURATION_SECONDS))))
                .protocols(httpProtocol)
                .assertions(assertions);
    }
}
