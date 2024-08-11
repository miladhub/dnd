package org.meh.dnd;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.meh.dnd.openai.HttpUrlConnectionOpenAiClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.meh.dnd.GameMode.EXPLORING;

@Path("/")
public class RestServer
{
    private final static Logger LOG = Logger.getLogger(RestServer.class);
    private final DMChannel dmChannel = new InMemoryDMChannel();
    private final PlayerChannel playersChannel = new InMemoryPlayerChannel();
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final DndCombat combat = new DndCombat();
    private final DnD dnd = new DnD(gameRepository, dmChannel, playersChannel, combat);
    private final DM dm = new AiDM(dmChannel, playersChannel,
            new HttpUrlConnectionOpenAiClient(), gameRepository);

    @PostConstruct
    public void initialize() {
        dmChannel.subscribe("42", pi -> {
            try {
                dm.process("42", pi);
            } catch (Exception e) {
                LOG.error(e);
            }
        });
    }

    @POST
    @Path("/character/{gameId}")
    @Produces(MediaType.TEXT_HTML)
    public Response createCharacter(
            @PathParam("gameId") String gameId,
            @FormParam("name") String name,
            @FormParam("class") String clazz,
            @FormParam("level") int level,
            @FormParam("max_hp") int maxHp,
            @FormParam("armor_class") int ac,
            @FormParam("experience_points") int xp,
            @FormParam("strength") int strength,
            @FormParam("constitution") int constitution,
            @FormParam("dexterity") int dexterity,
            @FormParam("intelligence") int intelligence,
            @FormParam("wisdom") int wisdom,
            @FormParam("charisma") int charisma,
            @FormParam("background") String background,
            @FormParam("place") String place,
            @FormParam("actions") int actions,
            @FormParam("bonus_actions") int bonusActions,
            @FormParam("speed") int speed
    ) {
        CharClass charClass = CharClass.valueOf(clazz.toUpperCase());
        GameChar gameChar = new GameChar(
                name,
                level,
                charClass,
                maxHp,
                maxHp,
                ac,
                xp,
                xp,
                new Stats(
                        strength,
                        dexterity,
                        constitution,
                        intelligence,
                        wisdom,
                        charisma
                ),
                switch (charClass) {
                    case FIGHTER -> DndCombat.FIGHTER_WEAPONS;
                    case WIZARD -> DndCombat.WIZARD_WEAPONS;
                },
                switch (charClass) {
                    case FIGHTER -> List.of();
                    case WIZARD -> DndCombat.WIZARD_SPELLS;
                },
                new AvailableActions(actions, bonusActions, speed)
        );
        Game game = new Game(
                gameId,
                EXPLORING,
                List.of(new ExploreOutput(
                        place,
                        "Ready.",
                        List.of(new Start()))),
                gameChar,
                new Peace(),
                new Chat(List.of()),
                background,
                place,
                new Nobody()
        );
        gameRepository.save(game);
        return Response.ok()
                .header("HX-Redirect", "/")
                .build();
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
    public Response enter(
            @PathParam("gameId") String gameId
    ) {
        return dnd.enter(gameId)
                .map(o -> toHtml(gameId, o))
                .map(Response::ok)
                .orElse(Response.seeOther(URI.create("/create.html")))
                .build();
    }

    @POST
    @Path("/updates/{gameId}")
    public void doAction(
            @PathParam("gameId") String gameId,
            @FormParam("action") String action,
            @FormParam("info") String info
    ) {
        dnd.doAction(gameId, ActionParser.actionFrom(action, info));
    }

    @POST
    @Path("/combat/{gameId}")
    public void combat(
            @PathParam("gameId") String gameId,
            @FormParam("action") String action,
            @FormParam("info") String info,
            @FormParam("bonus") boolean bonusAction
    )
    throws InterruptedException {
        CombatActions combatAction = ActionParser.combatActionFrom(action, info);
        dnd.playCombatAction(gameId, combatAction, bonusAction);
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
                .onItem().call(i ->
                        // Delay the emission until the returned uni emits its item
                        Uni.createFrom().nullItem().onItem().delayIt().by(Duration.ofSeconds(1))
                )
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
                    List.of(actionView(new Explore(game.place())))));
            case CombatOutput co -> Templates.combat(new CombatView(
                    co.playerTurn(), co.playerWon(), co.enemyWon(),
                    co.playerWon() || co.enemyWon(),
                    co.playerTurn()? co.playerAvailableActions() : co.opponentAvailableActions(),
                    new CharacterView(pc.name(),
                            pc.level(),
                            pc.charClass().toString().toLowerCase(),
                            pc.ac(),
                            pc.xp(),
                            pc.maxHp(),
                            pc.hp(),
                            pc.maxHp(),
                            pc.stats().strength(),
                            pc.stats().dexterity(),
                            pc.stats().constitution(),
                            pc.stats().intelligence(),
                            pc.stats().wisdom(),
                            pc.stats().charisma()),
                    new CharacterView(co.opponent().name(),
                            co.opponent().level(),
                            co.opponent().charClass().toString().toLowerCase(),
                            co.opponent().ac(),
                            co.opponent().xp(),
                            co.opponent().nextXp(),
                            co.opponent().hp(),
                            co.opponent().maxHp(),
                            co.opponent().stats().strength(),
                            co.opponent().stats().dexterity(),
                            co.opponent().stats().constitution(),
                            co.opponent().stats().intelligence(),
                            co.opponent().stats().wisdom(),
                            co.opponent().stats().charisma()),
                    String.join("\n", co.log()).trim(),
                    co.distance(),
                    co.availableActions().stream().flatMap(a -> switch (a.type()) {
                        case WEAPON -> Stream.of(
                                new CombatActionView("Melee", a.info(),
                                        "Attack with " + a.info(), a.bonusAction()));
                        case SPELL -> Stream.of(
                                new CombatActionView("Spell", a.info(),
                                        "Cast " + a.info(), a.bonusAction()));
                        case MOVE -> Stream.of(
                                new CombatActionView("MoveForward", a.info(),
                                        "Move " + a.info() + " feet forward",
                                        a.bonusAction()),
                                new CombatActionView("MoveBackward", a.info(),
                                        "Move " + a.info() + " feet backward",
                                        a.bonusAction()));
                        case END_TURN -> Stream.of(
                                new CombatActionView("EndTurn", "", "End Turn", false)
                        );
                    }).toList()
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
            case Start start -> new ActionView("Start", "", "Play");
        };
    }
}
