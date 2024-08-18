package org.meh.dnd;

import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

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
    public static final Weapon LONGSWORD = new Weapon("longsword", false, D8, false, true);
    public static final Weapon GREATAXE = new Weapon("greataxe", false, D12, true, false);
    public static final Weapon LONGBOW = new Weapon("longbow", true, D6, true, false);
    public static final Weapon UNARMED = new Weapon("unarmed", false, D4, false, true);
    public static final Weapon DAGGER = new Weapon("dagger", false, D4, false, true);
    public static final Spell SHOCKING_GRASP = new Spell("Shocking Grasp", false, D8, true, 0);
    public static final Spell FIRE_BOLT = new Spell("Fire Bolt", true, D10, true, 0);
    public static final Spell MAGIC_MISSILE = new Spell("Magic Missile", true, D8, false, 1);
    public static final Spell MELF_ARROW = new Spell("Melf's Magic Arrow", true, D8, false, 2);

    private static final List<Weapon> WEAPONS = List.of(
            LONGSWORD,
            GREATAXE,
            UNARMED,
            DAGGER,
            LONGBOW
    );
    private static final List<Spell> SPELLS = List.of(
            SHOCKING_GRASP,
            FIRE_BOLT,
            MAGIC_MISSILE,
            MELF_ARROW
    );
    public static final List<Weapon> FIGHTER_WEAPONS = List.of(LONGSWORD, DAGGER, LONGBOW);
    public static final List<Weapon> WIZARD_WEAPONS = List.of(LONGBOW, DAGGER);
    public static final List<Spell> WIZARD_SPELLS = List.of(MAGIC_MISSILE, SHOCKING_GRASP, FIRE_BOLT, MELF_ARROW);
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
    public static final int XP_GAIN = 100;

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
                    (m.weapon().ranged()? "ranged" : "melee") +
                    " attack with " + m.weapon().name() + dmg;
            case SpellAttack s -> "cast " + s.spell().name() + dmg;
        };
        if (opponent.isDead()) {
            return attacker.name() + ": killed " + opponent.name() + ", " +
                    attackDescription;
        }
        else {
            return attacker.name() + ": " + attackDescription;
        }
    }

    public static Weapon weaponByName(String name) {
        return WEAPONS.stream()
                .filter(w -> w.name().equals(name))
                .findFirst().orElseThrow();
    }

    public static SpellSlots spellSlots(
            CharClass charClass,
            int level
    ) {
        if (charClass != WIZARD)
            return new SpellSlots(0, 0, 0, 0, 0, 0, 0, 0, 0);
        else
            return switch (level) {
                case 1 -> new SpellSlots(2, 0, 0, 0, 0, 0, 0, 0, 0);
                case 2 -> new SpellSlots(3, 0, 0, 0, 0, 0, 0, 0, 0);
                case 3 -> new SpellSlots(4, 2, 0, 0, 0, 0, 0, 0, 0);
                case 4 -> new SpellSlots(4, 3, 0, 0, 0, 0, 0, 0, 0);
                case 5 -> new SpellSlots(4, 3, 2, 0, 0, 0, 0, 0, 0);
                case 6 -> new SpellSlots(4, 3, 3, 0, 0, 0, 0, 0, 0);
                case 7 -> new SpellSlots(4, 3, 3, 1, 0, 0, 0, 0, 0);
                case 8 -> new SpellSlots(4, 3, 3, 2, 0, 0, 0, 0, 0);
                case 9 -> new SpellSlots(4, 3, 3, 3, 1, 0, 0, 0, 0);
                case 10 -> new SpellSlots(4, 3, 3, 3, 2, 0, 0, 0, 0);
                case 11, 12 -> new SpellSlots(4, 3, 3, 3, 2, 1, 0, 0, 0);
                case 13, 14 -> new SpellSlots(4, 3, 3, 3, 2, 1, 1, 0, 0);
                case 15, 16 -> new SpellSlots(4, 3, 3, 3, 2, 1, 1, 1, 0);
                case 17 -> new SpellSlots(4, 3, 3, 3, 2, 1, 1, 1, 1);
                case 18 -> new SpellSlots(4, 3, 3, 3, 3, 1, 1, 1, 1);
                case 19 -> new SpellSlots(4, 3, 3, 3, 3, 2, 1, 1, 1);
                case 20 -> new SpellSlots(4, 3, 3, 3, 3, 2, 2, 1, 1);
                default ->
                        throw new IllegalStateException("Unexpected value: " + level);
            };
    }

    public static int xpAtLevel(int level) {
        return switch (level) {
            case 1 ->  0;
            case 2 ->  300;
            case 3 ->  900;
            case 4 ->  2_700;
            case 5 ->  6_500;
            case 6 ->  14_000;
            case 7 ->  23_000;
            case 8 ->  34_000;
            case 9 ->  48_000;
            case 10 -> 64_000;
            case 11 -> 85_000;
            case 12 -> 100_000;
            case 13 -> 120_000;
            case 14 -> 140_000;
            case 15 -> 165_000;
            case 16 -> 195_000;
            case 17 -> 225_000;
            case 18 -> 265_000;
            case 19 -> 305_000;
            default -> 355_000;
        };
    }

    public static GameChar levelUp(GameChar gc) {
        if (gc.xp() < xpAtLevel(gc.level() + 1))
            return gc;
        int incHp = Dice.roll(1, hitDice(gc.charClass()), Dice.conBonus(gc));
        int newLevel = gc.level() + 1;
        return new GameChar(
                gc.name(),
                newLevel,
                gc.charClass(),
                gc.maxHp() + incHp,
                gc.maxHp() + incHp,
                gc.ac(),
                gc.xp(),
                DndCombat.xpAtLevel(newLevel + 1),
                gc.stats(),
                gc.weapons(),
                gc.spells(),
                gc.availableActions(),
                spellSlots(gc.charClass(), newLevel)
        );
    }

    private static Die hitDice(CharClass charClass) {
        return switch (charClass) {
            case FIGHTER -> D10;
            case WIZARD -> D6;
        };
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
                opponent.availableActions(),
                XP_GAIN);
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
                .map(DndCombat::spellByName)
                .map(SpellAttack::new)
                .findFirst();
    }

    private static Optional<SpellAttack> pickRangedSpell(GameChar monster) {
        return monster.spells().stream()
                .filter(Spell::ranged)
                .map(Spell::name)
                .map(DndCombat::spellByName)
                .map(SpellAttack::new)
                .findFirst();
    }

    private static Optional<WeaponAttack> pickMeleeWeapon(GameChar monster) {
        return monster.weapons().stream()
                .filter(w -> !w.ranged())
                .map(Weapon::name)
                .map(DndCombat::weaponByName)
                .map(WeaponAttack::new)
                .findFirst();
    }

    private static Optional<WeaponAttack> pickLightMeleeWeapon(GameChar monster) {
        return monster.weapons().stream()
                .filter(w -> !w.ranged())
                .filter(Weapon::light)
                .map(Weapon::name)
                .map(DndCombat::weaponByName)
                .map(WeaponAttack::new)
                .findFirst();
    }

    private static Optional<WeaponAttack> pickRangedWeapon(GameChar monster) {
        return monster.weapons().stream()
                .filter(Weapon::ranged)
                .map(Weapon::name)
                .map(DndCombat::weaponByName)
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
                    new DamageRoll(Dice.roll(1, die, 0), die, INT);
            case WeaponAttack ignored -> isRangedAttack(attack)
                    ? new DamageRoll(Dice.rollRanged(attacker, die), die, DEX)
                    : new DamageRoll(Dice.rollMelee(attacker, die), die, STR);
        };
    }

    private static Die damageDie(Attacks attack) {
        return switch (attack) {
            case WeaponAttack wa -> wa.weapon().damage();
            case SpellAttack sa -> sa.spell().damage();
        };
    }

    record AttackRoll(int roll, Stat stat) {}
    record DamageRoll(int damage, Die die, Stat stat) {}

    private boolean hits(
            Attacks attack,
            GameChar attacker,
            GameChar defender
    ) {
        if (needsNoRoll(attack))
            return true;
        AttackRoll attackRoll = attackRoll(attack, attacker);
        LOG.infof("attack roll (%s) - %s: %d, %s (AC): %d",
                attackRoll.stat().name().toLowerCase(),
                attacker.name(),
                attackRoll.roll(),
                defender.name(),
                defender.ac());
        return attackRoll.roll() >= defender.ac();
    }

    private boolean needsNoRoll(Attacks attack) {
        return attack instanceof SpellAttack sa &&
                !sa.spell().rollsToHit();
    }

    private static AttackRoll attackRoll(
            Attacks attack,
            GameChar attacker
    ) {
        int pb = proficiencyBonus(attacker);
        return switch (attack) {
            case SpellAttack ignored ->
                    new AttackRoll(pb + Dice.roll(1, D20, Dice.intBonus(attacker)), INT);
            case WeaponAttack ignored -> isRangedAttack(attack)
                    ? new AttackRoll(pb + Dice.rollRanged(attacker, D20), DEX)
                    : new AttackRoll(pb + Dice.rollMelee(attacker, D20), STR);
        };
    }

    public static int proficiencyBonus(GameChar gameChar) {
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
            case WeaponAttack wa -> wa.weapon().ranged();
            case SpellAttack sa -> sa.spell().ranged();
        };
    }

    public static Spell spellByName(String name) {
        return SPELLS.stream()
                .filter(s -> s.name().equals(name))
                .findFirst().orElseThrow();
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
                template.availableActions(),
                new SpellSlots(4, 3, 0, 0, 0, 0, 0, 0, 0)
        );
    }
}
