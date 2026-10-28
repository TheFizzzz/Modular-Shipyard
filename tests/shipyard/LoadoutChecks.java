package shipyard;

import arc.util.io.Reads;
import arc.util.io.Writes;
import java.io.*;
import java.lang.reflect.Method;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.NetClient;
import mindustry.core.NetServer;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.mod.Mod;
import mindustry.net.NetConnection;
import mindustry.type.UnitType;
import mindustry.world.Block;

/** Tests the actual jar via its own classloader, including the native unit codecs. */
final class LoadoutChecks{
    private static Class<?> service;
    private static Method apply;
    private static Method restore;
    private static Building yard;

    static void run(Block block) throws Exception{
        Vars.netServer = new NetServer();
        Vars.netClient = new NetClient();
        Vars.mods.eachClass(Mod::init);
        service = Vars.mods.getMod("modular-shipyard").loader.loadClass("shipyard.service.LoadoutService");
        apply = service.getMethod("apply", Player.class, int.class, int.class, int.class, int.class);
        restore = service.getMethod("restore", Player.class);
        Vars.world.tile(12, 12).setBlock(block, Team.sharded, 0);
        yard = Vars.world.build(12, 12);
        Vars.world.tile(14, 12).setBlock(Blocks.powerSource, Team.sharded, 0);
        power();
        float baseSpeed = UnitTypes.alpha.speed;
        int baseCapacity = UnitTypes.alpha.itemCapacity;
        int weapons = UnitTypes.alpha.weapons.size, engines = UnitTypes.alpha.engines.size;
        for(UnitType base : new UnitType[]{UnitTypes.alpha, UnitTypes.beta, UnitTypes.gamma}){
            for(int speed = -4; speed <= 5; speed++){
                for(int cargo = -4; cargo <= 5; cargo++){
                    UnitType variant = Vars.content.unit("modular-shipyard-ship-" + base.name + "-s" + (speed + 4) + "-c" + (cargo + 4));
                    check(variant != null && variant.hidden, "variant missing or exposed");
                    check(variant.constructor == base.constructor && variant.weapons == base.weapons, "baseline behavior differs");
                    near(variant.speed, base.speed * (1f + speed * .1f - Math.max(0, cargo) * .05f), "variant speed formula");
                    check(variant.itemCapacity == Math.round(base.itemCapacity * (1f + cargo * .2f)), "capacity formula");
                }
            }
        }
        Player alice = player("loadout-test-alice");
        Player bob = player("loadout-test-bob");
        Unit a = ship(alice, UnitTypes.alpha);
        Unit b = ship(bob, UnitTypes.alpha);
        float health = a.health;
        var mounts = a.mounts;
        String result = apply(alice, 1, 1);
        check(result.equals("Loadout applied and saved."), result);
        UnitType customized = a.type;
        near(a.type.speed, baseSpeed * 1.05f, "cargo drag not applied");
        check(a.itemCapacity() == 36 && a.maxAccepted(Items.copper) == 36, "cargo mechanics unchanged");
        check(a.health == health && a.mounts == mounts && a.controller() == alice, "apply reset ship state");
        check(b.type == UnitTypes.alpha && b.itemCapacity() == baseCapacity, "other player changed");
        near(UnitTypes.alpha.speed, baseSpeed, "vanilla speed mutated");
        check(UnitTypes.alpha.itemCapacity == baseCapacity && UnitTypes.alpha.weapons.size == weapons
            && UnitTypes.alpha.engines.size == engines, "vanilla content mutated");
        check(alice.team().data().countType(customized) == 1, "variant count not maintained");
        check(!apply(alice, 5, 5).contains("applied"), "over-budget accepted");
        check(!apply(alice, Integer.MAX_VALUE, 0).contains("applied"), "out-of-range accepted");
        check(a.type == customized, "rejected loadout changed unit");
        String forged = (String)DockTest.apply(apply, alice, yard.pos(), b.id, 0, 0);
        check(!forged.contains("applied") && b.type == UnitTypes.alpha, "foreign unit request accepted");
        yard.power.status = 0f;
        check(!apply(alice, 0, 0).contains("applied"), "unpowered Apply accepted");
        power();
        a.stack.set(Items.copper, 35);
        check(apply(alice, 0, 0).startsWith("Unload"), "cargo truncation allowed");
        check(a.stack.amount == 35 && a.type == customized, "cargo lost on rejection");
        a.stack.amount = 0;
        check(apply(alice, 0, 0).contains("applied"), "reset failed");
        check(a.type == UnitTypes.alpha, "default did not restore vanilla type");
        check(alice.team().data().countType(customized) == 0, "variant count leaked");
        check(apply(alice, 1, 1).contains("applied"), "reapply failed");
        a.stack.set(Items.copper, 35);
        roundTrip(a, false);
        roundTrip(a, true);
        a.stack.amount = 0;

        // Exercise the registered server packet handler using connection-derived identity.
        NetServer.serverPacketReliable(bob, "modular-shipyard-apply-v4", yard.pos() + "," + a.id + ",1,1,0,0,0,-,-,-");
        check(b.type == UnitTypes.alpha, "packet changed sender to another player's ship");
        NetServer.serverPacketReliable(alice, "modular-shipyard-apply-v4", "malformed");

        Unit beta = ship(alice, UnitTypes.beta);
        check(apply(alice, 3, 1).contains("applied"), "Beta config rejected");
        Unit gamma = ship(alice, UnitTypes.gamma);
        check(apply(alice, 5, 2).contains("applied"), "Gamma config rejected");
        Unit alphaRespawn = ship(alice, UnitTypes.alpha);
        restore.invoke(null, alice);
        check(alphaRespawn.type.id == customized.id && alphaRespawn.itemCapacity() == customized.itemCapacity, "downgrade/respawn did not restore separate Alpha loadout");
        Unit bobRespawn = ship(bob, UnitTypes.alpha);
        restore.invoke(null, bob);
        check(bobRespawn.type == UnitTypes.alpha, "another player's saved loadout restored");
        // Re-read from disk and rejoin with a new Player object but the same server UUID.
        service.getMethod("init").invoke(null);
        Player reconnect = player("loadout-test-alice");
        Unit rejoinedBeta = ship(reconnect, UnitTypes.beta);
        restore.invoke(null, reconnect);
        check(rejoinedBeta.type.id == beta.type.id && rejoinedBeta.itemCapacity() == beta.itemCapacity(), "disk/reconnect tier restoration failed");
        Unit unsupported = ship(reconnect, UnitTypes.evoke);
        restore.invoke(null, reconnect);
        check(unsupported.type == UnitTypes.evoke, "Erekir unit modified");
        System.out.println("PASS: 300 legacy speed/cargo aliases, trade-offs, budgets, independent players, cargo safety, Apply/reset, respawn, downgrade, disk/reconnect, native hull codecs plus per-unit restoration");
    }

