package shipyard;

import arc.math.*;
import arc.math.geom.Intersector;
import arc.struct.FloatSeq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.*;

/** Native cursor guidance independent of trigger, exact retrace, return-only bonus and bounded loops. */
final class InvolutionSteeringChecks{
    static void run()throws Exception{
        for(int tier=0;tier<3;tier++){
            Groups.bullet.clear();
            var base=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier];
            Unit ship=base.create(Team.sharded),other=base.create(Team.sharded);
            for(Unit owner:new Unit[]{ship,other}){owner.set(320,400);owner.rotation=0;owner.add();ExperimentalChecks.equip(owner,tier,"lancer");owner.aim(460,400);}
            BulletType type=ship.mounts[0].weapon.bullet;
            near(type.speed,5f,"outward speed");near(type.range,198.72f,"20% greater range");
            ship.isShooting=true;other.isShooting=false;
            Bullet guided=launch(type,ship,4),comparison=launch(other.mounts[0].weapon.bullet,other,4);
            float full=guided.damage,initial=guided.rotation();
            FloatSeq record=new FloatSeq();record.add(guided.x,guided.y);
            ship.aim(400,480);other.aim(400,480); // Cursor moves before the very first native update.
            InvolutionChecks.tick();record.add(guided.x,guided.y);
            check(Angles.angleDist(initial,guided.rotation())>4f,"cursor guidance did not start immediately");
            for(int frame=0;frame<15;frame++){
                if(frame==7){ship.isShooting=false;other.isShooting=true;ship.aim(470,430);other.aim(470,430);}
                InvolutionChecks.tick();record.add(guided.x,guided.y);
                near(guided.x,comparison.x,"trigger changed cursor path x");near(guided.y,comparison.y,"trigger changed cursor path y");
                near(guided.damage,full,"bonus applied during outbound flight");
            }
            comparison.remove();other.remove();
            ship.aim(470,460);
            boolean retraced=false,homed=false,moved=false;
            for(int frame=0;frame<140 && guided.isAdded();frame++){
                int previousPhase=(int)field(guided,"phase");InvolutionChecks.tick();
                int phase=(int)field(guided,"phase");
                if(previousPhase==0)record.add(guided.x,guided.y);
                if(phase>0){
                    near(guided.damage,previousPhase==0?full:full*1.25f,"return bonus early, missing or stacking");
                    if(previousPhase==1)check(Mathf.dst(guided.lastX,guided.lastY,guided.x,guided.y)<=6.02f,"retrace movement exceeded six units/tick");
                    near(guided.type.damage,type.damage,"return bonus mutated projectile definition");
                    if(!moved){ship.y+=20;moved=true;}
                }
                if(phase==1){
                    retraced=true;float nearest=Float.MAX_VALUE;
                    for(int i=2;i<record.size;i+=2)nearest=Math.min(nearest,Intersector.distanceSegmentPoint(record.items[i-2],record.items[i-1],record.items[i],record.items[i+1],guided.x,guided.y));
                    check(nearest<.02f,"guided return left its exact recorded segments");
                }
                if(phase==2)homed=true;
                check(guided.dst(guided.originX,guided.originY)<205f,"guided throw exceeded the range boundary by more than one native step");
            }
            check(retraced && homed && !guided.isAdded() && guided.time<140f,"guided throw failed retrace/moving-owner catch");Groups.bullet.clear();

            // A cursor near the launch point can make an orbit; total travel must still force a return.
            ship.aim(ship.x+100,ship.y);ship.isShooting=true;Bullet looping=launch(type,ship,4);ship.aim(ship.x,ship.y);
            int ticks=0;
            while(looping.isAdded() && field(looping,"phase")==0 && ticks++<70)InvolutionChecks.tick();
            check(field(looping,"phase")==1 && ticks<55,"cursor loop did not reach its bounded travel cutoff");
            check(looping.dst(looping.originX,looping.originY)<198.72f,"loop reached radial boundary instead of travel cutoff");
            for(int i=0;i<140 && looping.isAdded();i++)InvolutionChecks.tick();
            check(!looping.isAdded(),"looping blade did not return");ship.remove();
        }
        Time.delta=1f;
        System.out.println("PASS: Involution immediate cursor guidance independent of trigger, 20% range gain, slower outbound/return, once-only private 25% return bonus, exact retrace/moving catch and bounded loops on all tiers");
    }
    static Bullet launch(BulletType type,Unit ship,float offset){
        float x=ship.x,y=ship.y+offset;
        return type.create(ship,ship.team,x,y,Angles.angle(x,y,ship.aimX,ship.aimY),-1f,1f,1f,null,null,ship.aimX,ship.aimY);
    }
    static float field(Bullet b,String name)throws Exception{
        var f=b.data.getClass().getDeclaredField(name);f.setAccessible(true);return ((Number)f.get(b.data)).floatValue();
    }
    static void check(boolean value,String why){InvolutionChecks.check(value,why);}
    static void near(float a,float b,String why){InvolutionChecks.near(a,b,why);}
}
