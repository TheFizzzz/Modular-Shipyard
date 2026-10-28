package shipyard;

import arc.math.*;
import arc.struct.Seq;
import arc.util.Time;
import java.io.PrintWriter;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.units.UnitController;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.UnitType;

/** Role probes: native HP, armour, guns, statuses and collisions; scripted targets, no retaliation. */
final class WeaponPairChecks{
    record Scenario(String name,float range,int count,boolean moving,boolean burst,boolean sweep,boolean lead,boolean spread){}
    static final Scenario[] beam={
        new Scenario("focus-5",40,1,false,false,false,false,false),
        new Scenario("focus-12",96,1,false,false,false,false,false),
        new Scenario("burst-5",40,1,false,true,false,false,false),
        new Scenario("strafe-5",40,1,true,false,false,false,false),
        new Scenario("tight-pack-5",40,6,false,false,false,false,false),
        new Scenario("sweep-pack-5",40,6,false,false,true,false,true)};
    static final Scenario[] blast={
        new Scenario("focus-14",112,1,false,false,false,false,false),
        new Scenario("focus-26",208,1,false,false,false,false,false),
        new Scenario("strafe-18",144,1,true,false,false,false,false),
        new Scenario("lead-strafe-18",144,1,true,false,false,true,false),
        new Scenario("tight-pack-18",144,6,false,false,false,false,false),
        new Scenario("spread-pack-18",144,6,false,false,false,false,true)};
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(160,160,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Vars.state.set(mindustry.core.GameState.State.playing);Time.setDeltaProvider(()->1f);Time.delta=1;
        UnitType[][] targets={{UnitTypes.dagger,UnitTypes.mace,UnitTypes.flare},{UnitTypes.fortress,UnitTypes.zenith},{UnitTypes.scepter,UnitTypes.reign,UnitTypes.antumbra,UnitTypes.eclipse}};
        try(var out=new PrintWriter(Vars.dataDirectory.child("weapon-pairs.csv").file())){
            out.println("version,ship,weapon,target,scenario,count,duration_s,kills,damage,first_kill_s,clear_s,corroded_unit_ticks");
            for(int tier=0;tier<3;tier++)for(UnitType target:targets[tier])for(String token:new String[]{"flamethrower","caustic","missiles","asymptote"}){
                for(Scenario scenario:token.equals("flamethrower")||token.equals("caustic")?beam:blast){
                    simulate(out,tier,target,token,scenario,false);
                    if(token.equals("missiles"))simulate(out,tier,target,token,scenario,true);
                }
            }
        }
        System.out.println("WEAPON PAIR BENCHMARK: "+Vars.dataDirectory.child("weapon-pairs.csv"));
    }
    static void simulate(PrintWriter out,int tier,UnitType type,String token,Scenario s,boolean old)throws Exception{
        for(Unit u:Groups.unit.copy(new Seq<>()))u.remove();Groups.bullet.clear();Time.clear();
        // Isolate transient contact buildup between cases without reloading content.
        arc.Events.fire(new mindustry.game.EventType.ResetEvent());Time.delta=1;
        Unit ship=new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
        ship.set(400,500);ship.rotation=0;freeze(ship);ship.add();ExperimentalChecks.equip(ship,tier,token);
        if(old){var b=ship.mounts[0].weapon.bullet;b.lifetime=55;b.range=55*b.speed;b.fragBullet.lifetime=40;b.fragBullet.range=40*b.fragBullet.speed;}
        Unit[] pack=new Unit[s.count];float[] initial=new float[pack.length],x=new float[pack.length],y=new float[pack.length];
        for(int i=0;i<pack.length;i++){
            pack[i]=type.create(Team.crux);if(type.flying)pack[i].elevation=1;
            // Scale spacing by native hull size: no overlapping heavy enemy hitboxes.
            float spacing=Math.max(type.hitSize+3,s.spread?48:12);
            x[i]=400+s.range+(i/3)*spacing;y[i]=500+(pack.length==1?0:(i%3-1)*spacing);
            pack[i].set(x[i],y[i]);freeze(pack[i]);pack[i].add();initial[i]=pack[i].health;
        }
        int tick=0,first=-1,corroded=0;
        for(;tick<1200 && killed(pack)<pack.length;tick++){
            Time.update();Vars.state.tick++;Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();Groups.bullet.updatePhysics();
            Unit aim=null;int desired=s.sweep?(tick/30)%pack.length:0;
            for(int n=0;n<pack.length;n++){Unit u=pack[(desired+n)%pack.length];if(u.health>0){aim=u;break;}}
            for(int i=0;i<pack.length;i++)if(pack[i].isAdded()){
                Unit u=pack[i];
                if(s.moving){float phase=tick*type.speed/24f;u.vel.set(x[i]-u.x,y[i]+24*Mathf.sin(phase)-u.y);}
                else u.vel.setZero();
                u.update();if(u.hasEffect(StatusEffects.corroded))corroded++;
            }
            boolean firing=!s.burst || tick%60<30;
            if(aim!=null){float ax=aim.x+(s.lead?aim.vel.x*30:0),ay=aim.y+(s.lead?aim.vel.y*30:0);ship.rotation=ship.angleTo(ax,ay);ship.aim(ax,ay);}
            ship.isShooting=firing;ship.controlWeapons(firing,firing);ship.update();
            Groups.bullet.update();Groups.bullet.collide();ExperimentalChecks.exposure.getMethod("update").invoke(null);
            for(Unit u:pack)if(u.health<=0 && u.isAdded())u.remove();
            if(first<0 && killed(pack)>0)first=tick+1;
        }
        float damage=0;for(int i=0;i<pack.length;i++)damage+=initial[i]-Math.max(0,pack[i].health);
        out.printf(java.util.Locale.ROOT,"%s,%s,%s,%s,%s,%d,%.3f,%d,%.3f,%s,%s,%d%n",old?"0.20.1":Vars.mods.getMod("modular-shipyard").meta.version,new String[]{"alpha","beta","gamma"}[tier],token,type.name,s.name,pack.length,tick/60f,killed(pack),damage,first<0?">20":String.format(java.util.Locale.ROOT,"%.3f",first/60f),killed(pack)==pack.length?String.format(java.util.Locale.ROOT,"%.3f",tick/60f):">20",corroded);
    }
    static int killed(Unit[] pack){int n=0;for(Unit u:pack)if(u.health<=0)n++;return n;}
    static void freeze(Unit u){u.controller(new UnitController(){Unit unit;public void unit(Unit v){unit=v;}public Unit unit(){return unit;}});}
}
