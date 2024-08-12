package org.meh.dnd;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.meh.dnd.CharClass.*;
import static org.meh.dnd.FightOutcome.IN_PROGRESS;

public class DndCombat implements Combat
{
    public static final AvailableActions STANDARD_ACTIONS = new AvailableActions(1, 1, 30);
    public static final Stats STATS_FIGHTER = new Stats(15, 13, 14, 8, 12, 10);
    public static final Stats STATS_WIZARD = new Stats(8, 12, 14, 15, 13, 10);
    public static final Weapon SWORD = new Weapon("sword", false, 8, false, true);
    public static final Weapon BATTLEAXE = new Weapon("battleaxe", false, 10, true, false);
    public static final Weapon BOW = new Weapon("bow", true, 6, true, false);
    public static final Weapon UNARMED = new Weapon("unarmed", false, 4, false, true);
    public static final Weapon DAGGER = new Weapon("dagger", false, 6, false, true);
    public static final Spell MAGIC_MISSILE = new Spell("Magic Missile", true, 8);
    public static final Spell SHOCKING_GRASP = new Spell("Shocking Grasp", false, 8);
    public static final Spell FIRE_BOLT = new Spell("Fire Bolt", true, 10);

    private static final List<Weapon> WEAPONS = List.of(
            SWORD,
            BATTLEAXE,
            UNARMED,
            DAGGER,
            BOW
    );
    private static final List<Spell> SPELLS = List.of(
            SHOCKING_GRASP,
            FIRE_BOLT,
            MAGIC_MISSILE
    );
    public static final List<Weapon> FIGHTER_WEAPONS = List.of(SWORD, DAGGER, BOW);
    public static final List<Weapon> WIZARD_WEAPONS = List.of(BOW, DAGGER);
    public static final List<Spell> WIZARD_SPELLS = List.of(MAGIC_MISSILE, SHOCKING_GRASP, FIRE_BOLT);
    public static final CharTemplate WARRIOR_TEMPLATE = new CharTemplate(
            10,
            13,
            3,
            FIGHTER,
            STATS_FIGHTER,
            FIGHTER_WEAPONS,
            List.of(),
            STANDARD_ACTIONS);
    public static final CharTemplate WIZARD_TEMPLATE = new CharTemplate(
            10,
            13,
            3,
            WIZARD,
            STATS_WIZARD,
            WIZARD_WEAPONS,
            WIZARD_SPELLS,
            STANDARD_ACTIONS);
    public static final CharTemplate BEAST_TEMPLATE = new CharTemplate(
            10,
            13,
            3,
            FIGHTER,
            STATS_FIGHTER,
            List.of(UNARMED),
            List.of(),
            STANDARD_ACTIONS);

