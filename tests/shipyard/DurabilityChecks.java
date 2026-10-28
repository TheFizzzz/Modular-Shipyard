package shipyard;

import arc.Events;
import arc.util.Time;
import arc.util.io.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Properties;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.world.Block;

/** Engine integration: HP, damage, weapon cooldowns, native codecs and saved profiles. */
final class DurabilityChecks{
    private static Method apply, restore, refresh;
    private static Building yard;

    static void run(Block block) throws Exception{
        var loader = Vars.mods.getMod("modular-shipyard").loader;
        var service = loader.loadClass("shipyard.service.LoadoutService");
        apply = service.getMethod("apply", Player.class, int.class, int.class,
            int.class, int.class, int.class, int.class, int.class);
        restore = service.getMethod("restore", Player.class);
        refresh = loader.loadClass("shipyard.service.UnitLoadouts").getMethod("refreshUnit", Unit.class);
        var model = loader.loadClass("shipyard.model.ShipLoadout");
        Object sacrifice = model.getConstructor(int.class, int.class, int.class, int.class, int.class)
            .newInstance(0, 0, 0, -2, -3);
        check((int)model.getMethod("refundPoints").invoke(sacrifice) == 5, "new reductions do not refund points");
        for(String raw : new String[]{"1,-1", "1,-1,2"}){
            Object old = model.getMethod("parse", String.class).invoke(null, raw);
            check((int)model.getMethod("durability").invoke(old) == 0 && (int)model.getMethod("fireRate").invoke(old) == 0,
                "old profile gained new upgrades");
        }
        Vars.world.loadGenerator(80, 80, tiles -> {
            tiles.fill(); tiles.eachTile(tile -> tile.setFloor(Blocks.stone.asFloor()));
            tiles.getn(8, 8).setBlock(Blocks.coreShard, Team.sharded, 0);
            tiles.getn(40, 40).setBlock(block, Team.sharded, 0);
            tiles.getn(42, 40).setBlock(Blocks.powerSource, Team.sharded, 0);
        });
        Vars.state.rules.env = mindustry.world.meta.Env.terrestrial;
        yard = Vars.world.build(40, 40);
        yard.power.graph.reflow(yard); yard.power.graph.update();
        Player alice = LoadoutChecks.player("durability-alice"); alice.add();
        Player bob = LoadoutChecks.player("durability-bob"); bob.add();
        Unit a = ship(alice, UnitTypes.alpha), b = ship(bob, UnitTypes.alpha);
        float baseHp = UnitTypes.alpha.health, baseReload = UnitTypes.alpha.weapons.first().reload;
        float bulletDamage = UnitTypes.alpha.weapons.first().bullet.damage;
        var mounts = a.mounts;
        a.health = baseHp * .5f;
        check(apply(alice, 0, 2, 0).contains("applied"), "Durability Apply failed");
        near(a.maxHealth, baseHp * 1.3f, "max HP not applied");
        near(a.health, a.maxHealth * .5f, "damage percentage not preserved on upgrade");
        check(a.mounts == mounts && a.controller() == alice, "unit state replaced");
        check(b.type == UnitTypes.alpha && b.maxHealth == baseHp, "another player's HP changed");
        near(UnitTypes.alpha.health, baseHp, "shared vanilla HP changed");
        near(a.armor, UnitTypes.alpha.armor, "Durability changed armor");
        a.heal();
        a.damagePierce(30f);
        near(a.health, baseHp * 1.3f - 30f*.94f, "native damage ignored upgraded HP");
        float fraction = a.health / a.maxHealth;
        for(int i = 0; i < 5; i++){
            check(apply(alice, 0, -4, 0).contains("applied"), "Durability sacrifice failed");
            near(a.maxHealth, baseHp * .4f, "minimum Durability formula");
            check(a.health > 0 && a.health <= a.maxHealth, "invalid HP after reduction");
            check(apply(alice, 0, 2, 0).contains("applied"), "Durability reapply failed");
            near(a.health / a.maxHealth, fraction, "loadout cycling repaired damage");
        }
        check(!apply(alice, 0, 5, 5).contains("applied"), "new upgrades bypass budget");
        check(!apply(alice, 0, Integer.MIN_VALUE, 0).contains("applied"), "Durability bounds bypass");
        check(!apply(alice, 0, 0, Integer.MAX_VALUE).contains("applied"), "Fire Rate bounds bypass");

        // +1 Construction and +1 Fire Rate cost 4 points; .92 * 1.10 = 1.012.
        check(apply(alice, 1, 0, 1).contains("applied"), "combined Fire Rate Apply failed");
        float rate = .92f * 1.1f;
        Time.delta = 1f;
        for(var mount : a.mounts) mount.reload = 100f;
        a.update();
        near(a.reloadMultiplier, rate, "Construction/Fire Rate combination wrong");
        near(a.mounts[0].reload, 100f - rate, "actual weapon cooldown unchanged");
        a.update(); near(a.reloadMultiplier, rate, "Fire Rate accumulated");
        a.apply(StatusEffects.overclock, 100f); a.update();
        near(a.reloadMultiplier, rate * StatusEffects.overclock.reloadMultiplier, "Fire Rate did not compose with status");
        a.clearStatuses();
        check(apply(alice, 0, 0, -4).contains("applied"), "fire sacrifice failed");
        a.update(); near(a.reloadMultiplier, .6f, "negative Fire Rate ignored");
        near(UnitTypes.alpha.weapons.first().reload, baseReload, "shared Weapon changed");
        near(UnitTypes.alpha.weapons.first().bullet.damage, bulletDamage, "bullet damage changed");
        check(apply(alice, 0, 1, 1).contains("applied"), "two new upgrades rejected");
        check(apply(bob, 0, -2, -2).contains("applied"), "independent reduced loadout rejected");
        check(a.type != b.type && a.type.id == b.type.id, "private views or stable ID lost");
        near(b.maxHealth, baseHp * .7f, "other player's reduction missing");

        // Native read resets maxHealth to the neutral hull. Reattachment must keep the
        // already-authoritative HP, not resize it again relative to that neutral hull.
        a.health = a.maxHealth * .4f;
        roundTrip(a, false); roundTrip(a, true);
        float hp = a.health;
        a.setType(Vars.content.unit("modular-shipyard-ship-alpha-hull"));
        Events.fire(Trigger.update);
        near(a.maxHealth, baseHp * 1.15f, "snapshot max HP not repaired");
        near(a.health, hp, "snapshot repair rescaled health");
        int id = a.id;
        var save = Vars.dataDirectory.child("durability-roundtrip.msav");
        SaveIO.write(save); SaveIO.load(save);
        a = Groups.unit.getByID(id);
        near(a.maxHealth, baseHp * 1.15f, "save lost max HP");
        near(a.health, hp, "save changed damage");
        near((float)model.getMethod("fireRateFactor").invoke(
            loader.loadClass("shipyard.content.ShipVariants").getMethod("current", mindustry.type.UnitType.class).invoke(null, a.type)),
            1.1f, "save lost Fire Rate");

        // Expanded profiles restore fresh ships at the saved tier's HP and rate.
        Unit respawn = ship(alice, UnitTypes.alpha); restore.invoke(null, alice);
        near(respawn.maxHealth, baseHp * 1.15f, "respawn lost Durability");
        near(respawn.health, respawn.maxHealth, "fresh respawn did not retain full health");
        Unit lower = ship(alice, UnitTypes.beta); restore.invoke(null, alice);
        check(lower.type == UnitTypes.beta, "Alpha profile applied to Beta");
        var profiles = new Properties();
        try(var input = Vars.dataDirectory.child("modular-shipyard/loadouts-v1.properties").read()){ profiles.load(input); }
        check(profiles.getProperty("durability-alice.alpha").equals("0,0,0,1,1"), "five-field profile not saved");
        check(Vars.content.units().count(type -> type.name.startsWith("modular-shipyard-ship-")) == 3, "new dimensions registered types");
        SaveIO.load(new arc.files.Fi("../../tests/fixtures/shipyard-v0.4.0.msav"));
        Unit migrated = Groups.unit.find(ship -> ship.type.name.equals("modular-shipyard-ship-alpha-hull"));
        check(migrated != null, "v0.4 hull lost");
        near(migrated.maxHealth, baseHp, "v1 chunk unexpectedly changed max HP");
        near(migrated.health, 75f, "v1 chunk changed saved damage");
        near(migrated.type.mineSpeed, UnitTypes.alpha.mineSpeed * 1.15f, "v1 chunk lost old loadout");
        Object oldState = loader.loadClass("shipyard.content.ShipVariants").getMethod("current", mindustry.type.UnitType.class).invoke(null, migrated.type);
        check((int)model.getMethod("durability").invoke(oldState) == 0 && (int)model.getMethod("fireRate").invoke(oldState) == 0,
            "v1 chunk gained new slider values");
        SaveIO.write(save); SaveIO.load(save);
        near(Groups.unit.find(ship -> ship.type.name.equals("modular-shipyard-ship-alpha-hull")).health, 75f, "v1 migration resave changed HP");
        System.out.println("PASS: actual v0.4 save chunk migrates with neutral new controls and preserves damage on resave");
        System.out.println("PASS: Durability HP/damage/cycling, Fire Rate cooldown/status composition, new refunds/bounds, independent private views, native/save codecs, respawn and five-field profiles");
    }

