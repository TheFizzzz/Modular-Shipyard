package shipyard;

import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Map;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.Predict;
import mindustry.entities.units.UnitController;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.UnitType;

/** Moving player versus native return fire, real HP/death, old/new corrosion payoff. */
final class MovingCombatChecks{
    static Field contacts,remaining;
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(180,180,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Vars.state.set(mindustry.core.GameState.State.playing);Time.setDeltaProvider(()->1f);Time.delta=1;
        contacts=ExperimentalChecks.exposure.getDeclaredField("contacts");contacts.setAccessible(true);
        var nested=ExperimentalChecks.exposure.getDeclaredClasses()[0];remaining=nested.getDeclaredField("penetrationLeft");remaining.setAccessible(true);
        UnitType[][] targets={{UnitTypes.dagger,UnitTypes.mace,UnitTypes.flare},{UnitTypes.mace,UnitTypes.fortress,UnitTypes.zenith},{UnitTypes.fortress,UnitTypes.scepter,UnitTypes.reign,UnitTypes.antumbra,UnitTypes.eclipse}};
        try(var out=new PrintWriter(Vars.dataDirectory.child("moving-combat.csv").file())){
            out.println("ship,weapon,penetration,loadout,target,range_tiles,harassers,fire_pattern,duration_s,player_alive,player_hp_lost,target_killed,target_hp_lost,target_shield_left,harassers_killed,corroded_ticks,penetration_ticks,moving_ticks,average_speed,enemy_shots,target_armor,beam_damage,aim_strategy,movement");
            for(int tier=0;tier<3;tier++)for(UnitType type:targets[tier])for(boolean defensive:new boolean[]{false,true})for(int pressure:new int[]{3,10})for(boolean interrupted:new boolean[]{false,true})for(boolean weave:new boolean[]{false,true}){
                simulate(out,tier,type,"flamethrower",false,defensive,pressure,interrupted,40,false,weave);
                simulate(out,tier,type,"flamethrower",false,defensive,pressure,interrupted,40,true,weave);
                for(float range:new float[]{40,96})for(boolean old:new boolean[]{true,false})simulate(out,tier,type,"caustic",old,defensive,pressure,interrupted,range,false,weave);
            }
        }
        System.out.println("MOVING COMBAT BENCHMARK: "+Vars.dataDirectory.child("moving-combat.csv"));
    }
    static void simulate(PrintWriter out,int tier,UnitType type,String token,boolean old,boolean defensive,int pressure,boolean interrupted,float range,boolean compensated,boolean weave)throws Exception{
        for(Unit u:Groups.unit.copy(new Seq<>()))u.remove();Groups.bullet.clear();Time.clear();Events.fire(new mindustry.game.EventType.ResetEvent());Time.delta=1;Vars.state.tick=0;Mathf.rand.setSeed(2489);
        Unit ship=new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
        Object value=ExperimentalChecks.model.getMethod("parse",String.class).invoke(null,(defensive?"0,-4,-2,5,0,":"0,0,0,0,0,")+token);
        ship.setType((UnitType)ExperimentalChecks.get.invoke(null,ExperimentalChecks.tiers.getEnumConstants()[tier],value,ExperimentalChecks.palette));
        ExperimentalChecks.bind.invoke(null,ship);ship.set(650+range,650);ship.elevation=1;ship.heal();freeze(ship);ship.add();
        ship.vel.set(0,Math.min(2.3f,ship.type.speed*.8f)); // Enter the encounter already moving.
        Unit focus=type.create(Team.crux);focus.set(650,650);if(type.flying)focus.elevation=1;freeze(focus);focus.add();float startHP=focus.health;
        Unit[] enemies=new Unit[pressure+1];enemies[0]=focus;
        for(int i=0;i<pressure;i++){Unit u=UnitTypes.dagger.create(Team.crux);float a=i*(Mathf.pi*2)/pressure+.31f;u.set(650+128*Mathf.cos(a),650+128*Mathf.sin(a));freeze(u);u.add();enemies[i+1]=u;}
        int tick=0,corroded=0,pen=0,moving=0;float distance=0,incoming=0,phase=0;
        var multiplier=ExperimentalChecks.exposure.getMethod("armorMultiplier",Unit.class,Unit.class);
        for(;tick<1200 && ship.health>0 && focus.health>0;tick++){
            Time.update();Vars.state.tick++;Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();Groups.bullet.updatePhysics();
            phase+=(weave && tick/45%2==1?-1:1)*Math.min(2.3f,ship.type.speed*.8f)/range;
            float beforeX=ship.x,beforeY=ship.y,beforeHP=ship.health;
            float vx=ship.vel.x,vy=ship.vel.y;
            float radius=range+(weave?5*Mathf.sin(tick*.07f):0);
            ship.vel.set(focus.x+radius*Mathf.cos(phase)-ship.x,focus.y+radius*Mathf.sin(phase)-ship.y).limit(ship.speed());
            // Bound steering acceleration instead of teleporting velocity at a reversal.
            ship.vel.sub(vx,vy).limit(ship.type.accel*ship.type.speed).add(vx,vy);
            if(compensated){
                var bullet=ship.mounts[0].weapon.bullet;float inherited=bullet.keepVelocity?1:0;
                var aim=Predict.intercept(ship.x,ship.y,focus.x,focus.y,focus.vel.x-ship.vel.x*inherited,focus.vel.y-ship.vel.y*inherited,bullet.speed);
                ship.rotation=ship.angleTo(aim);ship.aim(aim);
            }else{ship.rotation=ship.angleTo(focus);ship.aim(focus);}
            boolean held=!interrupted || tick%90<60;
            ship.isShooting=held;ship.controlWeapons(held,held);ship.update();
            float travelled=Mathf.dst(beforeX,beforeY,ship.x,ship.y);distance+=travelled;if(travelled>.01f)moving++;
            for(Unit u:enemies)if(u.isAdded() && u.health>0){
                u.vel.setZero();
                if(u==focus)u.vel.x=650+20*Mathf.sin((tick+1)*type.speed/40f)-u.x;
                float speed=u.type.weapons.first().bullet.speed;
                var aim=Predict.intercept(u,ship,Math.max(speed,.01f));u.rotation=u.angleTo(aim);u.aim(aim);
                u.isShooting=u.type.targetAir;u.controlWeapons(true,u.isShooting);
                for(var m:u.mounts)m.shoot=u.isShooting && m.weapon.bullet.collidesAir && u.within(ship,m.weapon.range()+u.hitSize/2f);
                u.update();
            }
            Groups.bullet.update();Groups.bullet.collide();ExperimentalChecks.exposure.getMethod("update").invoke(null);
            if(old)for(Object e:((Map<?,?>)contacts.get(null)).values())remaining.setFloat(e,0f);
            if(focus.hasEffect(StatusEffects.corroded))corroded++;
            if((float)multiplier.invoke(null,ship,focus)<1)pen++;
            incoming+=Math.max(0,beforeHP-Math.max(0,ship.health));
            for(Unit u:enemies)if(u.health<=0 && u.isAdded())u.remove();
        }
        int kills=0,shots=0;for(int i=0;i<enemies.length;i++){Unit u=enemies[i];if(i>0 && u.health<=0)kills++;for(var m:u.mounts)shots+=m.totalShots;}
        if(tick>0 && moving<tick*.95f)throw new AssertionError("moving fixture stalled: "+moving+"/"+tick);
        if(shots==0)throw new AssertionError("return-fire fixture did not fire");
        out.printf(java.util.Locale.ROOT,"%s,%s,%s,%s,%s,%.0f,%d,%s,%.3f,%s,%.3f,%s,%.3f,%.3f,%d,%d,%d,%d,%.3f,%d,%.1f,%.3f,%s,%s%n",new String[]{"alpha","beta","gamma"}[tier],token,token.equals("caustic")?(old?"0%":"50% after buildup"):"n/a",defensive?"durability+5":"neutral",type.name,range/8,pressure,interrupted?"1s on / .5s off":"hold",tick/60f,ship.health>0,incoming,focus.health<=0,startHP-Math.max(0,focus.health),focus.shield,kills,corroded,pen,moving,tick==0?0:distance/tick,shots,focus.armor,ship.mounts[0].weapon.bullet.damage,compensated?"motion-compensated":"current position",weave?"weave":"orbit");
    }
    static void freeze(Unit u){u.controller(new UnitController(){Unit unit;public void unit(Unit v){unit=v;}public Unit unit(){return unit;}});}
}
