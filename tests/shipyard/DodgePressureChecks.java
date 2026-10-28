package shipyard;

import arc.math.*;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.Predict;
import mindustry.entities.units.UnitController;
import mindustry.game.Team;
import mindustry.gen.*;

/** Incoming damage, not survival/TTK: twelve native T1 guns, current-velocity prediction, evasive orbit. */
final class DodgePressureChecks{
    static void run()throws Exception{
        var file=Vars.dataDirectory.child("dodge-pressure.csv");
        try(var out=new java.io.PrintWriter(file.file())){
            out.println("movement,durability,attackers,seconds,incoming_damage");
            float[] damage=new float[4];int index=0;
            for(boolean moving:new boolean[]{false,true})for(int durability:new int[]{0,5}){
                Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
                Vars.world.loadGenerator(160,160,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
                Vars.state.set(mindustry.core.GameState.State.playing);Time.setDeltaProvider(()->1f);Time.delta=1;Mathf.rand.setSeed(92);
                Unit ship=UnitTypes.alpha.create(Team.sharded);ship.dead=false;ship.spawnedByCore=false;ship.set(moving?690:640,640);freeze(ship);ship.add();
                var loader=Vars.mods.getMod("modular-shipyard").loader;
                Object loadout=ExperimentalChecks.model.getMethod("parse",String.class).invoke(null,"0,"+(durability>0?-4:0)+",0,"+durability+",0");
                loader.loadClass("shipyard.service.UnitLoadouts").getMethod("install",Unit.class,ExperimentalChecks.tiers,ExperimentalChecks.model,ExperimentalChecks.colours)
                    .invoke(null,ship,ExperimentalChecks.tiers.getEnumConstants()[0],loadout,ExperimentalChecks.palette);
                // Equal large health pools keep geometry/shot counts comparable after ordinary Alpha HP would be depleted.
                ship.health=ship.maxHealth=100000f;
                Unit[] enemies=new Unit[12];
                for(int i=0;i<enemies.length;i++){
                    Unit enemy=UnitTypes.dagger.create(Team.crux);enemy.dead=false;
                    enemy.set(640+Angles.trnsx(i*30f,96),640+Angles.trnsy(i*30f,96));freeze(enemy);enemy.add();enemies[i]=enemy;
                }
                for(int tick=0;tick<600;tick++){
                    Time.update();Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();Groups.bullet.updatePhysics();
                    if(moving){float angle=(tick+1)*2.6f;ship.vel.set(640+Angles.trnsx(angle,50)-ship.x,640+Angles.trnsy(angle,50)-ship.y);}
                    else ship.vel.setZero();
                    ship.update();
                    for(Unit enemy:enemies){
                        var aim=Predict.intercept(enemy,ship,enemy.type.weapons.first().bullet.speed);
                        enemy.rotation=enemy.angleTo(aim);enemy.aim(aim);enemy.isShooting=true;enemy.controlWeapons(true,true);enemy.update();
                    }
                    Groups.bullet.update();Groups.bullet.collide();
                }
                damage[index++]=100000f-ship.health;
                out.printf(java.util.Locale.ROOT,"%s,%d,12,10,%.3f%n",moving?"orbit":"stationary",durability,100000f-ship.health);
            }
            if(!(damage[0]>0 && damage[2]<damage[0]))throw new AssertionError("evasive route did not reduce incoming native projectile damage");
            if(Math.abs(damage[1]-damage[0]*.85f)>2f || Math.abs(damage[3]-damage[2]*.85f)>2f)throw new AssertionError("DR did not scale matching moving/stationary exposure");
            System.out.println("DODGE PRESSURE: stationary="+damage[0]+", orbit="+damage[2]+", protected="+damage[1]+"/"+damage[3]+"; "+file);
        }
    }
    static void freeze(Unit unit){unit.controller(new UnitController(){Unit u;public void unit(Unit value){u=value;}public Unit unit(){return u;}});}
}
