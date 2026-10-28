package shipyard;

import arc.Events;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.*;
import mindustry.input.InputHandler;
import mindustry.io.SaveIO;
import mindustry.world.Block;
import mindustry.world.blocks.ControlBlock;
import mindustry.world.meta.Env;

/** Dock ownership, payload preservation, recovery and native block-unit serialization. */
final class SessionChecks{
    static void run(Block block) throws Exception{
        Vars.logic.reset();Vars.state.rules.env=Env.terrestrial;Vars.state.rules.possessionAllowed=true;
        Vars.world.loadGenerator(48,48,tiles->{tiles.fill();tiles.eachTile(t->t.setFloor(Blocks.stone.asFloor()));
            tiles.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);
            tiles.getn(24,24).setBlock(block,Team.sharded,0);tiles.getn(26,24).setBlock(Blocks.powerSource,Team.sharded,0);});
        Building yard=Vars.world.build(24,24);power(yard);
        Player alice=LoadoutChecks.player("dock-alice");alice.add();
        Player bob=LoadoutChecks.player("dock-bob");bob.add();
        Unit original=ship(alice,yard);original.health=73f;original.stack.set(Items.copper,17);
        Unit other=ship(bob,yard);
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var model=loader.loadClass("shipyard.model.ShipLoadout");var colours=loader.loadClass("shipyard.model.ShipCosmetics");
        var apply=loader.loadClass("shipyard.service.LoadoutService").getMethod("apply",Player.class,int.class,int.class,model,colours);
        Object neutral=model.getField("defaults").get(null),palette=colours.getMethod("parse",String.class).invoke(null,"F7E2E1,FF4488,30EE70");
        var kinds=loader.loadClass("shipyard.model.ShipWeapon");
        neutral=model.getMethod("withWeapon",kinds).invoke(neutral,kinds.getEnumConstants()[6]);
        check(!((String)apply.invoke(null,alice,yard.pos(),original.id,neutral,palette)).contains("applied"),"unpossessed Apply succeeded");
        Vars.state.rules.possessionAllowed=false;check(!yard.canControlSelect(original),"possession rule ignored");Vars.state.rules.possessionAllowed=true;
        InputHandler.buildingControlSelect(alice,yard);
        check(alice.unit()==((ControlBlock)yard).unit() && !original.isAdded() && !original.dead,"original ship was not absorbed safely");
        check(!yard.canControlSelect(other),"occupied Shipyard accepted another player");
        check(!((String)apply.invoke(null,bob,yard.pos(),original.id,neutral,palette)).contains("applied"),"foreign owner applied loadout");
        check(((String)apply.invoke(null,alice,yard.pos(),original.id,neutral,palette)).contains("applied"),"docked Apply failed");
        check(field(yard,"ship")==original && original.health==73f && original.stack.amount==17,"Apply replaced docked ship/cargo/HP");
        var bytes=new java.io.ByteArrayOutputStream();mindustry.io.TypeIO.writeUnit(new arc.util.io.Writes(new java.io.DataOutputStream(bytes)),alice.unit());
        Unit decoded=mindustry.io.TypeIO.readUnit(new arc.util.io.Reads(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))));
        check(decoded==alice.unit(),"native block-unit codec failed");
        int id=original.id;
        var file=Vars.dataDirectory.child("occupied-shipyard.msav");SaveIO.write(file);SaveIO.load(file);
        yard=Vars.world.build(24,24);power(yard);original=(Unit)field(yard,"ship");
        check(original!=null && original.id==id && original.health==73f && original.stack.amount==17,"occupied save lost docked ship state");
        check(!original.isAdded(),"occupied save duplicated the ship in the world");
        Object restored=loader.loadClass("shipyard.content.ShipVariants").getMethod("current",mindustry.type.UnitType.class).invoke(null,original.type);
        check(restored.equals(neutral),"occupied save lost custom weapon");
        check(original.mounts[0].weapon.bullet instanceof mindustry.entities.bullet.MissileBulletType,"occupied save lost private gun");
        yard.getClass().getMethod("release",Player.class).invoke(yard,alice);
        check(alice.unit()==original && original.isAdded() && original.spawnedByCore,"save recovery did not return the stored ship");
        original.setType(Vars.content.unit("modular-shipyard-ship-alpha-hull"));
        Events.fire(mindustry.game.EventType.Trigger.update);
        restored=loader.loadClass("shipyard.content.ShipVariants").getMethod("current",mindustry.type.UnitType.class).invoke(null,original.type);
        check(restored.equals(neutral),"released save payload lost its loadout after native snapshot");
        // Player list is not part of the world save; explicitly re-add this test connection.
        if(!alice.isAdded())alice.add();
        original.set(yard);InputHandler.buildingControlSelect(alice,yard);
        Events.fire(new PlayerLeave(alice));alice.remove();
        check(field(yard,"ship")==original && (int)field(yard,"ownerId")==-1,"disconnect discarded docked cargo");
        Player reconnect=LoadoutChecks.player("dock-alice");reconnect.add();Unit replacement=ship(reconnect,yard);
        yard.updateTile();
        check(reconnect.unit()==original && !replacement.isAdded() && original.health==73f,"reconnect failed or duplicated core ship");
        original.set(yard);InputHandler.buildingControlSelect(reconnect,yard);yard.power.status=0;yard.updateTile();
        check(reconnect.unit()==original && original.isAdded() && original.stack.amount==17,"power loss failed to return ship");power(yard);
        original.set(yard);InputHandler.buildingControlSelect(reconnect,yard);InputHandler.unitClear(reconnect);yard.updateTile();
        check(reconnect.unit()==original && original.isAdded(),"native clear-control did not return original ship");
        original.set(yard);InputHandler.buildingControlSelect(reconnect,yard);yard.changeTeam(Team.crux);yard.updateTile();
        check(reconnect.unit()==original && yard.team==Team.crux,"capture stranded owner or reverted building team");
        yard.changeTeam(Team.sharded);power(yard);
        original.set(yard);InputHandler.buildingControlSelect(reconnect,yard);yard.tile.remove();
        check(reconnect.unit()==original && original.isAdded() && original.health==73f,"removed Shipyard stranded its owner");
        System.out.println("PASS: possession-only Apply, real BlockUnit codec, occupied exclusivity, exact cargo/HP, occupied save/reload, disconnect/reconnect, power loss, native control exit and block removal");
    }
    private static Unit ship(Player player,Building yard){Unit ship=UnitTypes.alpha.create(Team.sharded);ship.spawnedByCore=true;ship.set(yard.x,yard.y-24);ship.add();player.unit(ship);return ship;}
    private static Object field(Building yard,String name)throws Exception{return yard.getClass().getField(name).get(yard);}
    private static void power(Building yard){yard.power.graph.reflow(yard);yard.power.graph.update();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
