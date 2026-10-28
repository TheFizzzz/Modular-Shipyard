package shipyard;

import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.Damage;
import mindustry.game.Team;
import mindustry.gen.*;

/** Nine smaller contacts preserve six-drone damage/energy, including native flat armour. */
final class EigenSwarmChecks{
    static void run()throws Exception{
        for(int tier=0;tier<3;tier++){
            Unit owner=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            owner.set(320,400);owner.add();ExperimentalChecks.equip(owner,tier,"eigen");EigenChecks.deploy(owner);owner.isShooting=false;
            float oldContact=12f*new float[]{.35f,.8f,2f}[tier],oldBudget=new float[]{20f,55f,130f}[tier];
            var marker=owner.mounts[0].weapon.bullet;
            near(marker.damage*9,oldContact*6,"aggregate swarm contact damage changed");
            near(marker.armorMultiplier,2f/3f,"split armour compensation missing");
            float sum=0;for(float charge:EigenChecks.values(owner,"charge"))sum+=charge;
            near(sum,oldBudget*6,"aggregate swarm energy changed");
            Unit target=ExperimentalChecks.enemy(UnitTypes.dagger,400,400);Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();
            for(float armor:new float[]{0,5,9,20}){
                target.armor=armor;target.health=10000;target.shield=0;
                for(int slot=0;slot<9;slot++){
                    Unit drone=Groups.unit.getByID(EigenChecks.ids(owner)[slot]);drone.set(target);
                    EigenChecks.values(owner,"dx")[slot]=EigenChecks.values(owner,"dy")[slot]=0;
                    EigenChecks.values(owner,"charge")[slot]=oldBudget*2f/3f;
                    EigenChecks.forceContact(owner,drone,slot);
                }
                near(10000-target.health,6*Damage.applyArmor(oldContact,armor),"native armoured aggregate damage changed: "+armor);
            }
            target.remove();owner.aim(400,400);owner.isShooting=true;EigenChecks.step(120);
            float nearest=Float.MAX_VALUE,farthest=0;
            for(int tick=0;tick<100;tick++){
                EigenChecks.step(1);
                for(int id:EigenChecks.ids(owner)){Unit drone=Groups.unit.getByID(id);float radius=drone.dst(400,400);nearest=Math.min(nearest,radius);farthest=Math.max(farthest,radius);}
            }
            check(nearest<6 && farthest>10 && farthest<20,"swarm did not spread and pass through cursor: "+nearest+" / "+farthest);
            ExperimentalChecks.equip(owner,tier,"lancer");
            check(owner.mounts[0].weapon.bullet.damage>marker.damage*2,"Involution lost stronger per-hit identity");
            owner.remove();EigenChecks.step(1);
        }
        System.out.println("PASS: nine-drone aggregate damage/energy unchanged, flat-armour compensation on all tiers, broad cursor passes and stronger Involution per-hit damage");
    }
    static void near(float actual,float expected,String why){ExperimentalChecks.near(actual,expected,why);}
    static void check(boolean passed,String why){ExperimentalChecks.check(passed,why);}
}
