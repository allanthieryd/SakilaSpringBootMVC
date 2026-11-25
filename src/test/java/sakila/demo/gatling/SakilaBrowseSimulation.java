package sakila.demo.gatling;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling load test for the Sakila Spring Boot MVC application.
 *
 * <p>Models a typical visitor browsing the read-only pages of the site: home, actors, films,
 * categories, customers and rentals. Run it with {@code mvn gatling:test}.
 */
public class SakilaBrowseSimulation extends Simulation {

    /** Base URL of the running application (override with -Dgatling.baseUrl=...). */
    private static final String BASE_URL =
            System.getProperty("gatling.baseUrl", "http://localhost:8080/");

    /** Target number of concurrent users (override with -Dgatling.users=...). */
    private static final int PEAK_USERS = Integer.getInteger("gatling.users", 20);

    /** Injection profile durations in seconds (override for short smoke runs). */
    private static final int RAMP_SECONDS = Integer.getInteger("gatling.rampSeconds", 10);
    private static final int HOLD_SECONDS = Integer.getInteger("gatling.holdSeconds", 30);

    /** Pick a random film id from the Sakila sample data for the detail page. */
    private static final FeederBuilder.SeparatedValues<String> filmFeeder =
            csv("film-ids.csv").random();

    private static final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .acceptLanguageHeader("en-US,en;q=0.5")
            .acceptEncodingHeader("gzip, deflate")
            .userAgentHeader("Gatling Load Test")
            // Keep the "yyyy/MM/dd" dates used by /rental-list intact.
            .disableUrlEncoding()
            .check(status().is(200));

    private static final ScenarioBuilder scn = scenario("Browse Sakila")
            .exec(http("Home page").get("/sakila"))
            .pause(Duration.ofSeconds(1))
            .exec(http("Actor list").get("/actor-list"))
            .exec(http("Actor pagination").get("/actor-pagination?page=1&size=20"))
            .pause(Duration.ofSeconds(2))
            .exec(http("Film list").get("/film-list"))
            .pause(Duration.ofMillis(500))
            .feed(filmFeeder)
            .exec(http("Film detail").get("/film/#{filmId}"))
            .pause(Duration.ofSeconds(1))
            .exec(http("Category list").get("/category-list"))
            .exec(http("Country / City list").get("/country-city-list"))
            .exec(http("Customer list").get("/customer-list"))
            .exec(http("Language list").get("/language-list"))
            .exec(http("Staff list").get("/staff-list"))
            .pause(Duration.ofSeconds(2))
            .exec(http("Rentals by date")
                    .get("/rental-list?startDate=2005/05/01&endDate=2005/06/30"));

    private static final Assertion[] assertions = {
            global().responseTime().percentile(95).lt(2000),
            global().successfulRequests().percent().gt(95.0)
    };

    {
        setUp(scn.injectOpen(
                        rampUsers(PEAK_USERS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(PEAK_USERS / 2.0)
                                .during(Duration.ofSeconds(HOLD_SECONDS))))
                .protocols(httpProtocol)
                .assertions(assertions);
    }
}
