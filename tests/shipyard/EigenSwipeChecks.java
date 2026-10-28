package shipyard;

import arc.struct.FloatSeq;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Trail;

/** Real position samples, swept crossings, armour/shield accounting and bounded native trails. */
final class EigenSwipeChecks{
    static void run()throws Exception{
        for(int tier=0;tier<3;tier++){
            Unit owner=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            owner.set(320,400);owner.add();ExperimentalChecks.equip(owner,tier,"eigen");owner.isShooting=false;EigenChecks.step(185);EigenChecks.deploy(owner);
            Unit drone=Groups.unit.getByID(EigenChecks.ids(owner)[0]);
            Unit target=ExperimentalChecks.enemy(tier==1?UnitTypes.flare:UnitTypes.dagger,400,400);target.armor=0;
            Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();float base=owner.mounts[0].weapon.bullet.damage;
            float budget=EigenChecks.values(owner,"charge")[0];
            sample(owner,drone,target.x,target.y);drone.vel.set(20f,0); // Velocity alone must not earn damage.
            owner.isShooting=true;float before=target.health;EigenChecks.update.invoke(null);
            near(before-target.health,base,"stationary contact or fake velocity got a swipe bonus");
            near(budget-EigenChecks.values(owner,"charge")[0],base,"stationary energy cost");
            fresh(owner,budget);sample(owner,drone,target.x-10,target.y);drone.set(target.x+10,target.y);drone.vel.setZero();
            owner.isShooting=true;before=target.health;EigenChecks.update.invoke(null);
            near(before-target.health,base*2f,"swept small-target crossing missed or lacked bonus");
            near(budget-EigenChecks.values(owner,"charge")[0],base,"swipe spent twice the energy");
            near(owner.mounts[0].weapon.bullet.damage,base,"swipe mutated shared/private gun damage");
            fresh(owner,budget);sample(owner,drone,target.x-4,target.y);drone.set(target);target.vel.set(4,0);
            owner.isShooting=true;before=target.health;EigenChecks.update.invoke(null);target.vel.setZero();
            near(before-target.health,base,"matching target velocity got a swipe bonus");
            fresh(owner,budget);sample(owner,drone,target.x-100,target.y);drone.set(target.x+100,target.y);
            owner.isShooting=true;before=target.health;EigenChecks.update.invoke(null);
            near(target.health,before,"teleport inflicted a map-wide swipe");
            fresh(owner,budget);sample(owner,drone,target.x,target.y);target.vel.set(5,0);
            owner.isShooting=true;before=target.health;EigenChecks.update.invoke(null);target.vel.setZero();
            near(before-target.health,base,"moving enemy alone gave an idle drone a swipe bonus");
            // Shield loss also spends charge, at the same efficient swipe rate.
            fresh(owner,budget);target.shield=1000;sample(owner,drone,target.x-10,target.y);drone.set(target.x+10,target.y);
            owner.isShooting=true;EigenChecks.update.invoke(null);near(1000-target.shield,base*2f,"swipe bypassed or missed shield");
            near(budget-EigenChecks.values(owner,"charge")[0],base,"shield contact charged wrong energy");target.remove();
            Vars.world.tile(49,49).setBlock(Blocks.copperWallLarge,Team.crux,0);Building wall=Vars.world.build(49,49);Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();
            fresh(owner,budget);sample(owner,drone,wall.x-13,wall.y);drone.set(wall.x+13,wall.y);owner.isShooting=true;
            before=wall.health;EigenChecks.update.invoke(null);near(before-wall.health,base*2f,"building contact missed swipe bonus");
            near(budget-EigenChecks.values(owner,"charge")[0],base,"building swipe energy cost");wall.tile.remove();
            // The extra reach must hit a target beyond the old 20-tile anchor.
            owner.isShooting=false;EigenChecks.step(185);EigenChecks.deploy(owner);target=ExperimentalChecks.enemy(UnitTypes.dagger,owner.x+176,owner.y);target.armor=0;
            owner.aim(target.x,target.y);owner.isShooting=true;EigenChecks.step(95);check(target.health<10000,"24-tile range could not reach a target at 22 tiles");
            target.remove();owner.isShooting=false;EigenChecks.step(185);EigenChecks.deploy(owner);drone=Groups.unit.getByID(EigenChecks.ids(owner)[0]);
            drone.trail=new Trail(drone.type.trailLength);EigenChecks.step(100);
            check(drone.trail.size()>5 && drone.trail.size()<=15,"trail history missing or unbounded");
            var points=Trail.class.getDeclaredField("points");points.setAccessible(true);FloatSeq path=(FloatSeq)points.get(drone.trail);
            float[] p=path.items;boolean curved=false;
            for(int i=6;i<path.size;i+=3)if(Math.abs((p[i-3]-p[i-6])*(p[i+1]-p[i-2])-(p[i-2]-p[i-5])*(p[i]-p[i-3]))>.001f)curved=true;
            check(curved,"orbit trail stayed a rigid straight tail");
            Call.unitDespawn(drone);check(drone.trail==null,"removed drone retained native trail / generic fade");
            owner.remove();EigenChecks.step(1);
        }
        compare();
        System.out.println("PASS: Eigen stationary/fake-velocity safety, 2x swept air/ground contact, energy-efficient bonus, relative enemy movement, teleport rejection, shield accounting, extended range and bounded curved native trails on all tiers");
    }
    static void compare()throws Exception{
        for(int tier=0;tier<3;tier++){
            int[] ttk=new int[2];
            for(int mode=0;mode<2;mode++){
                Unit owner=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
                owner.set(320,400);owner.add();ExperimentalChecks.equip(owner,tier,"eigen");EigenChecks.deploy(owner);
                Unit target=new mindustry.type.UnitType[]{UnitTypes.dagger,UnitTypes.fortress,UnitTypes.scepter}[tier].create(Team.crux);
                target.set(400,400);target.add();owner.isShooting=true;
                int tick=0;
                for(;tick<6000 && !target.dead;tick++){
                    owner.y=400+8f*(float)Math.sin(tick/50f);owner.vel.set(0,.16f*(float)Math.cos(tick/50f));
                    owner.aim(target.x+(mode==1?(tick%60<30?24f:-24f):0),target.y);EigenChecks.step(1);
                }
                ttk[mode]=tick;owner.remove();target.remove();EigenChecks.step(1);
            }
            System.out.println("EIGEN TTK tier "+tier+" stationary native target / moving player, no return fire: held="+(ttk[0]==6000?">=100":String.valueOf(ttk[0]/60f))+"s swipe="+(ttk[1]/60f)+"s");
            check(ttk[1]<ttk[0],"active swipes did not improve practical TTK on tier "+tier);
        }
    }
    static void sample(Unit owner,Unit drone,float x,float y)throws Exception{owner.isShooting=false;drone.set(x,y);drone.vel.setZero();EigenChecks.update.invoke(null);}
    static void fresh(Unit owner,float budget)throws Exception{EigenChecks.values(owner,"charge")[0]=budget;EigenChecks.values(owner,"cooldown")[0]=0;}
    static void check(boolean v,String why){ExperimentalChecks.check(v,why);}
    static void near(float a,float b,String why){ExperimentalChecks.near(a,b,why);}
}
