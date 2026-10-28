package shipyard;

import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Properties;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;

final class ConstructionChecks{
    private static Method apply, restore;
    private static Class<?> service;
    private static Building yard;

    static void run(Block block) throws Exception{
        ClassLoader loader = Vars.mods.getMod("modular-shipyard").loader;
        service = loader.loadClass("shipyard.service.LoadoutService");
        apply = service.getMethod("apply", Player.class, int.class, int.class, int.class, int.class, int.class);
        restore = service.getMethod("restore", Player.class);
        Class<?> model = loader.loadClass("shipyard.model.ShipLoadout");
        var constructor = model.getConstructor(int.class, int.class, int.class);
        check((int)model.getMethod("points").invoke(constructor.newInstance(0, 0, 1)) == 2,
            "automatic weapon penalty incorrectly refunds points");
        near((float)model.getMethod("speedFactor").invoke(constructor.newInstance(0, -4, 0)), 1f,
            "cargo sacrifice still grants free speed");
        near((float)model.getMethod("fireRateFactor").invoke(constructor.newInstance(0, 0, -4)), 1f,
            "construction sacrifice grants free fire rate");
        check((int)model.getMethod("points").invoke(constructor.newInstance(2, 0, -2)) == 2,
            "manual Construction refunds missing");
        int count = 0;
        check(Vars.content.units().count(type -> type.name.startsWith("modular-shipyard-ship-")) == 3, "registered hull count grew");
        for(UnitType base : new UnitType[]{UnitTypes.alpha, UnitTypes.beta, UnitTypes.gamma}){
            for(int speed = -4; speed <= 5; speed++){
                for(int cargo = -4; cargo <= 5; cargo++){
                    for(int construction = -4; construction <= 5; construction++){
                        String name = "modular-shipyard-ship-" + base.name + "-s" + (speed + 4) + "-c" + (cargo + 4)
                            + (construction == 0 ? "" : "-b" + (construction + 4));
                        UnitType type = Vars.content.unit(name);
                        check(type != null && type.hidden, "missing registered Construction variant");
                        near(type.buildSpeed, base.buildSpeed * (1f + construction * .15f), "build speed formula");
                        near(type.buildRange, base.buildRange * (1f + construction * .1f), "build range formula");
                        check(type.weapons == base.weapons && type.constructor == base.constructor, "shared weapons or constructor changed");
                        check(type.id == Vars.content.unit("modular-shipyard-ship-" + base.name + "-hull").id, "alias registered another ID");
                        count++;
                    }
                }
            }
        }
        check(count == 3000, "unexpected variant count");
        Vars.world.loadGenerator(80, 80, tiles -> {
            tiles.fill();
            tiles.eachTile(tile -> tile.setFloor(Blocks.stone.asFloor()));
            tiles.getn(8, 8).setBlock(Blocks.coreShard, Team.sharded, 0);
            tiles.getn(40, 40).setBlock(block, Team.sharded, 0);
            tiles.getn(42, 40).setBlock(Blocks.powerSource, Team.sharded, 0);
        });
        yard = Vars.world.build(40, 40);
        yard.power.graph.reflow(yard);
        yard.power.graph.update();
        Vars.world.build(8, 8).items.add(Items.copper, 1000);
        Vars.state.rules.infiniteResources = false;
        Vars.state.rules.instantBuild = false;
        Vars.state.rules.env = mindustry.world.meta.Env.terrestrial;
        Time.delta = 1f;
        Player alice = LoadoutChecks.player("construction-test-alice");
        Player bob = LoadoutChecks.player("construction-test-bob");
        alice.add();
        bob.add();
        Unit a = ship(alice, UnitTypes.alpha);
        Unit b = ship(bob, UnitTypes.alpha);
        float baseBuild = UnitTypes.alpha.buildSpeed, baseRange = UnitTypes.alpha.buildRange;
        float weaponReload = UnitTypes.alpha.weapons.first().reload;
        var mounts = a.mounts;
        check(apply(alice, 0, 0, 2).contains("applied"), "Construction Apply failed");
        near(a.type.buildSpeed, baseBuild * 1.3f, "applied build speed");
        near(a.type.buildRange, baseRange * 1.2f, "applied build range");
        check(a.mounts == mounts, "Apply reset weapon state");
        check(b.type == UnitTypes.alpha && b.type.buildSpeed == baseBuild, "another player changed");
        near(UnitTypes.alpha.weapons.first().reload, weaponReload, "shared weapon reload mutated");
        a.isShooting = false;
        for(var mount : a.mounts) mount.reload = 100f;
        a.update();
        near(a.reloadMultiplier, .84f, "native update did not apply rate penalty");
        near(a.mounts[0].reload, 99.16f, "native weapon cooldown not slowed");
        a.update();
        near(a.reloadMultiplier, .84f, "penalty accumulated across ticks");
        a.apply(StatusEffects.overclock, 100f);
        a.update();
        near(a.reloadMultiplier, .84f * StatusEffects.overclock.reloadMultiplier, "status effects not composed");
        String resetResult = apply(alice, 0, 0, 0);
        check(resetResult.contains("applied"), "Construction reset failed: " + resetResult + " dead=" + a.dead + " valid=" + a.isValid() + " power=" + yard.power.status);
        a.update();
        near(a.reloadMultiplier, StatusEffects.overclock.reloadMultiplier, "reset damaged existing status effect");
        a.clearStatuses();
        a.update();
        near(a.reloadMultiplier, 1f, "reset retained reload penalty");
        check(!apply(alice, 0, 0, 5).contains("applied"), "over-budget Construction accepted");
        check(!apply(alice, 0, 0, Integer.MAX_VALUE).contains("applied"), "Construction bounds bypass");
        check(apply(alice, 2, 0, -2).contains("applied"), "deliberate sacrifice/refund rejected");
        a.update();
        near(a.reloadMultiplier, 1f, "negative Construction grants weapon bonus");
        check(apply(alice, 0, 0, 2).contains("applied"), "Construction reapply failed");
        roundTrip(a, false);
        roundTrip(a, true);

        // Exercise the real builder, with resources and without sandbox's unlimited range.
        a = ship(alice, UnitTypes.alpha);
        a.set(64f, 64f);
        check(apply(alice, 0, 0, 0).contains("applied"), "builder baseline reset failed");
        float vanillaProgress = buildProgress(a, 16, 8, 5);
        Vars.world.tile(16, 8).remove();
        a.clearBuilding();
        a = ship(alice, UnitTypes.alpha);
        a.set(64f, 64f);
        check(apply(alice, 0, 0, 2).contains("applied"), "builder upgrade failed");
        float upgradedProgress = buildProgress(a, 16, 8, 5);
        check(upgradedProgress > vanillaProgress * 1.2f, "actual building did not become faster (" + vanillaProgress + " vs " + upgradedProgress + ")");
        Vars.world.tile(16, 8).remove();
        a.clearBuilding();
        int distantX = (int)((a.x + baseRange + 16f) / Vars.tilesize);
        check(apply(alice, 0, 0, 0).contains("applied"), "range baseline reset failed");
        a.addBuild(new BuildPlan(distantX, 8, 0, Blocks.copperWall));
        for(int tick = 0; tick < 5; tick++) a.updateBuildLogic();
        check(Vars.world.tile(distantX, 8).block() == Blocks.air, "vanilla built beyond range");
        a.clearBuilding();
        check(apply(alice, 0, 0, 2).contains("applied"), "range upgrade failed");
        check(buildProgress(a, distantX, 8, 5) > 0f, "extended range not honored by builder");
        a.clearBuilding();
        Unit respawn = ship(alice, UnitTypes.alpha);
        restore.invoke(null, alice);
        near(respawn.type.buildSpeed, baseBuild * 1.3f, "Construction did not restore on respawn");

        // Existing two-column profiles migrate implicitly to Construction zero.
        var file = Vars.dataDirectory.child("modular-shipyard/loadouts-v1.properties");
        Properties profiles = new Properties();
        try(var input = file.read()){ profiles.load(input); }
        profiles.setProperty("construction-test-legacy.alpha", "1,-1");
        profiles.setProperty("construction-test-invalid.alpha", "0,0,99");
        try(var output = file.write(false)){ profiles.store(output, "migration fixtures"); }
        service.getMethod("init").invoke(null);
        Player legacy = LoadoutChecks.player("construction-test-legacy");
        Unit old = ship(legacy, UnitTypes.alpha);
        restore.invoke(null, legacy);
        near(old.type.buildSpeed, baseBuild, "legacy save gained Construction");
        near(old.type.speed, UnitTypes.alpha.speed * 1.1f, "legacy cargo rebalance not applied");
        check(old.itemCapacity() == 24, "legacy cargo not restored");
        check(apply(legacy, 0, 0, 1).contains("applied"), "migrated profile could not save Construction");
        Player invalid = LoadoutChecks.player("construction-test-invalid");
        Unit unchanged = ship(invalid, UnitTypes.alpha);
        restore.invoke(null, invalid);
        check(unchanged.type == UnitTypes.alpha, "corrupt Construction profile applied");
        System.out.println("PASS: 3 registered hulls, 3000 legacy aliases, Construction budgets/refunds, native cooldown/status composition, actual build speed/range, serialization and legacy profiles");
    }

