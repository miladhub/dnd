package org.meh.dnd;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;

@Path("/")
public class RestServer
{
    private final DMChannel dmChannel = new InMemoryDMChannel();
    private final PlayerChannel playersChannel = new InMemoryPlayerChannel();
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final DnD dnd = new DnD(gameRepository, dmChannel, playersChannel);

    @PostConstruct
    public void initialize() {
        Game game = new Game(
                "42",
                GameMode.EXPLORING,
                new ExploreOutput(
                        "You are exploring the Dark Forest, what do you do?",
                        List.of(new Explore(), new Rest()))
        );
        gameRepository.save(game);
        dmChannel.subscribe("42", pi -> {
            playersChannel.post("42", game.lastOutput());
        });
    }

    @GET
    @Path("/hello")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus REST";
    }

    @GET
    @Path("/game/{gameId}")
    @Produces(MediaType.TEXT_HTML)
    public String enter(
            @PathParam("gameId") String gameId
    ) {
        return toHtml(dnd.enter(gameId));
    }

    private String toHtml(PlayerOutput output) {
        MustacheFactory mf = new DefaultMustacheFactory();
        Reader r = new StringReader(
                //language=HTML
                """
                <div id="game">
                  <p>{{description}}</p>
                  Choices:
                  <ul>
                    {{#choices}}
                    <li>{{.}}</li>
                    {{/choices}}
                  </ul>
                </div>
                """);
        Mustache template = mf.compile(r, "output");
        StringWriter writer = new StringWriter();
        template.execute(writer, output);
        return writer.toString();
    }

    @POST
    @Path("/updates/{gameId}")
    public void post(
            @PathParam("gameId") String gameId,
            @FormParam("description") String description
    ) {
        playersChannel.post(gameId, new ExploreOutput(description, List.of()));
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/updates/{gameId}")
    @RestStreamElementType("text/html")
    public Multi<String> updatesStream(
            //TODO read PC from game and use it to create the HTML view
            @PathParam("gameId") String gameId) {
        return Multi.createFrom().<String>emitter(
                me -> playersChannel.subscribe(
                        gameId,
                        po -> me.emit("<h1>" + po + "</h1>")))
                .onFailure().retry().withBackOff(Duration.ofMillis(100)).indefinitely();
    }
}
