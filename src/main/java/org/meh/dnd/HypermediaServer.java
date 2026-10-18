package org.meh.dnd;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

@Path("/")
public class HypermediaServer
{
    @jakarta.inject.Inject
    GameEngine engine;

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
        engine.save(game);
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
        Optional<Game> existingGame = engine.currentGame();
        if (existingGame.isEmpty()) {
            return Response.ok()
                    .header("HX-Redirect", "/create.html")
                    .build();
        }
        Game game = existingGame.get();
        Game newGame = GameSaveLoad.createGameFrom(background, place, game.playerChar());
        engine.save(newGame);
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
        return engine.enter()
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
        Game game = engine.currentGame().orElseThrow();
        engine.doAction(ViewEncoderDecoder.decodeAction(action, info, game));
    }

    @POST
    @Path("/combat")
    public void combat(
            @FormParam("action") String action,
            @FormParam("info") String info,
            @FormParam("target") String target,
            @FormParam("bonus") boolean bonusAction
    ) {
        CombatActions combatAction = ViewEncoderDecoder.decodeCombatAction(action, info, target);
        engine.playCombatAction(combatAction, bonusAction);
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/updates")
    @RestStreamElementType("text/html")
    public Multi<String> updatesStream() {
        return Multi.createFrom().<String>emitter(
                        me -> {
                            AtomicBoolean closed = new AtomicBoolean();
                            Consumer<PlayerOutput> sub = new Consumer<>() {
                                @Override
                                public void accept(PlayerOutput po) {
                                    if (me.isCancelled() || closed.get()) {
                                        closed.set(true);
                                        engine.unsubscribePlayer(this);
                                    } else {
                                        try {
                                            me.emit(toHtml(po));
                                        } catch (Exception ignored) {
                                            closed.set(true);
                                            engine.unsubscribePlayer(this);
                                        }
                                    }
                                }
                            };
                            engine.subscribePlayer(sub);
                            me.onTermination(() -> {
                                closed.set(true);
                                engine.unsubscribePlayer(sub);
                            });
                        }, BackPressureStrategy.LATEST)
                .onOverflow().drop()
                .onFailure().recoverWithMulti(Multi.createFrom().empty());
    }

    private String toHtml(
            PlayerOutput output
    ) {
        Game game = engine.currentGame().orElseThrow();
        return ViewEncoderDecoder.encodeOutput(output, game);
    }

    @GET
    @Path("/save")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response save()
    throws IOException {
        Optional<Game> game = engine.currentGame();
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
        if (file == null || file.filePath() == null)
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Choose a saved game file to upload.")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        try (FileReader r = new FileReader(file.filePath().toFile())
        ) {
            Game game = GameSaveLoad.load(r);
            engine.save(game);
            return Response.ok()
                    .header("HX-Redirect", "/")
                    .build();
        }
    }

    @GET
    @Path("/level-up")
    @Produces(MediaType.TEXT_HTML)
    public String viewLevelUp() {
        Game game = engine.currentGame().orElseThrow();
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
        Game game = engine.currentGame().orElseThrow();
        GameChar gc = game.playerChar();
        GameChar newGc = DndCombat.levelUp(gc);
        engine.update(g -> g.withPlayerChar(newGc));
        return Response.ok()
                .header("HX-Redirect", "/")
                .build();
    }
}
