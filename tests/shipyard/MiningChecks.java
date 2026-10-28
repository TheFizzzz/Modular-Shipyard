package shipyard;

import arc.Events;
import arc.util.Time;
import java.lang.reflect.Method;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.io.SaveVersion;
import mindustry.world.Block;

/** Real mining ticks plus disk and join-state codecs; no mock UnitType implementation. */
final class MiningChecks{
    static void run(Block block) throws Exception{
        var loader = Vars.mods.getMod("modular-shipyard").loader;
        Method apply = loader.loadClass("shipyard.service.LoadoutService").getMethod("apply", Player.class,
            int.class, int.class, int.class, int.class, int.class);
        Vars.world.loadGenerator(80, 80, tiles -> {
            tiles.fill();
            tiles.eachTile(tile -> tile.setFloor(Blocks.stone.asFloor()));
            tiles.getn(8, 8).setBlock(Blocks.coreShard, Team.sharded, 0);
            tiles.getn(40, 40).setBlock(block, Team.sharded, 0);
            tiles.getn(42, 40).setBlock(Blocks.powerSource, Team.sharded, 0);
            tiles.getn(40, 42).setOverlay(Blocks.oreCopper);
        });
        Vars.state.rules.env = mindustry.world.meta.Env.terrestrial;
        var yard = Vars.world.build(40, 40);
        yard.power.graph.reflow(yard); yard.power.graph.update();
        Player player = LoadoutChecks.player("mining-integration");
        player.add();
        Unit unit = UnitTypes.alpha.create(Team.sharded);
        unit.spawnedByCore = true; unit.set(yard); unit.add(); player.unit(unit);
        float baseMining = UnitTypes.alpha.mineSpeed;
        int tier = UnitTypes.alpha.mineTier;
        check(((String)DockTest.apply(apply, player, yard.pos(), unit.id, 0, 2, 0)).contains("applied"), "combined Apply rejected");
        near(unit.type.mineSpeed, baseMining * 1.3f, "mining scaling");
        near(unit.type.speed, UnitTypes.alpha.speed * .9f, "combined speed penalty");
        check(unit.itemCapacity() == 42 && unit.type.mineTier == tier, "capacity or mining tier changed");
        check(!unit.canMine(Items.thorium), "new resource unlocked");
        check(unit.type.mineRange == UnitTypes.alpha.mineRange && unit.type.mineHardnessScaling == UnitTypes.alpha.mineHardnessScaling,
            "unrequested mining properties changed");
        Time.delta = 1f;
        unit.mineTile = Vars.world.tile(40, 42);
        unit.mineTimer = 0f;
        unit.update();
        near(unit.mineTimer, baseMining * 1.3f, "native mining tick ignored setting");
        unit.mineTile = null;
        unit.type = Vars.content.unit("modular-shipyard-ship-alpha-hull");
        Events.fire(Trigger.update);
        near(unit.type.mineSpeed, baseMining * 1.3f, "snapshot reset not repaired before entity update");
        int id = unit.id;
        var file = Vars.dataDirectory.child("mining-roundtrip.msav");
        SaveIO.write(file);
        if(Vars.logic == null) Vars.logic = new mindustry.core.Logic();
        SaveIO.load(file);
        Unit restored = Groups.unit.getByID(id);
        check(restored != null, "ship lost in disk save");
        near(restored.type.mineSpeed, baseMining * 1.3f, "disk custom chunk lost mining");
        check(restored.itemCapacity() == 42, "disk custom chunk lost cargo");

        // The same custom-chunk codec is included by NetworkIO.writeWorld for late joiners.
        var bytes = new java.io.ByteArrayOutputStream();
        SaveIO.getVersion().writeCustomChunks(new java.io.DataOutputStream(bytes), true);
        Events.fire(new mindustry.game.EventType.ResetEvent());
        restored.type = Vars.content.unit("modular-shipyard-ship-alpha-hull");
        SaveIO.getVersion().readCustomChunks(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        near(restored.type.mineSpeed, baseMining * 1.3f, "join custom chunk lost mining");
        check(Vars.content.units().count(type -> type.name.startsWith("modular-shipyard-ship-")) == 3,
            "applying settings registered content");
        SaveIO.load(new arc.files.Fi("../../tests/fixtures/shipyard-v0.3.1.msav"));
        Unit migrated = Groups.unit.find(ship -> ship.type.name.equals("modular-shipyard-ship-alpha-hull"));
        check(migrated != null, "v0.3.1 ship name did not migrate");
        near(migrated.type.mineSpeed, baseMining * 1.15f, "legacy cargo step not carried into mining");
        near(migrated.type.buildSpeed, UnitTypes.alpha.buildSpeed * 1.15f, "legacy construction lost");
        check(migrated.itemCapacity() == 36, "legacy cargo lost");
        int migratedId = migrated.id;
        SaveIO.write(file);
        SaveIO.load(file);
        near(Groups.unit.getByID(migratedId).type.mineSpeed, baseMining * 1.15f, "migrated state not persisted");
        System.out.println("PASS: v0.3.1 binary save migrates and resaves with per-unit state");
        System.out.println("PASS: combined native mining/cargo, unchanged mining tier, snapshot repair, full disk save and join chunk round trips; three registered hulls");
    }
    private static void near(float actual, float expected, String reason){ check(Math.abs(actual - expected) < .0002f, reason); }
    private static void check(boolean condition, String reason){ if(!condition) throw new AssertionError(reason); }
}
