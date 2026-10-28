package shipyard;

import java.io.*;
import java.util.Properties;
import arc.util.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.world.Block;
import mindustry.world.blocks.payloads.*;

/** Shared point accounting, authority rejection, old save recovery and actual rail impact damage. */
final class WeaponBudgetChecks{
    static void run(Block block)throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var model=ExperimentalChecks.model;var tiers=ExperimentalChecks.tiers;var kinds=ExperimentalChecks.kinds;
        Object alpha=tiers.getEnumConstants()[0];
        int[] costs={0,2,3,2,4,3,2,1,2,2,2,2,3,4};
        for(int i=0;i<costs.length;i++){
            Object value=model.getMethod("withWeapon",kinds).invoke(ExperimentalChecks.neutral,kinds.getEnumConstants()[i]);
            check((int)model.getMethod("points").invoke(value)==costs[i],"wrong cost for "+kinds.getEnumConstants()[i]);
            check((int)model.getMethod("remainingPoints",tiers).invoke(value,alpha)==6-costs[i],"cost not deducted");
        }
        Object old=model.getMethod("parse",String.class).invoke(null,"3,0,0,0,0,railgun");
        check(!(boolean)model.getMethod("valid",tiers).invoke(old,alpha),"combined stat/weapon budget not enforced");
        Object funded=model.getMethod("withCargo",int.class).invoke(old,-4);
        check((boolean)model.getMethod("valid",tiers).invoke(funded,alpha),"manual sacrifice did not fund weapon");
        Object restored=model.getMethod("forRestore",tiers).invoke(old,alpha);
        check(model.getMethod("encode").invoke(restored).equals("3,0,0,0,0"),"save fallback changed stats or retained unaffordable weapon");

        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;Vars.state.rules.possessionAllowed=true;
        Vars.world.loadGenerator(96,96,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));
            t.getn(20,20).setBlock(block,Team.sharded,0);t.getn(22,20).setBlock(Blocks.powerSource,Team.sharded,0);});
        Unit source=UnitTypes.alpha.create(Team.sharded);source.dead=false;source.spawnedByCore=true;source.set(160,160);source.add();
        Player owner=LoadoutChecks.player("weapon-budget-"+System.nanoTime());owner.add();owner.unit(source);
        var service=loader.loadClass("shipyard.service.LoadoutService");
        var colours=ExperimentalChecks.colours;Object palette=ExperimentalChecks.palette;
        var apply=service.getMethod("apply",Player.class,int.class,int.class,model,colours);
        var yard=Vars.world.build(20,20);yard.power.graph.reflow(yard);yard.power.graph.update();
        String rejection=(String)DockTest.apply(apply,owner,yard.pos(),source.id,old,palette);
        check(!rejection.contains("applied") && rejection.contains("budget"),"authority accepted weapon-budget bypass");
        var profileField=service.getDeclaredField("profiles");profileField.setAccessible(true);
        Properties profiles=(Properties)profileField.get(null);
        String key=owner.uuid()+".alpha",raw="3,0,0,0,0,railgun";
        profiles.setProperty(key,raw);profiles.setProperty(key+".colours","F7E2E1,FF80AB,30EE70");
        service.getMethod("restore",Player.class).invoke(null,owner);
        check(ExperimentalChecks.current(source).equals("standard"),"over-budget profile equipped its old weapon");
        check(profiles.getProperty(key).equals(raw),"old profile was overwritten during migration");
        String name="Old Vector";
        var library=loader.loadClass("shipyard.service.PresetService");
        String token=(String)library.getMethod("token",String.class).invoke(null,name);
        profiles.setProperty(key+".preset."+token,raw+"|F7E2E1,FF80AB,30EE70");
        Object catalog=library.getMethod("catalog",Player.class,tiers).invoke(null,owner,alpha);
        check(((java.util.Map<?,?>)catalog.getClass().getMethod("presets").invoke(catalog)).containsKey(name),"old preset was hidden instead of remaining editable");
        // Occupied saves must retain the real ship, cargo and HP even when a weapon gained a cost.
        var bytes=new ByteArrayOutputStream();var out=new Writes(new DataOutputStream(bytes));
        source.health=77f;source.stack.set(Items.copper,3);
        out.i(source.id);out.str(raw);out.str("F7E2E1,FF80AB,30EE70");Payload.write(new UnitPayload(source),out);
        Unit held=(Unit)loader.loadClass("shipyard.service.ShipyardSessions").getMethod("readShip",Reads.class)
            .invoke(null,new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        near(held.health,77f,"held save HP");check(held.stack.amount==3,"held save cargo");
        check(ExperimentalChecks.current(held).equals("standard"),"occupied save did not drop unaffordable weapon");
        source.remove();

        for(int tier=0;tier<3;tier++){
            Unit ship=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            ship.set(120,500);ship.rotation=0;ship.dead=false;ship.spawnedByCore=true;ship.add();ExperimentalChecks.equip(ship,tier,"railgun");
            var shot=ship.mounts[0].weapon.bullet;float full=shot.damage;
            for(var type:new mindustry.type.UnitType[]{UnitTypes.dagger,UnitTypes.flare})for(float distance:new float[]{32,64,128,192,288}){
                Unit target=ExperimentalChecks.enemy(type,ship.x+distance,ship.y);Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();
                shot.create(ship,ship.team,ship.x,ship.y,0f);
                float expected=full*(.4f+.6f*arc.math.Mathf.clamp((distance-64)/128));
                near(10000-target.health,expected,"Vector ground/air distance "+distance);target.remove();
            }
            for(int x:new int[]{22,39}){
                Vars.world.tile(x,62).setBlock(Blocks.copperWallLarge,Team.crux,0);var wall=Vars.world.build(x,62);
                float before=wall.health,dy=wall.y;ship.y=dy;Vars.state.teams.updateTeamStats();
                float distance=ship.dst(wall);shot.create(ship,ship.team,ship.x,ship.y,0);
                near(before-wall.health,full*(.4f+.6f*arc.math.Mathf.clamp((distance-64)/128)),"Vector building distance");wall.tile.remove();
            }
            near(shot.damage,full,"distance damage mutated the projectile template");ship.remove();
        }
        System.out.println("PASS: weapon costs/refunds/shared budgets, authoritative rejection, editable old presets and occupied/profile recovery, Vector close/mid/far unit/building damage on all tiers");
    }
    static void check(boolean ok,String why){ExperimentalChecks.check(ok,why);}
    static void near(float a,float b,String why){ExperimentalChecks.near(a,b,why);}
}
