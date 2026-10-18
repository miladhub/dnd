package org.meh.dnd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public record SimpleDM(
        PlayerChannel playerChannel,
        GameRepository gameRepository
) implements DM {
    private static final Random RANDOM = new Random();

    private static final List<String> WARRIOR_NAMES = List.of(
            "Gorak", "Brunhilda", "Ragnar", "Sif", "Thorn");

    private static final List<String> MAGIC_NAMES = List.of(
            "Elric", "Morgana", "Zara", "Finnean", "Lyra");

    private static final List<String> BEAST_NAMES = List.of(
            "Wolf", "Bear", "Giant Spider", "Dire Boar", "Panther");

    private static final List<String> PLACE_SUFFIXES = List.of(
            "Cave", "Ruins", "Riverbank", "Clearing", "Camp", "Temple", "Lake", "Pass");

    private static final List<String> WARRIOR_GREETINGS = List.of(
            "Halt! State your business in these lands.",
            "Well met, traveler. I've not seen your face before.",
            "You look like you can handle yourself. What brings you here?",
            "Ho there! Careful -- these parts aren't safe for wanderers.");

    private static final List<String> WARRIOR_RESPONSES = List.of(
            "Hmph. I'll believe it when I see it.",
            "Fair enough. Stay on the path and you might survive.",
            "I've heard rumors of trouble to the north. Could be your concern, could be not.",
            "If you're heading into danger, make sure you're prepared.");

    private static final List<String> WARRIOR_SAY_OPTIONS = List.of(
            "I'm just passing through.",
            "Have you seen anything unusual around here?",
            "I could use a lead. Anything interesting nearby?");

    private static final List<String> MAGIC_GREETINGS = List.of(
            "Ah, a visitor to these humble parts. How curious.",
            "The air tingles with your presence. What do you seek?",
            "I've foreseen your arrival... more or less. What can I do for you?",
            "Welcome, wanderer. I sensed someone approaching.");

    private static final List<String> MAGIC_RESPONSES = List.of(
            "Interesting. That explains much.",
            "The threads of fate are tangled indeed. Be careful.",
            "I see. Well, knowledge is its own reward.",
            "There are forces at work beyond mortal understanding.");

    private static final List<String> MAGIC_SAY_OPTIONS = List.of(
            "What can you tell me about this place?",
            "Do you know anything about the dangers ahead?",
            "I'm looking for adventure. Any leads?");

    private static final List<String> BEAST_GREETINGS = List.of(
            "[The beast watches you cautiously.]",
            "[A low growl rumbles from the creature's chest.]",
            "[The beast sniffs the air, uncertain of you.]",
            "[It tilts its head, studying you with intelligent eyes.]");

    private static final List<String> BEAST_RESPONSES = List.of(
            "[The beast seems to calm down, though it remains alert.]",
            "[It huffs and looks away, no longer seeing you as a threat.]",
            "[The creature takes a step closer, curious but wary.]",
            "[It whines softly and retreats into the brush.]");

    private static final List<String> BEAST_SAY_OPTIONS = List.of(
            "[Slowly approach the creature.]",
            "[Speak softly to calm it.]",
            "[Make an offering of food.]");

    private static final List<String> QUIET_DESCRIPTIONS = List.of(
            "{place} is calm and peaceful. Nothing seems out of the ordinary.",
            "A gentle quiet settles over {place}. The world feels at peace.",
            "The hours pass uneventfully in {place}. Nature goes about its business.",
            "A peaceful stillness hangs over {place}. Perhaps a good moment to rest.");

    private static final List<String> ENCOUNTER_DESCRIPTIONS = List.of(
            "As you move through {place}, you notice someone up ahead.",
            "A figure emerges from the shadows in {place}.",
            "Movement catches your eye in {place}.",
            "A presence makes itself known in {place}.");

    private static final List<String> NEW_PLACE_DESCRIPTIONS = List.of(
            "Through the trees you spot something -- {newPlace} lies nearby.",
            "A path branches off, leading toward {newPlace}.",
            "In the distance, you make out the shape of {newPlace}.",
            "Your instincts tell you that {newPlace} is worth investigating.");

    private static final List<String> HOSTILE_DESCRIPTIONS = List.of(
            "A hostile {npcType} named {name} lunges at you!",
            "You hear a roar -- a {npcType} named {name} charges toward you!",
            "{name}, a savage {npcType}, blocks your path and snarls.",
            "From the shadows, {name} the {npcType} attacks without warning!");

    private static final List<String> HOSTILE_NEW_PLACE_DESC = List.of(
            "As you approach {newPlace}, a {npcType} named {name} ambushes you!",
            "You were heading toward {newPlace} when {name} the {npcType} attacks!",
            "{newPlace} is nearby, but {name} a hostile {npcType} stands in your way.");

    private record EncounterResult(
            String description, String storyLine, List<Actions> choices) {}

    @Override
    public void process(Actions action) {
        Game game = gameRepository.game().orElseThrow();
        switch (action) {
            case Start(String place) -> handleStart(game, place);
            case Explore(String place) -> handleExplore(game, place);
            case Dialogue(String target, NpcType type) -> handleDialogue(game, target, type);
            case Say(String what) -> handleSay(game, what);
            case EndDialogue(String phrase, QuestGoal goal) -> handleEndDialogue(game, goal);
            default -> throw new IllegalStateException("Unexpected action: " + action);
        }
    }

    private void handleStart(Game game, String place) {
        List<QuestGoal> initial = generateInitialQuest(place);
        List<QuestGoal> quest = Quests.updateQuestFromExploring(initial, place);
        EncounterResult er = rollEncounter(place, true);
        List<Actions> choices = Quests.addQuestGoal(er.choices(), quest);
        ExploreOutput output = new ExploreOutput(
                place, er.description(), choices, er.storyLine());
        gameRepository.save(g -> g
                .withLastOutput(output)
                .withStoryLine(output.storyLine())
                .withQuest(quest));
        playerChannel.post(output);
    }

    private void handleExplore(Game game, String place) {
        EncounterResult er = rollEncounter(place, false);
        List<Actions> choices = er.choices();
        if (hasUnreachedGoal(game.quest())) {
            choices = Quests.addQuestGoal(choices, game.quest());
        }
        ExploreOutput output = new ExploreOutput(
                place, er.description(), choices, er.storyLine());
        gameRepository.save(g -> g
                .withLastOutput(output)
                .withStoryLine(output.storyLine()));
        playerChannel.post(output);
    }

    private void handleDialogue(Game game, String target, NpcType type) {
        String greeting = pick(npcGreetings(type));
        List<Actions> answers = buildDialogueAnswers(type, game);
        DialogueOutput output = new DialogueOutput(greeting, answers);
        gameRepository.save(g -> g
                .withChat(new ChatWith(target, List.of(
                        new ChatMessage(ChatRole.DM, target, greeting))))
                .withLastOutput(output));
        playerChannel.post(output);
    }

    private void handleSay(Game game, String what) {
        Somebody npc = (Somebody) game.dialogueTarget();
        ChatWith chat = (ChatWith) game.chat();
        String response = pick(npcResponses(npc.type()));
        List<Actions> answers = buildDialogueAnswers(npc.type(), game);
        DialogueOutput output = new DialogueOutput(response, answers);
        gameRepository.save(g -> g
                .withChat(chat.add(
                        new ChatMessage(ChatRole.PLAYER,
                                g.playerChar().name(), what),
                        new ChatMessage(ChatRole.DM, chat.target(), response)))
                .withLastOutput(output));
        playerChannel.post(output);
    }

    private void handleEndDialogue(Game game, QuestGoal goal) {
        List<QuestGoal> newGoals = new ArrayList<>(game.quest());
        newGoals.add(goal);
        String place = game.place();
        EncounterResult er = rollEncounter(place, false);
        List<Actions> choices = er.choices();
        if (hasUnreachedGoal(newGoals)) {
            choices = Quests.addQuestGoal(choices, newGoals);
        }
        ExploreOutput output = new ExploreOutput(
                place, er.description(), choices, er.storyLine());
        gameRepository.save(g -> g
                .withChat(new NoChat())
                .withDialogueTarget(new Nobody())
                .withLastOutput(output)
                .withStoryLine(output.storyLine())
                .withQuest(newGoals));
        playerChannel.post(output);
    }

    private EncounterResult rollEncounter(String place, boolean forceContent) {
        int roll;
        if (forceContent) {
            roll = RANDOM.nextInt(17) + 4;
        } else {
            roll = RANDOM.nextInt(20) + 1;
        }

        if (roll <= 3) {
            String desc = fillTemplate(pick(QUIET_DESCRIPTIONS), place);
            return new EncounterResult(desc,
                    "A quiet moment in " + place + ".",
                    new ArrayList<>(List.of(new Explore(place), new Rest())));

        } else if (roll <= 7) {
            NpcType type = randomNpcType();
            String name = randomNpcName(type);
            String desc = fillTemplate(pick(ENCOUNTER_DESCRIPTIONS), place)
                    + " A " + type.name().toLowerCase()
                    + " named " + name + " stands nearby.";
            return new EncounterResult(desc,
                    "You met " + name + " in " + place + ".",
                    new ArrayList<>(List.of(
                            new Dialogue(name, type),
                            new Explore(place),
                            new Rest())));

        } else if (roll <= 11) {
            String newPlace = place + " " + pick(PLACE_SUFFIXES);
            String desc = fillTemplateNewPlace(
                    pick(NEW_PLACE_DESCRIPTIONS), newPlace);
            return new EncounterResult(desc,
                    "You discovered " + newPlace + ".",
                    new ArrayList<>(List.of(
                            new Explore(place),
                            new Explore(newPlace),
                            new Rest())));

        } else if (roll <= 14) {
            NpcType type = randomNpcType();
            String name = randomNpcName(type);
            String newPlace = place + " " + pick(PLACE_SUFFIXES);
            String desc = fillTemplate(pick(ENCOUNTER_DESCRIPTIONS), place)
                    + " A " + type.name().toLowerCase() + " named " + name
                    + " is here, and you notice " + newPlace + " nearby.";
            return new EncounterResult(desc,
                    "You met " + name + " near " + newPlace + ".",
                    new ArrayList<>(List.of(
                            new Dialogue(name, type),
                            new Explore(newPlace),
                            new Explore(place),
                            new Rest())));

        } else if (roll <= 18) {
            List<AiEntities.NPC> encounter = hostileEncounter();
            String desc = fillTemplateHostile(
                    pick(HOSTILE_DESCRIPTIONS), encounter.getFirst().type(),
                    encounter.getFirst().name()) + " " + otherEncounterNames(encounter);
            return hostileResult(desc, place, encounter);

        } else {
            List<AiEntities.NPC> encounter = hostileEncounter();
            String newPlace = place + " " + pick(PLACE_SUFFIXES);
            String desc = fillTemplateHostileNewPlace(
                    pick(HOSTILE_NEW_PLACE_DESC), newPlace,
                    encounter.getFirst().type(), encounter.getFirst().name()) + " "
                    + otherEncounterNames(encounter);
            List<Actions> choices = new ArrayList<>(hostileChoices(encounter));
            choices.addAll(List.of(new Explore(newPlace), new Explore(place), new Rest()));
            return new EncounterResult(desc,
                    "You were ambushed near " + newPlace + "!", choices);
        }
    }

    private EncounterResult hostileResult(
            String description,
            String place,
            List<AiEntities.NPC> encounter
    ) {
        List<Actions> choices = new ArrayList<>(hostileChoices(encounter));
        choices.addAll(List.of(new Explore(place), new Rest()));
        return new EncounterResult(description,
                "You were attacked by a group in " + place + "!", choices);
    }

    private static List<Actions> hostileChoices(List<AiEntities.NPC> encounter) {
        return encounter.stream()
                .filter(AiEntities.NPC::hostile)
                .map(npc -> (Actions) new Attack(npc.name(), npc.type(), encounter))
                .toList();
    }

    static List<AiEntities.NPC> hostileEncounter() {
        int hostileCount = 2 + RANDOM.nextInt(2);
        List<AiEntities.NPC> encounter = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (int i = 0; i < hostileCount; i++) {
            NpcType type = randomNpcType();
            encounter.add(new AiEntities.NPC(randomNpcName(type, usedNames), type, true));
        }
        if (RANDOM.nextBoolean()) {
            NpcType type = randomNpcType();
            encounter.add(new AiEntities.NPC(randomNpcName(type, usedNames), type, false));
        }
        return encounter;
    }

    private static String otherEncounterNames(List<AiEntities.NPC> encounter) {
        String names = encounter.stream().skip(1).map(AiEntities.NPC::name).reduce(
                "", (result, name) -> result.isBlank() ? "Also present: " + name
                        : result + ", " + name);
        return names.isBlank() ? "" : names + ".";
    }

    private static List<QuestGoal> generateInitialQuest(String place) {
        String suffix1 = pick(PLACE_SUFFIXES);
        String suffix2 = pick(PLACE_SUFFIXES);
        while (suffix2.equals(suffix1)) {
            suffix2 = pick(PLACE_SUFFIXES);
        }
        NpcType talkType = randomNpcType();
        return List.of(
                new ExploreGoal(place + " " + suffix1, false),
                new TalkGoal(talkType, randomNpcName(talkType), false));
    }

    private List<Actions> buildDialogueAnswers(NpcType type, Game game) {
        List<Actions> answers = new ArrayList<>();
        for (String option : npcSayOptions(type)) {
            answers.add(new Say(option));
        }
        boolean hasUnreachedEndGoal = false;
        for (QuestGoal goal : game.quest()) {
            if (!goal.reached()) {
                String phrase = endPhraseForGoal(goal);
                if (!alreadyInAnswers(answers, goal)) {
                    answers.add(new EndDialogue(phrase, goal));
                    hasUnreachedEndGoal = true;
                }
            }
        }
        if (!hasUnreachedEndGoal) {
            QuestGoal newGoal = generateRandomGoal(game.place());
            String phrase = endPhraseForGoal(newGoal);
            answers.add(new EndDialogue(phrase, newGoal));
        }
        return answers;
    }

    private static boolean alreadyInAnswers(List<Actions> answers, QuestGoal goal) {
        return answers.stream().anyMatch(a ->
                a instanceof EndDialogue ed
                        && ed.goal().target().equals(goal.target()));
    }

    private static QuestGoal generateRandomGoal(String place) {
        return switch (RANDOM.nextInt(3)) {
            case 0 -> new ExploreGoal(
                    place + " " + pick(PLACE_SUFFIXES), false);
            case 1 -> {
                NpcType type = randomNpcType();
                yield new TalkGoal(type, randomNpcName(type), false);
            }
            default -> {
                NpcType type = randomNpcType();
                yield new KillGoal(type, randomNpcName(type), false);
            }
        };
    }

    private static String endPhraseForGoal(QuestGoal goal) {
        return switch (goal) {
            case ExploreGoal eg -> "I should explore " + eg.target() + ".";
            case KillGoal kg -> "I must find " + kg.target() + ".";
            case TalkGoal tg -> "I need to speak with " + tg.target() + ".";
        };
    }

    private static <T> T pick(List<T> list) {
        return list.get(RANDOM.nextInt(list.size()));
    }

    private static NpcType randomNpcType() {
        return NpcType.values()[RANDOM.nextInt(NpcType.values().length)];
    }

    private static String randomNpcName() {
        return randomNpcName(randomNpcType());
    }

    private static String randomNpcName(NpcType type) {
        return switch (type) {
            case WARRIOR -> pick(WARRIOR_NAMES);
            case MAGIC -> pick(MAGIC_NAMES);
            case BEAST -> pick(BEAST_NAMES);
        };
    }

    private static String randomNpcName(NpcType type, Set<String> usedNames) {
        List<String> availableNames = switch (type) {
            case WARRIOR -> WARRIOR_NAMES;
            case MAGIC -> MAGIC_NAMES;
            case BEAST -> BEAST_NAMES;
        };
        List<String> unusedNames = availableNames.stream()
                .filter(name -> !usedNames.contains(name))
                .toList();
        String name = pick(unusedNames.isEmpty() ? availableNames : unusedNames);
        usedNames.add(name);
        return name;
    }

    private static List<String> npcGreetings(NpcType type) {
        return switch (type) {
            case WARRIOR -> WARRIOR_GREETINGS;
            case MAGIC -> MAGIC_GREETINGS;
            case BEAST -> BEAST_GREETINGS;
        };
    }

    private static List<String> npcResponses(NpcType type) {
        return switch (type) {
            case WARRIOR -> WARRIOR_RESPONSES;
            case MAGIC -> MAGIC_RESPONSES;
            case BEAST -> BEAST_RESPONSES;
        };
    }

    private static List<String> npcSayOptions(NpcType type) {
        return switch (type) {
            case WARRIOR -> WARRIOR_SAY_OPTIONS;
            case MAGIC -> MAGIC_SAY_OPTIONS;
            case BEAST -> BEAST_SAY_OPTIONS;
        };
    }

    private static String fillTemplate(String template, String place) {
        return template.replace("{place}", place);
    }

    private static String fillTemplateNewPlace(
            String template, String newPlace) {
        return template.replace("{newPlace}", newPlace);
    }

    private static String fillTemplateHostile(
            String template, NpcType type, String name) {
        return template
                .replace("{npcType}", type.name().toLowerCase())
                .replace("{name}", name);
    }

    private static String fillTemplateHostileNewPlace(
            String template, String newPlace, NpcType type, String name) {
        return template
                .replace("{newPlace}", newPlace)
                .replace("{npcType}", type.name().toLowerCase())
                .replace("{name}", name);
    }

    private static boolean hasUnreachedGoal(List<QuestGoal> quest) {
        return quest.stream().anyMatch(g -> !g.reached());
    }
}
