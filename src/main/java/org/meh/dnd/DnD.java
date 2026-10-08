package org.meh.dnd;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.Random;

import static org.meh.dnd.AvailableActionType.*;
import static org.meh.dnd.FightOutcome.*;
import static org.meh.dnd.GameMode.*;
import static org.meh.dnd.Quests.*;

public record DnD(
            GameRepository gameRepository,
            DMChannel dmChannel,
            PlayerChannel playersChannel,
            Combat combat
    ) {

    private static final int MOVE_STEP = 5;
    private static final int WEAPONS_REACH = 5;
    private static final Random COMBAT_RANDOM = new Random();

    public void doAction(Actions action) {
        Game game = gameRepository.game().orElseThrow();
        switch (action) {
            case Start s -> {
                gameRepository.save(g -> g
                        .withPlayerChar(g.playerChar()
                                .withHp(g.playerChar().maxHp())
                                .withSpellSlots(DndCombat.spellSlots(
                                        g.playerChar().charClass(),
                                        g.playerChar().level())))
                        .withDiary(List.of())
                        .withQuest(updateQuestFromExploring(g.quest(), s.place())));
                dmChannel.post(action);
            }
            case Explore e -> {
                gameRepository.save(g -> g
                        .withMode(EXPLORING)
                        .withPlace(e.place())
                        .withQuest(updateQuestFromExploring(g.quest(), e.place())));
                dmChannel.post(action);
            }
            case Attack attack -> {
                Fight fight = combat.generateFight(game.playerChar(), attack);
                CombatOutput output = new CombatOutput(
                        fight.playerTurn(),
                        fight.playerActions(),
                        fight.opponentActions(),
                        fight.opponent(),
                        List.of(),
                        false, false, fight.distance(),
                        computeActions(game.playerChar(), fight.playerActions(), fight.distance()),
                        fight.combatants(), fight.activeCombatant());
                gameRepository.save(g -> g
                        .withMode(COMBAT)
                        .withFightStatus(fight)
                        .withLastOutput(output));
                playersChannel.post(output);
                if (!fight.playerTurn()) {
                    if (fight.isMultiCombat())
                        runMultiAiTurns(fight);
                    else
                        playEnemyCombatTurn();
                }
            }
            case Rest ignored -> {
                gameRepository.save(g -> g
                        .withPlayerChar(g.playerChar()
                                .withHp(g.playerChar().maxHp())
                                .withSpellSlots(DndCombat.spellSlots(
                                        g.playerChar().charClass(),
                                        g.playerChar().level())))
                        .withMode(RESTING)
                        .withLastOutput(new RestOutput())
                        .withDialogueTarget(new Nobody()));
                playersChannel.post(new RestOutput());
            }
            case Dialogue d -> {
                gameRepository.save(g -> g
                        .withMode(DIALOGUE)
                        .withDialogueTarget(new Somebody(d.target(), d.type()))
                        .withQuest(updateQuestFromTalking(g.quest(), d.target()))
                );
                dmChannel.post(action);
            }
            case Say ignored -> {
                gameRepository.save(g -> g.withMode(DIALOGUE));
                dmChannel.post(action);
            }
            case EndDialogue ignored -> {
                gameRepository.save(g -> g
                        .withMode(EXPLORING)
                        .withDialogueTarget(new Nobody())
                );
                dmChannel.post(action);
            }
        }
    }

    record DamageWithDescription(int damage, String description,
                                 GameChar attacker, GameChar defender) {}

    public void playCombatAction(
            CombatActions action,
            boolean bonusAction
    ) {
        Game game = gameRepository.game().orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        if (fight.isMultiCombat()) {
            playMultiCombatAction(game, fight, action, bonusAction);
            return;
        }
        if (action instanceof Move move) {
            requireMovement(fight.playerActions(), move);
            int newDistance = computeDistance(move, fight);
            GameChar gameChar = game.playerChar();
            GameChar opponent = fight.opponent();
            String description =
                    gameChar.name() + ": " + "move " +
                    dirDescription(move) + opponent.name();
            AvailableActions newPlayerActions =
                    fight.playerActions().subtractSpeed(move.amount());
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.add(description);
            Fight newFight = new Fight(
                    newPlayerActions.hasActionsLeft(),
                    fight.opponent(),
                    newLog,
                    newDistance,
                    fight.outcome(),
                    newPlayerActions,
                    fight.opponentActions(),
                    fight.xp(),
                    fight.delayedEffects()
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayersFight(game, newFight);
            if (!newFight.playerTurn())
                playEnemyCombatTurn();
        } else if (action instanceof Attacks attack) {
            requireAction(fight.playerActions(), bonusAction);
            requireSpellSlot(game.playerChar(), attack);
            AttackResult result =
                    combat.computeAttack(attack, game.playerChar(), fight.opponent());
            List<DelayedEffect> newDelayedEffects = new ArrayList<>(fight.delayedEffects());
            newDelayedEffects.addAll(switch (result) {
                case Hit hit -> hit.delayedEffects();
                case Miss ignored -> List.of();
            });
            String description = DndCombat.combatActionDescription(
                    attack,
                    game.playerChar(),
                    result);
            AvailableActions newPlayerActions =
                    fight.playerActions().subtractAction(bonusAction);
            SpellSlots newSlots = switch (attack) {
                case SpellAttack sa -> game.playerChar()
                        .spellSlots()
                        .subtractAtLevel(sa.spell().level());
                case WeaponAttack ignored -> game.playerChar().spellSlots();
            };
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.add(description);
            boolean killedEnemy = result.gameChar().isDead();
            if (killedEnemy)
                newLog.add(String.format("Gained %d xp", fight.xp()));
            GameChar newPlayerChar = game.playerChar()
                    .withSpellSlots(newSlots)
                    .withXp(killedEnemy
                            ? game.playerChar().xp() + fight.xp()
                            : game.playerChar().xp());
            Fight newFight = new Fight(
                    newPlayerActions.hasActionsLeft(),
                    result.gameChar(),
                    newLog,
                    fight.distance(),
                    killedEnemy ? PLAYER_WON : IN_PROGRESS,
                    newPlayerActions,
                    fight.opponentActions(),
                    fight.xp(),
                    newDelayedEffects
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
                    .withQuest(updateQuestFromFight(game.quest(), newFight))
                    .withPlayerChar(newPlayerChar)
            );
            notifyPlayersFight(game, newFight);
            if (!newFight.playerTurn())
                playEnemyCombatTurn();
        } else if (action instanceof EndTurn) {
            List<DamageWithDescription> damages = fight.delayedEffects().stream()
                    .map(e -> updatePlayerAndOpponent(game, fight, e))
                    .filter(e -> e.inTurns() == 0)
                    .map(e -> {
                        int amount = DndCombat.applyDamage(e.damageRolls(),
                                e.attacker(), e.defender());
                        String desc = DndCombat.combatActionDescription(
                                e.attack(),
                                e.attacker(),
                                new Hit(e.defender().damage(amount), amount, List.of())
                        );
                        return new DamageWithDescription(amount, desc,
                                e.attacker(), e.defender());
                    })
                    .toList();
            int playerCharDamage = damages.stream()
                    .filter(e -> e.defender().name().equals(game.playerChar().name()))
                    .mapToInt(DamageWithDescription::damage)
                    .sum();
            int opponentCharDamage = damages.stream()
                    .filter(e -> e.defender().name().equals(fight.opponent().name()))
                    .mapToInt(DamageWithDescription::damage)
                    .sum();
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.addAll(damages.stream().map(DamageWithDescription::description).toList());
            GameChar newOpponent = fight.opponent().damage(opponentCharDamage);
            if (newOpponent.isDead())
                newLog.add(String.format("Gained %d xp", fight.xp()));
            GameChar newPlayerChar = game.playerChar()
                    .damage(playerCharDamage)
                    .withXp(newOpponent.isDead()
                            ? game.playerChar().xp() + fight.xp()
                            : game.playerChar().xp());
            Fight newFight = new Fight(
                    false,
                    newOpponent,
                    newLog,
                    fight.distance(),
                    newOpponent.isDead()? PLAYER_WON :
                            newPlayerChar.isDead()? ENEMY_WON : IN_PROGRESS,
                    fight.playerActions(),
                    fight.opponent().availableActions(),
                    fight.xp(),
                    fight.delayedEffects().stream()
                            .filter(e -> e.inTurns() > 0)
                            .map(e -> new DelayedEffect(
                                    e.inTurns() - 1,
                                    e.damageRolls(),
                                    e.attacker(),
                                    e.defender(),
                                    e.attack()))
                            .toList()
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withPlayerChar(newPlayerChar));
            notifyPlayersFight(game, newFight);
            playEnemyCombatTurn();
        }
    }

    private void playMultiCombatAction(Game game, Fight fight,
                                       CombatActions action, boolean bonusAction) {
        if (!fight.activeCombatant().equals("player"))
            throw new IllegalStateException("It is not the player's turn");
        List<Combatant> combatants = new ArrayList<>(fight.combatants());
        Combatant player = combatants.stream().filter(c -> c.id().equals("player"))
                .findFirst().orElseThrow();
        List<String> log = new ArrayList<>(fight.log());
        if (action instanceof Attacks attack) {
            requireAction(player.actions(), bonusAction);
            requireSpellSlot(player.character(), attack);
            String targetId = attack instanceof WeaponAttack wa ? wa.target()
                    : ((SpellAttack) attack).target();
            Combatant target = combatants.stream().filter(c -> c.id().equals(targetId))
                    .filter(c -> !c.ally() && !c.character().isDead())
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid combat target"));
            AttackResult result = combat.computeAttack(attack, player.character(), target.character());
            combatants = replace(combatants, target.withCharacter(result.gameChar()));
            GameChar updatedPlayer = player.character();
            if (attack instanceof SpellAttack spellAttack)
                updatedPlayer = updatedPlayer.withSpellSlots(
                        updatedPlayer.spellSlots().subtractAtLevel(spellAttack.spell().level()));
            combatants = replace(combatants, player.withCharacter(updatedPlayer)
                    .withActions(player.actions().subtractAction(bonusAction)));
            List<DelayedEffect> delayedEffects = new ArrayList<>(fight.delayedEffects());
            if (result instanceof Hit hit)
                delayedEffects.addAll(hit.delayedEffects());
            fight = withDelayedEffects(fight, delayedEffects);
            log.add(DndCombat.combatActionDescription(attack, player.character(), result));
        } else if (action instanceof Move move) {
            requireMovement(player.actions(), move);
            int direction = move.dir() == Dir.TOWARDS_ENEMY ? 1 : -1;
            combatants = replace(combatants, player.withPosition(Math.max(0,
                    player.position() + direction * move.amount()))
                    .withActions(player.actions().subtractSpeed(move.amount())));
            log.add(movementDescription(player.character(), move,
                    nearestOpponent(combatants, player).character().name()));
        } else if (action instanceof EndTurn) {
            combatants = replace(combatants, player.withActions(new AvailableActions(0, 0, 0)));
        }
        DelayedResolution delayed = action instanceof EndTurn
                ? resolveMultiDelayedEffects(combatants, fight.delayedEffects(), log)
                : new DelayedResolution(combatants, fight.delayedEffects());
        combatants = delayed.combatants();
        Fight next = advanceMultiFight(fight, combatants, log);
        next = withDelayedEffects(next, delayed.effects());
        saveAndNotifyMulti(game, next);
        runMultiAiTurns(next);
    }

    private void runMultiAiTurns(Fight initial) {
        Fight fight = initial;
        while (fight.outcome() == IN_PROGRESS && !fight.activeCombatant().equals("player")) {
            Combatant actor = combatant(fight, fight.activeCombatant());
            List<Combatant> opponents = fight.combatants().stream()
                    .filter(c -> c.ally() != actor.ally() && !c.character().isDead()).toList();
            if (opponents.isEmpty()) break;
            CombatActions action = randomNpcAction(actor, opponents);
            Game game = gameRepository.game().orElseThrow();
            List<Combatant> combatants = new ArrayList<>(fight.combatants());
            List<String> log = new ArrayList<>(fight.log());
            // The action is generated from a snapshot. If the actor has no main
            // action by execution time, safely finish its turn instead of
            // allowing AvailableActions to reach a negative value.
            action = normalizeAiAction(action, actor.actions());
            if (action instanceof Attacks attack) {
                Combatant target = opponents.get(COMBAT_RANDOM.nextInt(opponents.size()));
                AttackResult result = combat.computeAttack(attack, actor.character(), target.character());
                combatants = replace(combatants, target.withCharacter(result.gameChar()));
                log.add(DndCombat.combatActionDescription(attack, actor.character(), result));
                GameChar updatedActor = actor.character();
                if (attack instanceof SpellAttack spellAttack &&
                        updatedActor.spellSlots().hasSlotsAtLevel(spellAttack.spell().level()))
                    updatedActor = updatedActor.withSpellSlots(
                            updatedActor.spellSlots().subtractAtLevel(spellAttack.spell().level()));
                combatants = replace(combatants, actor.withCharacter(updatedActor)
                        .withActions(actor.actions().subtractAction(false)));
                if (result instanceof Hit hit)
                    fight = withDelayedEffects(fight,
                            append(fight.delayedEffects(), hit.delayedEffects()));
            } else if (action instanceof Move move) {
                int direction = move.dir() == Dir.TOWARDS_ENEMY ? -1 : 1;
                combatants = replace(combatants, actor.withPosition(Math.max(0,
                        actor.position() + direction * move.amount()))
                        .withActions(actor.actions().subtractSpeed(move.amount())));
                log.add(movementDescription(actor.character(), move,
                        opponents.getFirst().character().name()));
            } else {
                combatants = replace(combatants, actor.withActions(new AvailableActions(0, 0, 0)));
            }
            DelayedResolution delayed = action instanceof EndTurn
                    ? resolveMultiDelayedEffects(combatants, fight.delayedEffects(), log)
                    : new DelayedResolution(combatants, fight.delayedEffects());
            combatants = delayed.combatants();
            fight = advanceMultiFight(fight, combatants, log);
            fight = withDelayedEffects(fight, delayed.effects());
            saveAndNotifyMulti(game, fight);
        }
    }

    private CombatActions randomNpcAction(Combatant actor, List<Combatant> opponents) {
        List<CombatActions> actions = new ArrayList<>();
        if (actor.actions().actions() > 0) {
            actor.character().weapons().forEach(w ->
                    actions.add(new WeaponAttack(w, opponents.getFirst().id())));
            actor.character().spells().stream()
                    .filter(s -> actor.character().spellSlots().hasSlotsAtLevel(s.level()))
                    .forEach(s -> actions.add(new SpellAttack(s, opponents.getFirst().id())));
        }
        if (actor.actions().remainingSpeed() >= MOVE_STEP)
            actions.add(new Move(Dir.TOWARDS_ENEMY, MOVE_STEP));
        actions.add(new EndTurn());
        return actions.get(COMBAT_RANDOM.nextInt(actions.size()));
    }

    static CombatActions normalizeAiAction(CombatActions action, AvailableActions available) {
        return action instanceof Attacks && available.actions() == 0
                ? new EndTurn()
                : action;
    }

    private static List<DelayedEffect> append(List<DelayedEffect> existing,
                                               List<DelayedEffect> additions) {
        List<DelayedEffect> result = new ArrayList<>(existing);
        result.addAll(additions);
        return result;
    }

    private static Fight withDelayedEffects(Fight fight, List<DelayedEffect> effects) {
        return new Fight(fight.playerTurn(), fight.opponent(), fight.log(), fight.distance(),
                fight.outcome(), fight.playerActions(), fight.opponentActions(), fight.xp(),
                effects, fight.combatants(), fight.activeCombatant());
    }

    private static void requireAction(AvailableActions actions, boolean bonusAction) {
        if ((bonusAction && actions.bonusActions() == 0) ||
                (!bonusAction && actions.actions() == 0))
            throw new IllegalStateException("No action available");
    }

    private static void requireMovement(AvailableActions actions, Move move) {
        if (move.amount() <= 0 || move.amount() > actions.remainingSpeed())
            throw new IllegalArgumentException("Movement exceeds remaining speed");
    }

    private static void requireSpellSlot(GameChar character, Attacks attack) {
        if (attack instanceof SpellAttack spell &&
                !character.spellSlots().hasSlotsAtLevel(spell.spell().level()))
            throw new IllegalStateException("No spell slot available");
    }

    private record DelayedResolution(List<Combatant> combatants,
                                     List<DelayedEffect> effects) {}

    private static DelayedResolution resolveMultiDelayedEffects(
            List<Combatant> initial,
            List<DelayedEffect> effects,
            List<String> log
    ) {
        List<Combatant> combatants = new ArrayList<>(initial);
        List<DelayedEffect> remaining = new ArrayList<>();
        for (DelayedEffect effect : effects) {
            if (effect.inTurns() > 0) {
                remaining.add(new DelayedEffect(effect.inTurns() - 1,
                        effect.damageRolls(), effect.attacker(), effect.defender(), effect.attack()));
                continue;
            }
            Combatant defender = combatants.stream()
                    .filter(c -> c.character().name().equals(effect.defender().name()))
                    .findFirst().orElse(null);
            if (defender == null)
                continue;
            int damage = DndCombat.applyDamage(effect.damageRolls(), effect.attacker(), defender.character());
            GameChar damaged = defender.character().damage(damage);
            combatants = replace(combatants, defender.withCharacter(damaged));
            log.add(DndCombat.combatActionDescription(effect.attack(), effect.attacker(),
                    new Hit(damaged, damage, List.of())));
        }
        return new DelayedResolution(combatants, remaining);
    }

    private Fight advanceMultiFight(Fight old, List<Combatant> combatants, List<String> log) {
        boolean enemiesAlive = combatants.stream().anyMatch(c -> !c.ally() && !c.character().isDead());
        boolean partyAlive = combatants.stream().anyMatch(c -> c.ally() && !c.character().isDead());
        FightOutcome outcome = enemiesAlive ? (partyAlive ? IN_PROGRESS : ENEMY_WON) : PLAYER_WON;
        if (outcome != IN_PROGRESS)
            return new Fight(true, firstEnemy(combatants).character(), log, 5, outcome,
                    player(combatants).actions(), firstEnemy(combatants).actions(),
                    old.xp(), old.delayedEffects(), combatants, "player");
        Combatant current = combatants.stream()
                .filter(c -> c.id().equals(old.activeCombatant()))
                .findFirst().orElseThrow();
        if (current.actions().hasActionsLeft())
            return new Fight(current.id().equals("player"), firstEnemy(combatants).character(), log,
                    5, IN_PROGRESS, player(combatants).actions(), firstEnemy(combatants).actions(),
                    old.xp(), old.delayedEffects(), combatants, current.id());
        List<Combatant> living = combatants.stream().filter(c -> !c.character().isDead()).toList();
        int currentIndex = living.stream().map(Combatant::id).toList().indexOf(old.activeCombatant());
        Combatant next = living.get((currentIndex + 1) % living.size());
        if (next.id().equals("player")) {
            Game game = gameRepository.game().orElseThrow();
            combatants = combatants.stream()
                    .map(c -> c.character().isDead()
                            ? c
                            : c.withActions(c.id().equals("player")
                                    ? game.playerChar().availableActions()
                                    : c.character().availableActions()))
                    .toList();
            next = player(combatants);
        }
        return new Fight(next.id().equals("player"), firstEnemy(combatants).character(), log, 5, IN_PROGRESS,
                player(combatants).actions(), firstEnemy(combatants).actions(), old.xp(),
                old.delayedEffects(), combatants, next.id());
    }

    private void saveAndNotifyMulti(Game game, Fight fight) {
        List<QuestGoal> quest = game.quest();
        for (Combatant enemy : fight.combatants()) {
            if (!enemy.ally() && enemy.character().isDead()) {
                quest = updateQuestFromFight(quest, new Fight(true, enemy.character(),
                        List.of(), 5, PLAYER_WON, enemy.actions(), enemy.actions(), 0, List.of()));
            }
        }
        int gainedXp = (fight.outcome() == PLAYER_WON)
                ? fight.combatants().stream()
                        .filter(c -> !c.ally() && c.character().isDead())
                        .mapToInt(c -> DndCombat.XP_GAIN).sum()
                : 0;
        GameChar updatedPlayer = player(fight.combatants()).character()
                .withXp(player(fight.combatants()).character().xp() + gainedXp);
        List<Combatant> withXp = replace(fight.combatants(),
                player(fight.combatants()).withCharacter(updatedPlayer));
        fight = new Fight(fight.playerTurn(), firstEnemy(withXp).character(), fight.log(),
                fight.distance(), fight.outcome(), player(withXp).actions(),
                firstEnemy(withXp).actions(), fight.xp(), fight.delayedEffects(),
                withXp, fight.activeCombatant());
        List<QuestGoal> updatedQuest = quest;
        Fight savedFight = fight;
        gameRepository.save(g -> g.withFightStatus(savedFight)
                .withPlayerChar(updatedPlayer)
                .withQuest(updatedQuest)
                .withMode(savedFight.outcome() == IN_PROGRESS ? COMBAT : EXPLORING));
        notifyPlayersMulti(fight);
    }

    private void notifyPlayersMulti(Fight fight) {
        Combatant player = player(fight.combatants());
        playersChannel.post(new CombatOutput(
                fight.activeCombatant().equals("player"), player.actions(),
                firstEnemy(fight.combatants()).actions(), firstEnemy(fight.combatants()).character(),
                fight.log(), fight.outcome() == PLAYER_WON, fight.outcome() == ENEMY_WON,
                5, computeActions(player.character(), player.actions(), 5),
                fight.combatants(), fight.activeCombatant()));
    }

    private static Combatant combatant(Fight fight, String id) {
        return fight.combatants().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static Combatant player(List<Combatant> combatants) {
        return combatants.stream().filter(c -> c.id().equals("player")).findFirst().orElseThrow();
    }

    private static Combatant firstEnemy(List<Combatant> combatants) {
        return combatants.stream().filter(c -> !c.ally()).findFirst().orElseThrow();
    }

    private static List<Combatant> replace(List<Combatant> all, Combatant replacement) {
        return all.stream().map(c -> c.id().equals(replacement.id()) ? replacement : c).toList();
    }

    private void playEnemyCombatTurn() {
        Game game = gameRepository.game().orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        while (fight.outcome() == IN_PROGRESS &&
                fight.opponentActions().hasActionsLeft() &&
                !fight.playerTurn()
        ) {
            GeneratedCombatAction ga = combat.generateAttack(fight);
            enemyCombatAction(ga);
            game = gameRepository.game().orElseThrow();
            fight = (Fight) game.combatStatus();
        }
    }

    private void enemyCombatAction(GeneratedCombatAction ga) {
        Game game = gameRepository.game().orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        if (ga.action() instanceof Move move) {
            int newDistance = computeDistance(move, fight);
            GameChar gameChar = fight.opponent();
            GameChar opponent = game.playerChar();
            String description = gameChar.name() + ": " + "move " +
                    dirDescription(move) + opponent.name();
            AvailableActions newOpponentActions =
                    fight.opponentActions().subtractSpeed(move.amount());
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.add(description);
            Fight newFight = new Fight(
                    !newOpponentActions.hasActionsLeft(),
                    fight.opponent(),
                    newLog,
                    newDistance,
                    fight.outcome(),
                    fight.playerActions(),
                    newOpponentActions,
                    fight.xp(),
                    fight.delayedEffects()
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayersFight(game, newFight);
        } else if (ga.action() instanceof Attacks attack) {
            AttackResult result =
                    combat.computeAttack(attack, fight.opponent(), game.playerChar());
            List<DelayedEffect> newDelayedEffects = new ArrayList<>(fight.delayedEffects());
            newDelayedEffects.addAll(switch (result) {
                case Hit hit -> hit.delayedEffects();
                case Miss ignored -> List.of();
            });
            String description = DndCombat.combatActionDescription(
                    attack,
                    fight.opponent(),
                    result);
            AvailableActions newOpponentActions =
                    fight.opponentActions().subtractAction(ga.bonusAction());
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.add(description);
            Fight newFight = new Fight(
                    !newOpponentActions.hasActionsLeft(),
                    fight.opponent(),
                    newLog,
                    fight.distance(),
                    result.gameChar().isDead()? ENEMY_WON : IN_PROGRESS,
                    fight.playerActions(),
                    newOpponentActions,
                    fight.xp(),
                    newDelayedEffects
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withPlayerChar(result.gameChar())
                    .withMode(result.gameChar().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayersFight(game, newFight);
        } else if (ga.action() instanceof EndTurn) {
            List<DamageWithDescription> damages = fight.delayedEffects().stream()
                    .map(e -> updatePlayerAndOpponent(game, fight, e))
                    .filter(e -> e.inTurns() == 0)
                    .map(e -> {
                        int amount = DndCombat.applyDamage(e.damageRolls(),
                                e.attacker(), e.defender());
                        String desc = DndCombat.combatActionDescription(
                                e.attack(),
                                e.attacker(),
                                new Hit(e.defender().damage(amount), amount, List.of())
                        );
                        return new DamageWithDescription(amount, desc,
                                e.attacker(), e.defender());
                    })
                    .toList();
            int playerCharDamage = damages.stream()
                    .filter(e -> e.defender().name().equals(game.playerChar().name()))
                    .mapToInt(DamageWithDescription::damage)
                    .sum();
            int opponentCharDamage = damages.stream()
                    .filter(e -> e.defender().name().equals(fight.opponent().name()))
                    .mapToInt(DamageWithDescription::damage)
                    .sum();
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.addAll(damages.stream().map(DamageWithDescription::description).toList());
            GameChar newOpponent = fight.opponent().damage(opponentCharDamage);
            if (newOpponent.isDead())
                newLog.add(String.format("Gained %d xp", fight.xp()));
            GameChar newPlayerChar = game.playerChar()
                    .damage(playerCharDamage)
                    .withXp(newOpponent.isDead()
                            ? game.playerChar().xp() + fight.xp()
                            : game.playerChar().xp());
            Fight newFight = new Fight(
                    true,
                    newOpponent,
                    newLog,
                    fight.distance(),
                    newOpponent.isDead()? PLAYER_WON :
                            newPlayerChar.isDead()? ENEMY_WON : IN_PROGRESS,
                    game.playerChar().availableActions(),
                    fight.opponentActions(),
                    fight.xp(),
                    fight.delayedEffects().stream()
                            .filter(e -> e.inTurns() > 0)
                            .map(e -> new DelayedEffect(
                                    e.inTurns() - 1,
                                    e.damageRolls(),
                                    e.attacker(),
                                    e.defender(),
                                    e.attack()))
                            .toList()
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withPlayerChar(newPlayerChar)
            );
            notifyPlayersFight(game, newFight);
        }
    }

    private DelayedEffect updatePlayerAndOpponent(
            Game game,
            Fight fight,
            DelayedEffect e
    ) {
        return e.defender().name().equals(game.playerChar().name())
                ? new DelayedEffect(e.inTurns(), e.damageRolls(),
                fight.opponent(), game.playerChar(), e.attack())
                : new DelayedEffect(e.inTurns(), e.damageRolls(),
                game.playerChar(), fight.opponent(), e.attack());
    }

    private static int computeDistance(
            Move move,
            Fight fight
    ) {
        return switch (move.dir()) {
            case TOWARDS_ENEMY -> Math.max(fight.distance() - move.amount(), 0);
            case AWAY_FROM_ENEMY -> fight.distance() + move.amount();
        };
    }

    private void notifyPlayersFight(
            Game game,
            Fight fight
    ) {
        CombatOutput output = new CombatOutput(
                fight.playerTurn(),
                fight.playerActions(),
                fight.opponentActions(),
                fight.opponent(),
                fight.log(),
                fight.outcome() == PLAYER_WON,
                fight.outcome() == ENEMY_WON,
                fight.distance(),
                computeActions(game.playerChar(), fight.playerActions(), fight.distance())
        );
        gameRepository.save(g -> g.withLastOutput(output));
        playersChannel.post(output);
    }

    private static String dirDescription(Move m) {
        return m.amount() + switch (m.dir()) {
            case TOWARDS_ENEMY -> " feet towards ";
            case AWAY_FROM_ENEMY -> " feet away from ";
        };
    }

    private static String movementDescription(GameChar actor, Move move, String target) {
        return actor.name() + ": move " + dirDescription(move) + target;
    }

    private static Combatant nearestOpponent(List<Combatant> combatants, Combatant actor) {
        return combatants.stream()
                .filter(c -> c.ally() != actor.ally() && !c.character().isDead())
                .min(java.util.Comparator.comparingInt(c ->
                        Math.abs(c.position() - actor.position())))
                .orElseThrow();
    }

    public Optional<PlayerOutput> enter() {
        return gameRepository.game().map(g -> g.events().getLast());
    }

    private List<AvailableAction> computeActions(
            GameChar gc,
            AvailableActions av,
            int distance
    ) {
        Stream<AvailableAction> stdActions = av.actions() > 0
                ? gc.weapons().stream()
                .filter(w -> distance <= WEAPONS_REACH || w.ranged())
                .map(w -> new AvailableAction(WEAPON, w.name(), false))
                : Stream.of();
        Stream<AvailableAction> bonusWeapons = av.bonusActions() > 0
                ? gc.weapons().stream()
                .filter(Weapon::light)
                .filter(w -> distance <= WEAPONS_REACH || w.ranged())
                .map(w -> new AvailableAction(WEAPON, w.name(), true))
                : Stream.of();
        Stream<AvailableAction> spells = av.actions() > 0
                ? gc.spells().stream()
                    .filter(s -> distance <= WEAPONS_REACH || s.ranged())
                    .filter(s -> gc.spellSlots().hasSlotsAtLevel(s.level()))
                    .map(s -> new AvailableAction(SPELL, s.name(), false))
                : Stream.of();
        int moveAmount = Math.min(av.remainingSpeed(), MOVE_STEP);
        Stream<AvailableAction> move = moveAmount > 0?
                Stream.of(new AvailableAction(MOVE, Integer.toString(moveAmount), false))
                : Stream.of();
        return Stream.concat(Stream.concat(stdActions,
                Stream.concat(bonusWeapons,
                        Stream.concat(spells, move))),
                Stream.of(new AvailableAction(END_TURN, "", false))).toList();
    }
}
