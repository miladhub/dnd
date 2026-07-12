package org.meh.dnd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SimpleDMTest
{
    private final PlayerChannel playerChannel = new InMemoryPlayerChannel();
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final SimpleDM dm = new SimpleDM(playerChannel, gameRepository);
    private final List<PlayerOutput> outputs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        playerChannel.subscribe(outputs::add);
        GameChar gc = new GameChar("Hero", 3, CharClass.FIGHTER,
                10, 10, 15, 1000, 1500,
                DndCombat.STATS_FIGHTER,
                DndCombat.FIGHTER_WEAPONS, List.of(),
                DndCombat.STANDARD_ACTIONS, new SpellSlots(0, 0, 0, 0, 0, 0, 0, 0, 0));
        Game game = new Game(GameMode.EXPLORING, List.of(), gc,
                new Peace(), new NoChat(), "A brave adventurer.",
                "Dark Forest", new Nobody(), List.of(), List.of());
        gameRepository.save(game);
    }

    @Test
    void startProducesExploreOutput() {
        dm.process(new Start("Dark Forest"));
        assertThat(outputs, hasSize(1));
        assertThat(outputs.get(0), instanceOf(ExploreOutput.class));
        ExploreOutput output = (ExploreOutput) outputs.get(0);
        assertThat(output.description(), not(blankOrNullString()));
        assertThat(output.choices(), is(not(empty())));
    }

    @Test
    void startSetsQuestGoals() {
        dm.process(new Start("Dark Forest"));
        Game game = gameRepository.game().orElseThrow();
        assertThat(game.quest(), is(not(empty())));
    }

    @Test
    void exploreProducesExploreOutput() {
        dm.process(new Explore("Dark Forest"));
        assertThat(outputs, hasSize(1));
        assertThat(outputs.get(0), instanceOf(ExploreOutput.class));
        ExploreOutput output = (ExploreOutput) outputs.get(0);
        assertThat(output.description(), not(blankOrNullString()));
        assertThat(output.choices(), is(not(empty())));
    }

    @Test
    void explorePreservesPlace() {
        dm.process(new Explore("Dark Forest"));
        ExploreOutput output = (ExploreOutput) outputs.get(0);
        assertThat(output.place(), is("Dark Forest"));
    }

    @Test
    void dialogueProducesDialogueOutput() {
        gameRepository.save(g -> g
                .withMode(GameMode.DIALOGUE)
                .withDialogueTarget(new Somebody("Gorak", NpcType.WARRIOR)));
        dm.process(new Dialogue("Gorak", NpcType.WARRIOR));
        assertThat(outputs, hasSize(1));
        assertThat(outputs.get(0), instanceOf(DialogueOutput.class));
        DialogueOutput output = (DialogueOutput) outputs.get(0);
        assertThat(output.phrase(), not(blankOrNullString()));
        assertThat(output.answers(), is(not(empty())));
    }

    @Test
    void dialogueStoresChat() {
        gameRepository.save(g -> g
                .withMode(GameMode.DIALOGUE)
                .withDialogueTarget(new Somebody("Morgana", NpcType.MAGIC)));
        dm.process(new Dialogue("Morgana", NpcType.MAGIC));
        Game game = gameRepository.game().orElseThrow();
        assertThat(game.chat(), instanceOf(ChatWith.class));
        ChatWith chat = (ChatWith) game.chat();
        assertThat(chat.target(), is("Morgana"));
    }

    @Test
    void sayProducesDialogueOutput() {
        gameRepository.save(g -> g
                .withMode(GameMode.DIALOGUE)
                .withDialogueTarget(new Somebody("Gorak", NpcType.WARRIOR))
                .withChat(new ChatWith("Gorak", List.of(
                        new ChatMessage(ChatRole.DM, "Gorak", "Hello.")))));
        dm.process(new Say("Hello there."));
        assertThat(outputs, hasSize(1));
        assertThat(outputs.get(0), instanceOf(DialogueOutput.class));
        DialogueOutput output = (DialogueOutput) outputs.get(0);
        assertThat(output.phrase(), not(blankOrNullString()));
        assertThat(output.answers(), is(not(empty())));
    }

    @Test
    void endDialogueProducesExploreOutput() {
        gameRepository.save(g -> g
                .withQuest(List.of(new ExploreGoal("Dark Forest Cave", false))));
        QuestGoal goal = new TalkGoal(NpcType.WARRIOR, "Gorak", false);
        dm.process(new EndDialogue("I must be going.", goal));
        assertThat(outputs, hasSize(1));
        assertThat(outputs.get(0), instanceOf(ExploreOutput.class));
        ExploreOutput output = (ExploreOutput) outputs.get(0);
        assertThat(output.description(), not(blankOrNullString()));
        assertThat(output.choices(), is(not(empty())));
    }

    @Test
    void endDialogueAddsGoalToQuest() {
        QuestGoal goal = new ExploreGoal("Ruined Temple", false);
        dm.process(new EndDialogue("I should check it out.", goal));
        Game game = gameRepository.game().orElseThrow();
        assertThat(game.quest(), hasItem(goal));
    }

    @Test
    void exploreOutputIncludesRestOption() {
        dm.process(new Explore("Dark Forest"));
        ExploreOutput output = (ExploreOutput) outputs.get(0);
        assertThat(output.choices(), hasItem(instanceOf(Rest.class)));
    }

    @Test
    void dialogueAnswersContainSayOptions() {
        gameRepository.save(g -> g
                .withMode(GameMode.DIALOGUE)
                .withDialogueTarget(new Somebody("Elric", NpcType.MAGIC)));
        dm.process(new Dialogue("Elric", NpcType.MAGIC));
        DialogueOutput output = (DialogueOutput) outputs.get(0);
        assertThat(output.answers(), hasItem(instanceOf(Say.class)));
    }

    @Test
    void dialogueAnswersContainEndDialogue() {
        gameRepository.save(g -> g
                .withMode(GameMode.DIALOGUE)
                .withDialogueTarget(new Somebody("Elric", NpcType.MAGIC)));
        dm.process(new Dialogue("Elric", NpcType.MAGIC));
        DialogueOutput output = (DialogueOutput) outputs.get(0);
        assertThat(output.answers(), hasItem(instanceOf(EndDialogue.class)));
    }
}
