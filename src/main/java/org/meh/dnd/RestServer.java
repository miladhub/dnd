package org.meh.dnd;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.meh.dnd.openai.HttpUrlConnectionOpenAiClient;

import java.io.*;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

@Path("/")
public class RestServer
{
    private final static Logger LOG = Logger.getLogger(RestServer.class);
    private final DMChannel dmChannel = new InMemoryDMChannel();
    private final PlayerChannel playersChannel = new InMemoryPlayerChannel();
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final DnD dnd = new DnD(gameRepository, dmChannel, playersChannel);
    private final DM dm = new AiDM(dmChannel, playersChannel, new HttpUrlConnectionOpenAiClient());

    @PostConstruct
    public void initialize() {
        Game game = new Game(
                "42",
                GameMode.EXPLORING,
                new ExploreOutput(
                        "You are exploring the Dark Forest, what do you do?",
                        List.of(new Explore(""), new Rest()))
        );
        gameRepository.save(game);
        dmChannel.subscribe("42", pi -> {
            try {
                dm.process("42", pi);
            } catch (Exception e) {
                LOG.error(e);
            }
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

    @POST
    @Path("/updates/{gameId}")
    public void post(
            @PathParam("gameId") String gameId,
            @FormParam("action") String action,
            @FormParam("info") String info
    ) {
        dnd.playTurn(gameId, ActionParser.actionFrom(action, info));
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/updates/{gameId}")
    @RestStreamElementType("text/html")
    public Multi<String> updatesStream(
            @PathParam("gameId") String gameId) {
        return Multi.createFrom().<String>emitter(
                        me -> playersChannel.subscribe(
                                gameId,
                                po -> me.emit(toHtml(po))))
                .onFailure().retry().withBackOff(Duration.ofMillis(100)).indefinitely();
    }

    private String toHtml(PlayerOutput output) {
        MustacheFactory mf = new DefaultMustacheFactory();
        try (InputStream is = getClass().getResourceAsStream("/_template.html")
        ) {
            assert( is != null );
            Reader r = new InputStreamReader(is);
            Mustache template = mf.compile(r, "output");
            StringWriter writer = new StringWriter();
            template.execute(writer, gameView(output));
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private GameView gameView(PlayerOutput output) {
        return switch (output) {
            case ExploreOutput e -> new GameView(
                    e.description(),
                    e.choices().stream()
                            .map(RestServer::exploreActionView)
                            .toList());
            case RestOutput ignored -> new GameView(
                    "You are resting.",
                    List.of(exploreActionView(new Explore(""))));
            case CombatOutput c -> new GameView(
                    "You are now in combat against " + c.opponent() + ".",
                    List.of());
            case DialogueOutput d -> new GameView(
                    d.phrase(),
                    Stream.concat(
                            d.answers().stream()
                                    .map(a -> new ChoiceView("Say", a, a)),
                            Stream.of(new ChoiceView("EndDialogue", "", "End Dialogue")))
                            .toList());
        };
    }

    private static ChoiceView exploreActionView(Actions a) {
        return switch (a) {
            case Attack attack -> new ChoiceView("Attack", attack.target(),
                    "Attack " + attack.target());
            case Rest ignored -> new ChoiceView("Rest", "", "Rest");
            case Dialogue d -> new ChoiceView("Dialogue", d.target(),
                    "Talk to " + d.target());
            case Explore e -> new ChoiceView("Explore", e.place(),
                    "Explore " + e.place());
            default ->
                    throw new IllegalStateException("Unexpected value: " + a);
        };
    }
}