    private static void roundTrip(Unit original, boolean sync) throws Exception{
        var output = new ByteArrayOutputStream();
        Writes writer = new Writes(new DataOutputStream(output));
        if(sync) original.writeSync(writer); else original.write(writer);
        Unit copy = (Unit)EntityMapping.map(original.classId()).get();
        Reads reader = new Reads(new DataInputStream(new ByteArrayInputStream(output.toByteArray())));
        if(sync){ copy.readSync(reader); }
        else{ copy.read(reader); }
        check(copy.type.id == original.type.id, "stable hull ID codec mismatch");
        copy.id = original.id;
        Vars.mods.getMod("modular-shipyard").loader.loadClass("shipyard.service.UnitLoadouts")
            .getMethod("refreshUnit", Unit.class).invoke(null, copy);
        check(copy.itemCapacity() == original.itemCapacity(), "type/capacity codec mismatch sync=" + sync + " original=" + original.type + "/" + original.itemCapacity() + " copy=" + copy.type + "/" + copy.itemCapacity());
        near(copy.type.speed, original.type.speed, "speed codec mismatch");
        check(copy.stack.amount == original.stack.amount, "cargo did not survive codec");
    }

    private static void power(){
        yard.power.graph.reflow(yard);
        yard.power.graph.update();
    }

    private static String apply(Player player, int speed, int cargo) throws Exception{
        return (String)DockTest.apply(apply, player, yard.pos(), player.unit().id, speed, cargo);
    }

    static Player player(String uuid){
        Player player = Player.create();
        player.team(Team.sharded);
        player.con = new NetConnection("127.0.0.1"){
            public void send(Object packet, boolean reliable){}
            public void close(){}
        };
        player.con.uuid = uuid;
        return player;
    }

    private static Unit ship(Player player, UnitType type){
        Unit ship = type.create(Team.sharded);
        ship.spawnedByCore = true;
        ship.set(yard);
        ship.add();
        player.unit(ship);
        return ship;
    }

    private static void near(float actual, float expected, String reason){
        check(Math.abs(actual - expected) < .0001f, reason);
    }

    private static void check(boolean value, String reason){
        if(!value) throw new AssertionError(reason);
    }
}