    public static String combatActionDescription(
            Attacks attack,
            GameChar attacker,
            AttackResult result
    ) {
        GameChar opponent = result.gameChar();
        String dmg = " (" + result.damage() + " hp damage)";
        String attackDescription = switch (attack) {
            case WeaponAttack m ->
                    (weaponByName(m.weapon()).ranged()? "ranged" : "melee") +
                    " attack with " + m.weapon() + dmg;
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

    private static Weapon weaponByName(String name) {
        return WEAPONS.stream()
                .filter(w -> w.name().equals(name))
                .findFirst().orElseThrow();
    }

    @Override
    public Fight generateFight(
            GameChar gameChar,
            Attack attack
    ) {
        boolean playersTurn = new Random().nextBoolean();
        GameChar opponent = generateOpponent(attack.target(), attack.type());
        return new Fight(playersTurn, opponent, List.of(), 5, IN_PROGRESS,
                gameChar.availableActions(),
                opponent.availableActions());
    }

    @Override
    public GeneratedCombatAction generateAttack(Fight fight) {
        GameChar opponent = fight.opponent();

        Optional<WeaponAttack> meleeWeapon = pickMeleeWeapon(opponent);
        if (fight.opponentActions().actions() > 0 && fight.distance() <= 5 &&
                meleeWeapon.isPresent()
        ) {
            return new GeneratedCombatAction(meleeWeapon.get(), false);
        }

        Optional<SpellAttack> meleeSpell = pickMeleeSpell(opponent);
        if (fight.opponentActions().actions() > 0 && fight.distance() <= 5 &&
                meleeSpell.isPresent()
        ) {
            return new GeneratedCombatAction(meleeSpell.get(), false);
        }

        Optional<WeaponAttack> rangedWeapon = pickRangedWeapon(opponent);
        if (fight.opponentActions().actions() > 0 && fight.distance() > 5 &&
                rangedWeapon.isPresent()
        ) {
            return new GeneratedCombatAction(rangedWeapon.get(), false);
        }

        Optional<SpellAttack> rangedSpell = pickRangedSpell(opponent);
        if (fight.opponentActions().actions() > 0 && fight.distance() > 5 &&
                rangedSpell.isPresent()
        ) {
            return new GeneratedCombatAction(rangedSpell.get(), false);
        }

        Optional<WeaponAttack> meleeLightWeapon = pickLightMeleeWeapon(opponent);
        if (fight.opponentActions().bonusActions() > 0 && fight.distance() <= 5 &&
                meleeLightWeapon.isPresent()
        ) {
            return new GeneratedCombatAction(meleeLightWeapon.get(), true);
        }

        if (rangedWeapon.isEmpty() && rangedSpell.isEmpty() && fight.distance() > 5) {
            return new GeneratedCombatAction(new Move(Dir.TOWARDS_ENEMY, 5), false);
        }

        if (meleeWeapon.isEmpty() && meleeSpell.isEmpty() && fight.distance() <= 5) {
            return new GeneratedCombatAction(new Move(Dir.AWAY_FROM_ENEMY, 5), false);
        }

        return new GeneratedCombatAction(new EndTurn(), false);
    }

    private static Optional<SpellAttack> pickMeleeSpell(GameChar monster) {
        return monster.spells().stream()
                .filter(s -> !s.ranged())
                .map(Spell::name)
                .map(SpellAttack::new)
                .findFirst();
    }

    private static Optional<SpellAttack> pickRangedSpell(GameChar monster) {
        return monster.spells().stream()
                .filter(Spell::ranged)
                .map(Spell::name)
                .map(SpellAttack::new)
                .findFirst();
    }

    private static Optional<WeaponAttack> pickMeleeWeapon(GameChar monster) {
        return monster.weapons().stream()
                .filter(w -> !w.ranged())
                .map(Weapon::name)
                .map(WeaponAttack::new)
                .findFirst();
    }

    private static Optional<WeaponAttack> pickLightMeleeWeapon(GameChar monster) {
        return monster.weapons().stream()
                .filter(w -> !w.ranged())
                .filter(Weapon::light)
                .map(Weapon::name)
                .map(WeaponAttack::new)
                .findFirst();
    }

    private static Optional<WeaponAttack> pickRangedWeapon(GameChar monster) {
        return monster.weapons().stream()
                .filter(Weapon::ranged)
                .map(Weapon::name)
                .map(WeaponAttack::new)
                .findFirst();
    }

    @Override
    public AttackResult computeAttack(
            Attacks attack,
            GameChar attacker,
            GameChar defender
    ) {
        int maxDmg = switch (attack) {
            case WeaponAttack wa -> WEAPONS.stream()
                    .filter(w -> w.name().equals(wa.weapon()))
                    .map(Weapon::damage)
                    .findFirst().orElseThrow();
            case SpellAttack sa -> SPELLS.stream()
                    .filter(s -> s.name().equals(sa.spell()))
                    .map(Spell::damage)
                    .findFirst().orElseThrow();
        };
        int damage = new Random().nextInt(maxDmg) + 1;
        return new AttackResult(defender.damage(damage), damage);
    }

    private static GameChar generateOpponent(
            String name,
            NpcType type
    ) {
        CharTemplate template = switch (type) {
            case WARRIOR -> WARRIOR_TEMPLATE;
            case MAGIC -> WIZARD_TEMPLATE;
            case BEAST -> BEAST_TEMPLATE;
        };
        return new GameChar(
                name,
                template.level(),
                template.charClass(),
                template.maxHp(),
                template.maxHp(),
                template.ac(),
                0, 0,
                template.stats(),
                template.weapons(),
                template.spells(),
                template.availableActions()
        );
    }
}
