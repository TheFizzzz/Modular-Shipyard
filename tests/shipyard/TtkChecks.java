package shipyard;

import arc.util.Time;
import java.io.PrintWriter;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.units.UnitController;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.world.meta.Env;

/** Native 60 Hz firing, charge queues, projectiles, armor, statuses and collision against stationary targets. */
final class TtkChecks{
    static void run() throws Exception{
        Vars.logic.reset();Vars.state.rules.env=Env.terrestrial;Vars.state.rules.unitCap=10000;
        Vars.world.loadGenerator(128,128,tiles->{tiles.fill();tiles.eachTile(t->t.setFloor(Blocks.stone.asFloor()));});
        Time.setDeltaProvider(()->1f);Time.delta=1f;
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var tiers=loader.loadClass("shipyard.model.ShipTier");
        var weapons=loader.loadClass("shipyard.model.ShipWeapon");
        var model=loader.loadClass("shipyard.model.ShipLoadout");
        var colours=loader.loadClass("shipyard.model.ShipCosmetics");
        var variants=loader.loadClass("shipyard.content.ShipVariants");
        var get=variants.getMethod("get",tiers,model,colours);
        Object neutral=model.getField("defaults").get(null),palette=colours.getField("defaults").get(null);
        UnitType[] bases={UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma};
        UnitType[] enemies={UnitTypes.dagger,UnitTypes.mace,UnitTypes.fortress,UnitTypes.scepter,UnitTypes.reign,
            UnitTypes.flare,UnitTypes.horizon,UnitTypes.zenith,UnitTypes.antumbra,UnitTypes.eclipse};
        try(var out=new PrintWriter(Vars.dataDirectory.child("ttk.csv").file())){
            out.println("ship,weapon,target,health,armor,distance_tiles,ttk_seconds");
            for(int tier=0;tier<3;tier++)for(Object kind:weapons.getEnumConstants())for(UnitType enemy:enemies){
                for(Unit unit:Groups.unit.copy(new arc.struct.Seq<>()))unit.remove();Groups.bullet.clear();Time.clear();
                if((boolean)weapons.getMethod("utility").invoke(kind))continue;
                Object loadout=model.getMethod("withWeapon",weapons).invoke(neutral,kind);
                UnitType view=(UnitType)get.invoke(null,tiers.getEnumConstants()[tier],loadout,palette);
                Unit ship=bases[tier].create(Team.sharded);ship.setType(view);ship.set(320,320);ship.rotation=0;freeze(ship);ship.add();
                loader.loadClass("shipyard.content.ShipWeapons").getMethod("bind",Unit.class).invoke(null,ship);
                Unit target=enemy.create(Team.crux);target.set(360,320);freeze(target);target.add();
                Vars.state.teams.updateTeamStats();
                int tick=0;
                for(;tick<7200 && target.health>0;tick++){
                    Time.update();Groups.bullet.updatePhysics();Groups.unit.updatePhysics();
                    ship.rotation=0;ship.aim(target);ship.isShooting=true;ship.controlWeapons(true,true);
                    ship.update();target.update();Groups.bullet.update();Groups.bullet.collide();
                }
                out.printf(java.util.Locale.ROOT,"%s,%s,%s,%.0f,%.0f,5,%s%n",bases[tier].name,kind,enemy.name,enemy.health,enemy.armor,
                    tick>=7200?">120":String.format(java.util.Locale.ROOT,"%.3f",tick/60f));
            }
        }
        System.out.println("TTK benchmark written: "+Vars.dataDirectory.child("ttk.csv"));
    }
    private static void freeze(Unit target){target.controller(new UnitController(){Unit unit;public void unit(Unit u){unit=u;}public Unit unit(){return unit;}});}
}
