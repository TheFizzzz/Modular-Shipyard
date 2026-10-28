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

/** Optimistic group throughput: six aligned stationary targets, no retaliation or travel delay. */
final class PackTtkChecks{
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=Env.terrestrial;Vars.state.rules.unitCap=10000;
        Vars.world.loadGenerator(128,128,tiles->{tiles.fill();tiles.eachTile(t->t.setFloor(Blocks.stone.asFloor()));});
        Time.setDeltaProvider(()->1f);Time.delta=1f;
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var tiers=loader.loadClass("shipyard.model.ShipTier");var weapons=loader.loadClass("shipyard.model.ShipWeapon");
        var model=loader.loadClass("shipyard.model.ShipLoadout");var colours=loader.loadClass("shipyard.model.ShipCosmetics");
        var get=loader.loadClass("shipyard.content.ShipVariants").getMethod("get",tiers,model,colours);
        Object neutral=model.getField("defaults").get(null),palette=colours.getField("defaults").get(null);
        UnitType[] bases={UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma};
        UnitType[] enemies={UnitTypes.dagger,UnitTypes.mace,UnitTypes.fortress,UnitTypes.scepter,UnitTypes.reign};
        try(var out=new PrintWriter(Vars.dataDirectory.child("pack-ttk.csv").file())){
            out.println("ship,weapon,target,fire_rate_step,count,kills_at_30s,kills_at_60s,killed,clear_seconds");
            for(int t=0;t<3;t++)for(Object kind:weapons.getEnumConstants())for(UnitType enemy:enemies)for(int fire:new int[]{0,5}){
                for(Unit old:Groups.unit.copy(new arc.struct.Seq<>()))old.remove();Groups.bullet.clear();Time.clear();
                if((boolean)weapons.getMethod("utility").invoke(kind))continue;
                int cost=(int)weapons.getMethod("cost").invoke(kind);
                int needed=Math.max(0,2*fire+cost-new int[]{6,10,14}[t]);
                Object loadout=model.getConstructor(int.class,int.class,int.class,int.class,int.class)
                    .newInstance(0,-Math.min(4,needed),-Math.max(0,needed-4),0,fire);
                loadout=model.getMethod("withWeapon",weapons).invoke(loadout,kind);
                if(!(boolean)model.getMethod("valid",tiers).invoke(loadout,tiers.getEnumConstants()[t]))throw new AssertionError("invalid benchmark loadout");
                Unit ship=bases[t].create(Team.sharded);ship.setType((UnitType)get.invoke(null,tiers.getEnumConstants()[t],loadout,palette));
                if(System.getenv("SHIPYARD_PACK_UNBOUNDED")!=null && java.util.Set.of("flamethrower","lancer","fuse","railgun").contains(kind.toString())){
                    for(var gun:ship.type.weapons)gun.bullet.pierceCap=-1;
                    if(t==0 && kind.toString().equals("railgun"))ship.type.weapons.first().bullet.damage*=5f/3f;
                }
                ship.set(320,320);ship.rotation=0;freeze(ship);ship.add();
                loader.loadClass("shipyard.content.ShipWeapons").getMethod("bind",Unit.class).invoke(null,ship);
                Unit[] pack=new Unit[6];for(int j=0;j<pack.length;j++){pack[j]=enemy.create(Team.crux);pack[j].set(360+j*18f,320);freeze(pack[j]);pack[j].add();}
                Vars.state.teams.updateTeamStats();int tick=0,k30=0,k60=0;
                for(;tick<7200 && killed(pack)<6;tick++){
                    Unit target=null;for(Unit candidate:pack)if(candidate.health>0){target=candidate;break;}
                    if(target==null)break;
                    ship.set(target.x-40,target.y);ship.rotation=0;
                    Time.update();Groups.bullet.updatePhysics();Groups.unit.updatePhysics();
                    ship.aim(target);ship.isShooting=true;ship.controlWeapons(true,true);ship.update();
                    for(Unit unit:pack)if(unit.isAdded())unit.update();Groups.bullet.update();Groups.bullet.collide();
                    for(Unit unit:pack)if(unit.health<=0 && unit.isAdded())unit.remove();
                    Vars.state.teams.updateTeamStats();
                    if(tick<1800)k30=killed(pack);if(tick<3600)k60=killed(pack);
                }
                out.printf(java.util.Locale.ROOT,"%s,%s,%s,%d,6,%d,%d,%d,%s%n",bases[t].name,kind,enemy.name,fire,k30,k60,killed(pack),tick>=7200?">120":String.format(java.util.Locale.ROOT,"%.3f",tick/60f));
            }
        }
        System.out.println("PACK TTK written: "+Vars.dataDirectory.child("pack-ttk.csv"));
    }
    private static int killed(Unit[] pack){int count=0;for(Unit unit:pack)if(unit.health<=0)count++;return count;}
    private static void freeze(Unit target){target.controller(new UnitController(){Unit unit;public void unit(Unit u){unit=u;}public Unit unit(){return unit;}});}
}
