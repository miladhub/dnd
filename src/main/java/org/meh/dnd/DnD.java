package org.meh.dnd;

import java.util.List;

import static org.meh.dnd.DndCombat.*;
import static org.meh.dnd.FightStatus.*;
import static org.meh.dnd.GameMode.*;
import static org.meh.dnd.GameMode.EXPLORING;

public record DnD(
            GameRepository gameRepository,
            DMChannel dmChannel,
            PlayerChannel playersChannel,
            Combat combat
    ) {
    public void doAction(
            String gameId,
            Actions action
    ) {
        PlayerInput input = new PlayerInput(action);
        if (action instanceof Start) {
            loadInitialGame(gameId);
            dmChannel.post(gameId, input);
        }
        else if (action instanceof Explore) {
            gameRepository.save(gameId, g -> g.withMode(EXPLORING));
            dmChannel.post(gameId, input);
        }
        else if (action instanceof Attack attack) {
            Fight fight = combat.generateFight(attack.target());
            CombatOutput output = new CombatOutput(
                    fight.playerTurn(),
                    fight.opponent(),
                    "",
                    false, false, fight.distance());
            gameRepository.save(gameId, g -> g
                    .withMode(COMBAT)
                    .withFightStatus(fight)
                    .withLastOutput(output));
            playersChannel.post(gameId, output);
            if (!fight.playerTurn())
                enemyCombatTurn(gameId, combat.generateAttack(fight));
        }
        else if (action instanceof Rest) {
            gameRepository.save(gameId, g -> g
                    .withPlayerChar(g.playerChar().withHp(g.playerChar().maxHp()))
                    .withMode(RESTING));
            playersChannel.post(gameId, new RestOutput());
        }
        else if (action instanceof Dialogue || action instanceof Say) {
            gameRepository.save(gameId, g -> g.withMode(DIALOGUE));
            dmChannel.post(gameId, input);
        }
        else if (action instanceof EndDialogue) {
            gameRepository.save(gameId, g -> g.withMode(EXPLORING));
            dmChannel.post(gameId, input);
        }
    }

    public void playCombatTurn(
            String gameId,
            CombatActions action
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        if (action instanceof Move move) {
            int newDistance = computeDistance(move, fight);
            GameChar gameChar = game.playerChar();
            GameChar opponent = fight.opponent();
            String description =
                    gameChar.name() + ": " + "move " +
                    dirDescription(move) + opponent.name();
            Fight newFight = new Fight(
                    !fight.playerTurn(),
                    fight.opponent(),
                    description,
                    newDistance,
                    fight.outcome()
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(gameId, newFight);
        } else if (action instanceof Attacks attack) {
            AttackResult result =
                    combat.computeAttack(attack, game.playerChar(), fight.opponent());
            String description = combatActionDescription(
                    attack,
                    game.playerChar(),
                    result);
            Fight newFight = new Fight(
                    !fight.playerTurn(),
                    result.gameChar(),
                    description,
                    fight.distance(),
                    result.gameChar().isDead() ? PLAYER_WON : IN_PROGRESS
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(gameId, newFight);
        }
    }

    public void enemyCombatTurn(
            String gameId,
            CombatActions action
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        if (action instanceof Move move) {
            int newDistance = computeDistance(move, fight);
            GameChar gameChar = fight.opponent();
            GameChar opponent = game.playerChar();
            String description = gameChar.name() + ": " + "move " +
                    dirDescription(move) + opponent.name();
            Fight newFight = new Fight(
                    !fight.playerTurn(),
                    fight.opponent(),
                    description,
                    newDistance,
                    fight.outcome()
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(gameId, newFight);
        } else if (action instanceof Attacks attack) {
            AttackResult result =
                    combat.computeAttack(attack, fight.opponent(), game.playerChar());
            String description = combatActionDescription(
                    attack,
                    fight.opponent(),
                    result);
            Fight newFight = new Fight(
                    !fight.playerTurn(),
                    fight.opponent(),
                    description,
                    fight.distance(),
                    result.gameChar().isDead()? ENEMY_WON : IN_PROGRESS
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withPlayerChar(result.gameChar())
                    .withMode(result.gameChar().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(gameId, newFight);
        }
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

    private void notifyPlayers(
            String gameId,
            Fight fight
    ) {
        CombatOutput output = new CombatOutput(
                fight.playerTurn(),
                fight.opponent(),
                fight.lastAction(),
                fight.outcome() == PLAYER_WON,
                fight.outcome() == ENEMY_WON,
                fight.distance()
        );
        gameRepository.save(gameId, g -> g.withLastOutput(output));
        playersChannel.post(gameId, output);
    }

    private static String combatActionDescription(
            Attacks attack,
            GameChar attacker,
            AttackResult result
    ) {
        GameChar opponent = result.gameChar();
        String dmg = " (" + result.damage() + " hp damage)";
        String attackDescription = switch (attack) {
            case WeaponAttack m -> "melee attack with " + m.weapon() + dmg;
            case SpellAttack s -> "cast " + s.spell() + dmg;
        };
        if (opponent.isDead()) {
            return attacker.name() + ": killed " + opponent.name() + ", " +
                    attackDescription;
        }
        else {
            return attacker.name() + ": " + attackDescription;
        }
    }

    private static String dirDescription(Move m) {
        return m.amount() + switch (m.dir()) {
            case TOWARDS_ENEMY -> " feet towards ";
            case AWAY_FROM_ENEMY -> " feet away from ";
        };
    }

    public PlayerOutput enter(
            String gameId
    ) {
        return gameRepository.gameById(gameId).map(Game::lastOutput).orElseThrow();
    }

    public void loadInitialGame(String gameId) {
        Game game = new Game(
                gameId,
                GameMode.EXPLORING,
                new ExploreOutput(
                        "Ready.",
                        List.of(new Start())),
                new GameChar(
                        "Duncan",
                        4,
                        CharClass.WIZARD,
                        10,
                        10,
                        15,
                        1500,
                        2000,
                        STATS_WIZARD,
                        List.of(DAGGER),
                        List.of(MAGIC_MISSILE, FIRE_BOLT, SHOCKING_GRASP)
                ),
                new Peace(),
                new Chat(List.of()),
                """
                Duncan is a wizard that is exploring a dark forest full of dungeons
                and creatures, both good and evil. He is looking for adventure.
                """
        );
        gameRepository.save(game);
    }
}