    private static String apply(Player player, int construction, int durability, int fireRate) throws Exception{
        return (String)DockTest.apply(apply, player, yard.pos(), player.unit().id, 0, 0, construction, durability, fireRate);
    }
    private static Unit ship(Player player, mindustry.type.UnitType type){
        Unit unit = type.create(Team.sharded); unit.spawnedByCore = true; unit.set(yard); unit.add(); player.unit(unit); return unit;
    }
    private static void roundTrip(Unit original, boolean sync) throws Exception{
        Player owner = original.getPlayer();
        var bytes = new ByteArrayOutputStream(); var out = new Writes(new DataOutputStream(bytes));
        if(sync) original.writeSync(out); else original.write(out);
        Unit copy = (Unit)EntityMapping.map(original.classId()).get();
        var in = new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        if(sync) copy.readSync(in); else copy.read(in);
        copy.id = original.id; refresh.invoke(null, copy);
        near(copy.maxHealth, original.maxHealth, "native codec max HP mismatch");
        near(copy.health, original.health, "native codec resized authoritative HP");
        if(owner != null) owner.unit(original);
    }
    private static void near(float actual, float expected, String reason){ check(Math.abs(actual - expected) < .001f, reason + " (" + actual + " vs " + expected + ")"); }
    private static void check(boolean condition, String reason){ if(!condition) throw new AssertionError(reason); }
}
