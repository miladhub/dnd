package org.meh.dnd;

import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.meh.dnd.CharClass.*;
import static org.meh.dnd.Die.*;
import static org.meh.dnd.FightOutcome.IN_PROGRESS;
import static org.meh.dnd.Stat.*;

public class DndCombat implements Combat
{
    private final static Logger LOG = Logger.getLogger(DndCombat.class);

    public static final AvailableActions STANDARD_ACTIONS = new AvailableActions(1, 1, 30);
    public static final Stats STATS_FIGHTER = new Stats(15, 13, 14, 8, 12, 10);
    public static final Stats STATS_WIZARD = new Stats(8, 12, 14, 15, 13, 10);
    public static final Weapon SWORD = new Weapon("sword", false, D8, false, true);
    public static final Weapon BATTLEAXE = new Weapon("battleaxe", false, D12, true, false);
    public static final Weapon BOW = new Weapon("bow", true, D6, true, false);
    public static final Weapon UNARMED = new Weapon("unarmed", false, D4, false, true);
    public static final Weapon DAGGER = new Weapon("dagger", false, D6, false, true);
    public static final Spell MAGIC_MISSILE = new Spell("Magic Missile", true, D8);
    public static final Spell SHOCKING_GRASP = new Spell("Shocking Grasp", false, D8);
    public static final Spell FIRE_BOLT = new Spell("Fire Bolt", true, D12);

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
        String dmg = switch (result) {
            case Hit hit -> " (" + hit.damage() + " hp damage)";
            case Miss ignored -> " (missed)";
        };
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
        return attackWeapon(name).findFirst().orElseThrow();
    }

    @Override
    public Fight generateFight(
            GameChar gameChar,
            Attack attack
    ) {
        GameChar opponent = generateOpponent(attack.target(), attack.type());
        boolean playersTurn = computePlayerTurn(gameChar, opponent);
        return new Fight(playersTurn, opponent, List.of(), 5, IN_PROGRESS,
                gameChar.availableActions(),
                opponent.availableActions());
    }

    private boolean computePlayerTurn(
            GameChar gameChar,
            GameChar opponent
    ) {
        int playerInitiative = Dice.initiative(gameChar);
        int opponentInitiative = Dice.initiative(opponent);
        LOG.infof("initiative roll - %s: %d, %s: %d",
                gameChar.name(),
                playerInitiative,
                opponent.name(),
                opponentInitiative);
        return playerInitiative >= opponentInitiative;
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
        if (hits(attack, attacker, defender)) {
            DamageRoll roll = damageRoll(attack, attacker);
            LOG.infof("damage roll (%s, %s) - %s: %d",
                    roll.die().name().toLowerCase(),
                    roll.stat().name().toLowerCase(),
                    attacker.name(),
                    roll.damage());
            return new Hit(defender.damage(roll.damage()), roll.damage());
        } else {
            return new Miss(defender);
        }
    }

    private DamageRoll damageRoll(
            Attacks attack,
            GameChar attacker
    ) {
        Die die = damageDie(attack);
        return switch (attack) {
            case SpellAttack ignored ->
                    new DamageRoll(Dice.rollInt(attacker, die), die, INT);
            case WeaponAttack ignored -> isRangedAttack(attack)
                    ? new DamageRoll(Dice.rollRanged(attacker, die), die, DEX)
                    : new DamageRoll(Dice.rollMelee(attacker, die), die, STR);
        };
    }

    private static Die damageDie(Attacks attack) {
        return switch (attack) {
            case WeaponAttack wa -> attackWeapon(wa.weapon())
                    .map(Weapon::damage)
                    .findFirst().orElseThrow();
            case SpellAttack sa -> attackSpell(sa.spell())
                    .map(Spell::damage)
                    .findFirst().orElseThrow();
        };
    }

    record AttackRoll(int roll, Stat stat) {}
    record DamageRoll(int damage, Die die, Stat stat) {}

    private boolean hits(
            Attacks attack,
            GameChar attacker,
            GameChar defender
    ) {
        AttackRoll attackRoll = attackRoll(attack, attacker);
        LOG.infof("attack roll (%s) - %s: %d, %s (AC): %d",
                attackRoll.stat().name().toLowerCase(),
                attacker.name(),
                attackRoll.roll(),
                defender.name(),
                defender.ac());
        return attackRoll.roll() >= defender.ac();
    }

    private static AttackRoll attackRoll(
            Attacks attack,
            GameChar attacker
    ) {
        int pb = proficiencyBonus(attacker);
        return switch (attack) {
            case SpellAttack ignored ->
                    new AttackRoll(pb + Dice.rollInt(attacker, D20), INT);
            case WeaponAttack ignored -> isRangedAttack(attack)
                    ? new AttackRoll(pb + Dice.rollRanged(attacker, D20), DEX)
                    : new AttackRoll(pb + Dice.rollMelee(attacker, D20), STR);
        };
    }

    private static int proficiencyBonus(GameChar gameChar) {
        int level = gameChar.level();
        if (level <= 4)
            return 2;
        else if (level <= 8)
            return 3;
        else if (level <= 12)
            return 4;
        else if (level <= 16)
            return 5;
        else
            return 6;
    }

    private static boolean isRangedAttack(Attacks attack) {
        return switch (attack) {
            case WeaponAttack wa -> attackWeapon(wa.weapon())
                    .map(Weapon::ranged)
                    .findFirst().orElseThrow();
            case SpellAttack sa -> attackSpell(sa.spell())
                    .map(Spell::ranged)
                    .findFirst().orElseThrow();
        };
    }

    private static Stream<Spell> attackSpell(String name) {
        return SPELLS.stream().filter(s -> s.name().equals(name));
    }

    private static Stream<Weapon> attackWeapon(String wa) {
        return WEAPONS.stream()
                .filter(w -> w.name().equals(wa));
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