    private static float buildProgress(Unit unit, int x, int y, int ticks){
        unit.addBuild(new BuildPlan(x, y, 0, Blocks.copperWall));
        for(int tick = 0; tick < ticks; tick++) unit.updateBuildLogic();
        check(Vars.world.build(x, y) instanceof ConstructBuild, "builder did not start requested block");
        return ((ConstructBuild)Vars.world.build(x, y)).progress;
    }

    private static String apply(Player player, int speed, int cargo, int construction) throws Exception{
        // Return to the Shipyard to edit, then resume the builder measurement position.
        Unit unit = player.unit();
        float x = unit.x, y = unit.y;
        unit.set(yard);
        try{
            return (String)DockTest.apply(apply, player, yard.pos(), unit.id, speed, cargo, construction);
        }finally{
            unit.set(x, y);
        }
    }

    private static Unit ship(Player player, UnitType type){
        Unit unit = type.create(Team.sharded);
        unit.spawnedByCore = true;
        unit.set(yard);
        unit.add();
        player.unit(unit);
        return unit;
    }

    private static void roundTrip(Unit unit, boolean sync) throws Exception{
        Player owner = unit.getPlayer();
        var bytes = new ByteArrayOutputStream();
        var writer = new Writes(new DataOutputStream(bytes));
        if(sync) unit.writeSync(writer); else unit.write(writer);
        Unit copy = (Unit)EntityMapping.map(unit.classId()).get();
        var reader = new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        if(sync) copy.readSync(reader); else copy.read(reader);
        check(copy.type.id == unit.type.id, "stable hull ID did not survive native codec");
        copy.id = unit.id;
        Vars.mods.getMod("modular-shipyard").loader.loadClass("shipyard.service.UnitLoadouts")
            .getMethod("refreshUnit", Unit.class).invoke(null, copy);
        near(copy.type.buildSpeed, unit.type.buildSpeed, "build speed codec mismatch");
        near(copy.type.buildRange, unit.type.buildRange, "build range codec mismatch");
        // A native read rebinds a present Player controller to the decoded unit.
        if(owner != null) owner.unit(unit);
    }

    private static void near(float actual, float expected, String reason){
        check(Math.abs(actual - expected) < .0002f, reason + " (" + actual + " vs " + expected + ")");
    }

    private static void check(boolean condition, String reason){
        if(!condition) throw new AssertionError(reason);
    }
}
