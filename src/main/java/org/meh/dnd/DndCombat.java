package org.meh.dnd;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.meh.dnd.FightStatus.IN_PROGRESS;

public class DndCombat implements Combat
{
    public static final Weapon SWORD = new Weapon("sword", false, 8);
    public static final Weapon BOW = new Weapon("bow", true, 6);
    public static final Spell MAGIC_MISSILE = new Spell("Magic Missile", true, 8);
    public static final Spell SHOCKING_GRASP = new Spell("Shocking Grasp", false, 8);

    private static final List<Weapon> WEAPONS = List.of(
            SWORD,
            BOW
    );
    private static final List<Spell> SPELLS = List.of(
            MAGIC_MISSILE,
            SHOCKING_GRASP
    );
    private static final List<CharTemplate> TEMPLATES = List.of(
            new CharTemplate(10,
                    List.of(SWORD),
                    List.of(MAGIC_MISSILE)),
            new CharTemplate(10,
                    List.of(BOW),
                    List.of(SHOCKING_GRASP))
    );

    @Override
    public Fight generateFight(String opponentName) {
        boolean playersTurn = new Random().nextBoolean();
        GameChar opponent = generateMonster(opponentName);
        return new Fight(playersTurn, opponent, "", 5, IN_PROGRESS);
    }

    @Override
    public CombatActions generateAttack(Fight fight) {
        GameChar monster = fight.opponent();
        Optional<CombatActions> melee = pickMelee(monster).map(x -> x);
        Optional<CombatActions> ranged = pickRanged(monster).map(x -> x);
        if (fight.distance() <= 5)
            return melee.orElseGet(() -> new Move(Dir.AWAY_FROM_ENEMY, 5));
        else
            return ranged.orElseGet(() -> new Move(Dir.TOWARDS_ENEMY, 5));
    }

    private Optional<Attacks> pickMelee(GameChar monster) {
        Optional<Attacks> weapon = monster.weapons().stream()
                .filter(w -> !w.ranged())
                .map(Weapon::name)
                .map(WeaponAttack::new)
                .map(x -> (Attacks) x)
                .findFirst();
        Optional<Attacks> spell = monster.spells().stream()
                .filter(s -> !s.ranged())
                .map(Spell::name)
                .map(SpellAttack::new)
                .map(x -> (Attacks) x)
                .findFirst();
        return weapon.or(() -> spell);
    }

    private Optional<Attacks> pickRanged(GameChar monster) {
        Optional<Attacks> weapon = monster.weapons().stream()
                .filter(Weapon::ranged)
                .map(Weapon::name)
                .map(WeaponAttack::new)
                .map(x -> (Attacks) x)
                .findFirst();
        Optional<Attacks> spell = monster.spells().stream()
                .filter(Spell::ranged)
                .map(Spell::name)
                .map(SpellAttack::new)
                .map(x -> (Attacks) x)
                .findFirst();
        return weapon.or(() -> spell);
    }

    @Override
    public AttackResult computeAttack(
            Attacks attack,
            GameChar attacker,
            GameChar defender
    ) {
        int maxDmg = switch (attack) {
            case WeaponAttack m -> WEAPONS.stream()
                    .filter(w -> w.name().equals(m.weapon()))
                    .map(Weapon::damage)
                    .findFirst().orElseThrow();
            case SpellAttack ss -> SPELLS.stream()
                    .filter(s -> s.name().equals(ss.spell()))
                    .map(Spell::damage)
                    .findFirst().orElseThrow();
        };
        int damage = new Random().nextInt(maxDmg) + 1;
        return new AttackResult(defender.damage(damage), damage);
    }

    private static GameChar generateMonster(String name) {
        int templateIndex = new Random().nextInt(TEMPLATES.size());
        CharTemplate template = TEMPLATES.get(templateIndex);
        return new GameChar(
                name,
                template.maxHp(),
                template.maxHp(),
                template.weapons(),
                template.spells()
        );
    }
}
