package org.meh.dnd;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import static org.meh.dnd.GameMode.EXPLORING;

public class GameSaveLoad
{
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String save(Game game)
    throws IOException {
        GameSave save = createSaveFrom(game);
        return mapper.writeValueAsString(save);
    }

    public static Game load(Reader is)
    throws IOException {
        GameSave save = mapper.readValue(is, GameSave.class);
        return load(save);
    }

    public static Game createGameFrom(
            String background,
            String place,
            GameChar gameChar
    ) {
        return new Game(
                EXPLORING,
                List.of(new ExploreOutput(
                        place,
                        "Ready.",
                        List.of(new Start(place)),
                        "")),
                gameChar,
                new Peace(),
                new Chat(List.of()),
                background,
                place,
                new Nobody(),
                List.of(),
                List.of()
        );
    }

    private static GameSave createSaveFrom(Game game) {
        return new GameSave(
                game.mode(),
                game.playerChar(),
                game.background(),
                game.place(),
                game.diary(),
                saveQuest(game.quest())
        );
    }

    private static Game load(GameSave save) {
        return new Game(
                save.mode(),
                List.of(save.quest().isEmpty()
                        ? new ExploreOutput(save.place(), "Ready.", List.of(new Start(save.place())), "")
                        : new ExploreOutput(save.place(), "Ready to continue.", List.of(new Explore(save.place())), "")
                ),
                save.playerChar(),
                new Peace(),
                new Chat(List.of()),
                save.background(),
                save.place(),
                new Nobody(),
                save.diary(),
                loadQuest(save.quest())
        );
    }

    private static List<QuestGoal> loadQuest(List<QuestGoalSave> quest) {
        return quest.stream().map(
                g -> (QuestGoal) switch (g.goalType()) {
                    case "explore" -> new ExploreGoal(
                            g.target(),
                            g.reached()
                    );
                    case "kill" -> new KillGoal(
                            NpcType.valueOf(g.type()),
                            g.target(),
                            g.reached()
                    );
                    case "talk" -> new TalkGoal(
                            NpcType.valueOf(g.type()),
                            g.target(),
                            g.reached()
                    );
                    default ->
                            throw new IllegalStateException("Unexpected value: " + g.goalType());
                }).toList();
    }

    private static List<QuestGoalSave> saveQuest(List<QuestGoal> quest) {
        return quest.stream().map(
                g -> switch (g) {
                    case ExploreGoal e ->
                            new QuestGoalSave(
                                    "explore",
                                    "",
                                    e.target(),
                                    e.reached()
                            );
                    case KillGoal k ->
                            new QuestGoalSave(
                                    "kill",
                                    k.type().name(),
                                    k.target(),
                                    k.reached()
                            );
                    case TalkGoal t ->
                            new QuestGoalSave(
                                    "talk",
                                    t.type().name(),
                                    t.target(),
                                    t.reached()
                            );
                }
        ).toList();
    }
}
