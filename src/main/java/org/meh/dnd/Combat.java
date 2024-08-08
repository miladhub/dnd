package org.meh.dnd;

import java.util.List;
import java.util.Random;

import static org.meh.dnd.FightStatus.IN_PROGRESS;

public record Combat()
{
    public static Fight generateFight(String opponentName) {
        boolean playersTurn = new Random().nextBoolean();
        GameChar opponent = new GameChar(
                opponentName, 10, 10,
                List.of(new Weapon("sword")),
                List.of());
        return new Fight(playersTurn, opponent, "", 5, IN_PROGRESS);
    }

    public static CombatActions generateAttack(GameChar monster) {
        return new MeleeAttack(monster.weapons().getFirst().name());
    }
}
