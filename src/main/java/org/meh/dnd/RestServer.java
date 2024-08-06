package org.meh.dnd;

import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.meh.dnd.openai.HttpUrlConnectionOpenAiClient;

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
                        List.of(new Explore(""), new Rest())),
                new GameChar(
                        "Randall",
                        List.of(new Weapon("Sword")),
                        List.of(new Spell("Magic missile"))
                ),
                new Peace()
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
        return toHtml(gameId, dnd.enter(gameId));
    }

    @POST
    @Path("/updates/{gameId}")
    public void post(
            @PathParam("gameId") String gameId,
            @FormParam("action") String action,
            @FormParam("info") String info
    ) {
        if (gameRepository.gameById(gameId).orElseThrow().mode() != GameMode.COMBAT)
            dnd.playTurn(gameId, ActionParser.actionFrom(action, info));
        else
            dnd.combatTurn(gameId, ActionParser.combatActionsFrom(action, info));
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
                                po -> me.emit(toHtml(gameId, po))))
                .onFailure().retry().withBackOff(Duration.ofMillis(100)).indefinitely();
    }

    private String toHtml(
            String gameId,
            PlayerOutput output
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        GameChar pc = game.playerChar();
        return switch (output) {
            case DialogueOutput d -> Templates.template(new GameView(
                    d.phrase(),
                    d.answers().stream()
                            .map(RestServer::actionView)
                            .toList()));
            case ExploreOutput e -> Templates.template(new GameView(
                    e.description(),
                    e.choices().stream()
                            .map(RestServer::actionView)
                            .toList()));
            case RestOutput ignored -> Templates.template(new GameView(
                    "You are resting.",
                    List.of(actionView(new Explore("")))));
            case CombatOutput co -> Templates.combat(new CombatView(
                    co.playerTurn(),
                    new CharacterView(pc.name()),
                    new CharacterView(co.opponent().name()),
                    co.lastAction(),
                    Stream.concat(
                            pc.weapons().stream().map(
                                            w -> new ActionView("Melee",
                                                    w.name(),
                                                    "Attack with " + w.name())),
                            pc.spells().stream().map(
                                    s -> new ActionView("Spell",
                                            s.name(),
                                            "Cast " + s.name()))
                            ).toList()
            ));
        };
    }

    private static ActionView actionView(Actions a) {
        return switch (a) {
            case Attack attack -> new ActionView("Attack", attack.target(),
                    "Attack " + attack.target());
            case Rest ignored -> new ActionView("Rest", "", "Rest");
            case Dialogue d -> new ActionView("Dialogue", d.target(),
                    "Talk to " + d.target());
            case Explore e -> new ActionView("Explore", e.place(),
                    "Explore " + e.place());
            case EndDialogue ignored -> new ActionView("EndDialogue", "", "End Dialogue");
            case Say say -> new ActionView("Say", say.what(), say.what());
        };
    }
}
