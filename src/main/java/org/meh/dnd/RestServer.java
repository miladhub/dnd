package org.meh.dnd;

import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.meh.dnd.openai.HttpUrlConnectionOpenAiClient;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.meh.dnd.GameMode.COMBAT;

@Path("/")
public class RestServer
{
    private final static Logger LOG = Logger.getLogger(RestServer.class);
    private final DMChannel dmChannel = new InMemoryDMChannel();
    private final PlayerChannel playersChannel = new InMemoryPlayerChannel();
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final DnD dnd = new DnD(gameRepository, dmChannel, playersChannel);
    private final DM dm = new AiDM(dmChannel, playersChannel,
            new HttpUrlConnectionOpenAiClient(), gameRepository);

    @PostConstruct
    public void initialize() {
        loadInitialGame("42");
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
    public void doAction(
            @PathParam("gameId") String gameId,
            @FormParam("action") String action,
            @FormParam("info") String info
    )
    throws InterruptedException {
        if (gameRepository.gameById(gameId).orElseThrow().mode() == COMBAT) {
            dnd.playCombatTurn(gameId, ActionParser.combatActionFrom(action, info));
            Game game = gameRepository.gameById(gameId).orElseThrow();
            Fight fight = (Fight) game.combatStatus();
            if (fight.outcome() == FightStatus.IN_PROGRESS) {
                Thread.sleep(Duration.of(1, ChronoUnit.SECONDS));
                dnd.enemyCombatTurn(gameId, Combat.generateAttack(fight));
            }
        } else {
            dnd.doAction(gameId, ActionParser.actionFrom(action, info));
        }
    }

    @POST
    @Path("/restart/{gameId}")
    @Produces(MediaType.TEXT_HTML)
    public Response restart(
            @PathParam("gameId") String gameId
    ) {
        loadInitialGame(gameId);
        return Response.ok()
                .header("HX-Redirect", "/")
                .build();
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
                    co.playerTurn(), co.playerWon(), co.enemyWon(),
                    co.playerWon() || co.enemyWon(),
                    new CharacterView(pc.name(), pc.hp(), pc.maxHp()),
                    new CharacterView(co.opponent().name(), co.opponent().hp(), co.opponent().maxHp()),
                    co.lastAction(),
                    co.distance(),
                    Stream.concat(
                            Stream.concat(
                            pc.weapons().stream().map(
                                            w -> new ActionView("Melee",
                                                    w.name(),
                                                    "Attack with " + w.name())),
                            pc.spells().stream().map(
                                    s -> new ActionView("Spell",
                                            s.name(),
                                            "Cast " + s.name()))
                            ),
                            Stream.of(new ActionView("MoveForward", "5",
                                            "Move 5 feet forward"),
                                    new ActionView("MoveBackward", "5",
                                            "Move 5 feet backward"))
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

    private void loadInitialGame(String gameId) {
        Game game = new Game(
                gameId,
                GameMode.EXPLORING,
                new ExploreOutput(
                        "You are exploring the Dark Forest, what do you do?",
                        List.of(new Explore(""), new Rest())),
                new GameChar(
                        "Randall", 10, 10,
                        List.of(new Weapon("sword")),
                        List.of(new Spell("Magic Missile"))
                ),
                new Peace(),
                new Chat(List.of())
        );
        gameRepository.save(game);
    }
}
