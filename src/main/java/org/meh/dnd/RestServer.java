package org.meh.dnd;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.meh.dnd.openai.HttpUrlConnectionOpenAiClient;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Path("/")
public class RestServer
{
    private final static Logger LOG = Logger.getLogger(RestServer.class);
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();
    private final DMChannel dmChannel =
            new InMemoryThreadedDMChannel(executor);
    private final PlayerChannel playersChannel =
            new InMemoryThreadedPlayerChannel(executor);
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final DndCombat combat = new DndCombat();
    private final DnD dnd = new DnD(gameRepository, dmChannel, playersChannel, combat);
    private final DM dm = new AiDM(dmChannel, playersChannel,
            new HttpUrlConnectionOpenAiClient(), gameRepository);

    @PostConstruct
    public void initialize() {
        dmChannel.subscribe(pi -> {
            try {
                dm.process(pi);
            } catch (Exception e) {
                LOG.error(e);
            }
        });
    }

    @PreDestroy
    public void shutDown()
    throws InterruptedException {
        executor.shutdown();
        assert(executor.awaitTermination(3, TimeUnit.SECONDS));
    }

    @POST
    @Path("/character")
    @Produces(MediaType.TEXT_HTML)
    public Response createCharacter(
            @FormParam("name") String name,
            @FormParam("class") String clazz,
            @FormParam("level") int level,
            @FormParam("max_hp") int maxHp,
            @FormParam("armor_class") int ac,
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
                DndCombat.xpAtLevel(level),
                DndCombat.xpAtLevel(level + 1),
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
                new AvailableActions(actions, bonusActions, speed),
                DndCombat.spellSlots(charClass, level)
        );
        Game game = GameSaveLoad.createGameFrom(background, place, gameChar);
        gameRepository.save(game);
        return Response.ok()
                .header("HX-Redirect", "/")
                .build();
    }

    @POST
    @Path("/quest")
    @Produces(MediaType.TEXT_HTML)
    public Response createQuest(
            @FormParam("background") String background,
            @FormParam("place") String place
    ) {
        Game game = gameRepository.game().orElseThrow();
        Game newGame = GameSaveLoad.createGameFrom(background, place, game.playerChar());
        gameRepository.save(newGame);
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
    @Path("/game")
    @Produces(MediaType.TEXT_HTML)
    public Response enter() {
        return dnd.enter()
                .map(this::toHtml)
                .map(Response::ok)
                .orElse(Response.ok().header("HX-Redirect", "/create.html"))
                .build();
    }

    @POST
    @Path("/actions")
    public void doAction(
            @FormParam("action") String action,
            @FormParam("info") String info
    ) {
        Game game = gameRepository.game().orElseThrow();
        dnd.doAction(ViewEncoderDecoder.decodeAction(action, info, game));
    }

    @POST
    @Path("/combat")
    public void combat(
            @FormParam("action") String action,
            @FormParam("info") String info,
            @FormParam("bonus") boolean bonusAction
    ) {
        CombatActions combatAction = ViewEncoderDecoder.decodeCombatAction(action, info);
        dnd.playCombatAction(combatAction, bonusAction);
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/updates")
    @RestStreamElementType("text/html")
    public Multi<String> updatesStream() {
        return Multi.createFrom().<String>emitter(
                        me -> playersChannel.subscribe(
                                po -> me.emit(toHtml(po))))
                .onItem().call(i ->
                        // Delay the emission until the returned uni emits its item
                        Uni.createFrom().nullItem().onItem().delayIt().by(Duration.ofMillis(500))
                )
                .onFailure().retry().withBackOff(Duration.ofMillis(100)).indefinitely();
    }

    private String toHtml(
            PlayerOutput output
    ) {
        Game game = gameRepository.game().orElseThrow();
        return ViewEncoderDecoder.encodeOutput(output, game);
    }

    @GET
    @Path("/save")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response save()
    throws IOException {
        Optional<Game> game = gameRepository.game();
        if (game.isEmpty()) {
            return Response.ok("".getBytes(StandardCharsets.UTF_8))
                    .header("Content-disposition",
                            "attachment; filename=game.json")
                    .build();
        } else {
            String save = GameSaveLoad.save(game.get());
            return Response.ok(save)
                    .header("Content-disposition",
                            "attachment; filename=game.json")
                    .build();
        }
    }

    @POST
    @Path("/upload")
    public Response load(@RestForm("game") FileUpload file)
    throws Exception {
        try (FileReader r = new FileReader(file.filePath().toFile())
        ) {
            Game game = GameSaveLoad.load(r);
            gameRepository.save(game);
            return Response.ok()
                    .header("HX-Redirect", "/")
                    .build();
        }
    }

    @GET
    @Path("/level-up")
    @Produces(MediaType.TEXT_HTML)
    public String viewLevelUp() {
        Game game = gameRepository.game().orElseThrow();
        GameChar gc = game.playerChar();
        GameChar newGc = DndCombat.levelUp(gc);
        return Templates.template(new LevelUpView(
                newGc.level(),
                newGc.xp(),
                newGc.nextXp(),
                newGc.maxHp(),
                newGc.charClass() == CharClass.WIZARD,
                newGc.spellSlots(),
                DndCombat.proficiencyBonus(newGc)
        ));
    }

    @POST
    @Path("/level-up")
    public Response saveLevelUp() {
        Game game = gameRepository.game().orElseThrow();
        GameChar gc = game.playerChar();
        GameChar newGc = DndCombat.levelUp(gc);
        gameRepository.save(g -> g.withPlayerChar(newGc));
        return Response.ok()
                .header("HX-Redirect", "/")
                .build();
    }
}
