package org.meh.dnd;

import java.util.List;
import java.util.Random;

import static org.meh.dnd.FightStatus.IN_PROGRESS;

public record Combat()
{
    public static Fight generateFight(String opponentName) {
        boolean playersTurn = new Random().nextBoolean();
        GameChar opponent = generateMonster(opponentName);
        return new Fight(playersTurn, opponent, "", 5, IN_PROGRESS);
    }

    private static GameChar generateMonster(
            String opponentName
    ) {
        if (new Random().nextBoolean())
            return new GameChar(
                    opponentName, 10, 10,
                    List.of(new Weapon("sword")),
                    List.of(new Spell("Magic Missile")));
        else
            return new GameChar(
                    opponentName, 10, 10,
                    List.of(new Weapon("sword")),
                    List.of());
    }

    public static CombatActions generateAttack(Fight fight) {
        GameChar monster = fight.opponent();
        if (fight.distance() <= 5)
            return new MeleeAttack(monster.weapons().getFirst().name());
        else if (!monster.spells().isEmpty())
            return new SpellAttack(monster.spells().getFirst().name());
        else
            return new Move(Dir.TOWARDS_ENEMY, 5);
    }
}
