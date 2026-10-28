package shipyard;

import arc.Events;
import arc.graphics.Color;
import java.io.*;
import java.lang.reflect.*;
import java.util.Properties;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.world.Block;

/** Actual per-unit application/persistence; rendering is also exercised by desktop checks. */
final class ColourChecks{
    static void run(Block block) throws Exception{
        var loader = Vars.mods.getMod("modular-shipyard").loader;
        var cosmetics = loader.loadClass("shipyard.model.ShipCosmetics");
        var stats = loader.loadClass("shipyard.model.ShipLoadout");
        var service = loader.loadClass("shipyard.service.LoadoutService");
        var variants = loader.loadClass("shipyard.content.ShipVariants");
        var parse = cosmetics.getMethod("parse", String.class);
        var encode = cosmetics.getMethod("encode");
        var current = variants.getMethod("cosmetics", mindustry.type.UnitType.class);
        var apply = service.getMethod("apply", Player.class, int.class, int.class, stats, cosmetics);
        var restore = service.getMethod("restore", Player.class);
        Object defaults = cosmetics.getField("defaults").get(null), neutral = stats.getField("defaults").get(null);
        Object colours = parse.invoke(null, "62B9FF,FF4488,30EE70");
        Object otherColours = parse.invoke(null, "FF8833,55FFDD,AA66FF");
        check(encode.invoke(parse.invoke(null, "#62b9ff,ff4488,30ee70")).equals("62B9FF,FF4488,30EE70"), "HEX normalization");
        for(String invalid : new String[]{"fff,-,-", "FFFFFF00,-,-", "GGGGGG,-,-", "-,-", "-2,-,-", ",-,-"}){
            try{ parse.invoke(null, invalid); throw new AssertionError("invalid colours accepted: " + invalid); }
            catch(InvocationTargetException expected){ check(expected.getCause() instanceof IllegalArgumentException, "wrong validation failure"); }
        }
        Vars.world.loadGenerator(80, 80, tiles -> {
            tiles.fill(); tiles.eachTile(tile -> tile.setFloor(Blocks.stone.asFloor()));
            tiles.getn(8, 8).setBlock(Blocks.coreShard, Team.sharded, 0);
            tiles.getn(40, 40).setBlock(block, Team.sharded, 0);
            tiles.getn(42, 40).setBlock(Blocks.powerSource, Team.sharded, 0);
        });
        Vars.state.rules.env = mindustry.world.meta.Env.terrestrial;
        Building yard = Vars.world.build(40, 40);
        yard.power.graph.reflow(yard); yard.power.graph.update();
        Player alice = LoadoutChecks.player("colours-alice"); alice.add();
        Player bob = LoadoutChecks.player("colours-bob"); bob.add();
        Unit a = ship(alice, UnitTypes.alpha, yard), b = ship(bob, UnitTypes.alpha, yard);
        float vanillaHealth = UnitTypes.alpha.health, vanillaSpeed = UnitTypes.alpha.speed;
        Color nativeEngine = UnitTypes.alpha.engineColor, nativeInner = UnitTypes.alpha.engineColorInner;
        Color nativeTeam = a.team.color.cpy();
        a.health = 75f;
        var mounts = a.mounts;
        check(((String)DockTest.apply(apply, alice, yard.pos(), a.id, neutral, colours)).contains("applied"), "cosmetic-only Apply rejected");
        check(a.mounts == mounts && a.controller() == alice, "colour change replaced ship state");
        near(a.health, 75f, "colour change healed damage");
        near(a.maxHealth, vanillaHealth, "colours changed maximum HP");
        near(a.type.speed, vanillaSpeed, "colours changed speed");
        check((int)stats.getMethod("points").invoke(variants.getMethod("current", mindustry.type.UnitType.class).invoke(null, a.type)) == 0,
            "cosmetics consume points");
        check(encode.invoke(current.invoke(null, a.type)).equals("62B9FF,FF4488,30EE70"), "cosmetic model lost");
        nearColour(a.type.engineColor, Color.valueOf("30EE70"), "engine colour not applied");
        check(a.type.engineColorInner.r > a.type.engineColor.r, "bright engine centre not retained");
        a.heal(); nearColour(a.type.cellColor(a), Color.valueOf("FF4488"), "native accent cell colour not applied");
        a.health = 67.89123f;
        check(((String)DockTest.apply(apply, alice, yard.pos(), a.id, neutral, colours)).contains("applied"), "reapplying colours failed");
        check(Float.floatToIntBits(a.health) == Float.floatToIntBits(67.89123f), "cosmetic-only Apply rounded current health");
        a.health = 75f;
        check(UnitTypes.alpha.engineColor == nativeEngine && UnitTypes.alpha.engineColorInner == nativeInner,
            "shared vanilla engine changed");
        nearColour(a.team.color, nativeTeam, "team colour mutated");
        check(b.type == UnitTypes.alpha, "another player's colours changed");
        check(((String)DockTest.apply(apply, bob, yard.pos(), b.id, neutral, otherColours)).contains("applied"), "second player's colours rejected");
        check(a.type != b.type && a.type.id == b.type.id, "cosmetics added content IDs or shared private views");
        check(encode.invoke(current.invoke(null, b.type)).equals("FF8833,55FFDD,AA66FF"), "second player lost independent colours");

        Object invalid = cosmetics.getConstructor(int.class, int.class, int.class).newInstance(0x1000000, -1, -1);
        check(((String)DockTest.apply(apply, alice, yard.pos(), a.id, neutral, invalid)).startsWith("Invalid cosmetic"), "invalid RGB accepted by authority");
        check(encode.invoke(current.invoke(null, a.type)).equals("62B9FF,FF4488,30EE70"), "rejected cosmetic request changed state");
        Object overBudget = stats.getConstructor(int.class, int.class, int.class, int.class, int.class).newInstance(5,5,5,5,5);
        check(!((String)DockTest.apply(apply, alice, yard.pos(), a.id, overBudget, colours)).contains("applied"), "colours bypass stat budget");

        // Existing stat-only entry points preserve colours, while channel reset changes only one.
        service.getMethod("apply", Player.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class)
            .invoke(null, alice, yard.pos(), a.id, 0,0,0,1,1);
        check(encode.invoke(current.invoke(null, a.type)).equals("62B9FF,FF4488,30EE70"), "stat edit removed colours");
        near(a.health / a.maxHealth, .5f, "stat+colour edit changed damage fraction");
        Object oneReset = cosmetics.getMethod("withChannel", int.class, int.class).invoke(colours, 1, -1);
        check(((String)DockTest.apply(apply, alice, yard.pos(), a.id, neutral, oneReset)).contains("applied"), "single-channel reset rejected");
        check(encode.invoke(current.invoke(null, a.type)).equals("62B9FF,-,30EE70"), "channel reset changed other channels");
        check(((String)DockTest.apply(apply, alice, yard.pos(), a.id, neutral, colours)).contains("applied"), "reapply failed");
        int id = a.id;
        var file = Vars.dataDirectory.child("colours-roundtrip.msav");
        SaveIO.write(file); SaveIO.load(file);
        a = Groups.unit.getByID(id);
        check(encode.invoke(current.invoke(null, a.type)).equals("62B9FF,FF4488,30EE70"), "disk save lost colours");
        near(a.health, 75f, "disk cosmetic restoration changed health");
        var bytes = new ByteArrayOutputStream();
        SaveIO.getVersion().writeCustomChunks(new DataOutputStream(bytes), true);
        Events.fire(new ResetEvent());
        a.setType(Vars.content.unit("modular-shipyard-ship-alpha-hull"));
        SaveIO.getVersion().readCustomChunks(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        check(encode.invoke(current.invoke(null, a.type)).equals("62B9FF,FF4488,30EE70"), "join chunk lost colours");
        a.setType(Vars.content.unit("modular-shipyard-ship-alpha-hull"));
        Events.fire(Trigger.update);
        check(encode.invoke(current.invoke(null, a.type)).equals("62B9FF,FF4488,30EE70"), "snapshot repair lost colours");

        yard = Vars.world.build(40,40); yard.power.graph.reflow(yard); yard.power.graph.update();
        service.getMethod("init").invoke(null);
        Unit fresh = ship(alice, UnitTypes.alpha, yard); restore.invoke(null, alice);
        check(current.invoke(null, fresh.type).equals(colours), "disk/reconnect profile lost colours");
        Unit beta = ship(alice, UnitTypes.beta, yard); restore.invoke(null, alice);
        check(current.invoke(null, beta.type).equals(defaults), "Alpha colours leaked into another tier");
        Unit alpha = ship(alice, UnitTypes.alpha, yard); restore.invoke(null, alice);
        check(current.invoke(null, alpha.type).equals(colours), "respawn lost Alpha colours");
        check(((String)DockTest.apply(apply, alice, yard.pos(), alpha.id, neutral, defaults)).contains("applied"), "default reset failed");
        check(alpha.type == UnitTypes.alpha, "default stats and colours did not restore vanilla type");
        Properties profiles = new Properties();
        try(var input = Vars.dataDirectory.child("modular-shipyard/loadouts-v1.properties").read()){ profiles.load(input); }
        check(profiles.getProperty("colours-alice.alpha.colours").equals("-,-,-"), "native defaults not persisted");
        check(Vars.content.units().count(type -> type.name.startsWith("modular-shipyard-ship-")) == 3, "colours registered extra hulls");
        SaveIO.load(new arc.files.Fi("../../tests/fixtures/shipyard-v0.5.0.msav"));
        Unit migrated = Groups.unit.find(ship -> ship.type.name.equals("modular-shipyard-ship-alpha-hull"));
        check(migrated != null && current.invoke(null, migrated.type).equals(defaults), "v0.5 chunk did not default colours");
        near(migrated.maxHealth, 172.5f, "v0.5 migration lost Durability");
        near(migrated.health, 86.25f, "v0.5 migration changed damage");
        check((int)stats.getMethod("fireRate").invoke(variants.getMethod("current", mindustry.type.UnitType.class).invoke(null, migrated.type)) == 1,
            "v0.5 migration lost Fire Rate");
        SaveIO.write(file); SaveIO.load(file);
        check(current.invoke(null, Groups.unit.find(ship -> ship.type.name.equals("modular-shipyard-ship-alpha-hull")).type).equals(defaults),
            "resaving v0.5 migration introduced colours");
        System.out.println("PASS: actual v0.5 chunk migrates with native colours and intact five-slider stats/damage");
        System.out.println("PASS: strict opaque HEX validation, free independent colours, native accents/engines, health/state preservation, channel/default resets, disk/join/snapshot state and per-tier respawn/reconnect profiles");
    }
    private static Unit ship(Player player, mindustry.type.UnitType type, Building yard){
        Unit unit = type.create(Team.sharded); unit.spawnedByCore=true; unit.set(yard); unit.add(); player.unit(unit); return unit;
    }
    private static void nearColour(Color actual, Color expected, String message){
        near(actual.r, expected.r, message); near(actual.g, expected.g, message); near(actual.b, expected.b, message);
    }
    private static void near(float actual,float expected,String message){ check(Math.abs(actual-expected)<.001f,message); }
    private static void check(boolean condition,String message){ if(!condition) throw new AssertionError(message); }
}
