package shipyard;

import arc.Events;
import arc.util.Time;
import java.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;

/** Native percentage protection, single-target physics, independent/persistent energy and grab safety. */
final class OperatorProtectionChecks{
    static Class<?> operators,registry;static java.lang.reflect.Method update,energy,target;
    static void run()throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        operators=loader.loadClass("shipyard.service.OperatorService");registry=loader.loadClass("shipyard.service.UnitLoadouts");
        update=operators.getMethod("update");energy=operators.getMethod("energy",Unit.class);target=operators.getMethod("target",Unit.class);
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(128,128,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Vars.state.set(mindustry.core.GameState.State.playing);Time.setDeltaProvider(()->1f);Time.delta=1;
        var install=registry.getMethod("install",Unit.class,ExperimentalChecks.tiers,ExperimentalChecks.model,ExperimentalChecks.colours);
        for(int i=0;i<3;i++){
            var base=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[i];
            Unit unit=base.create(Team.sharded);unit.dead=false;unit.spawnedByCore=true;unit.set(250,250);unit.add();
            Object loadout=ExperimentalChecks.model.getMethod("parse",String.class).invoke(null,"0,-4,0,5,0");
            install.invoke(null,unit,ExperimentalChecks.tiers.getEnumConstants()[i],loadout,ExperimentalChecks.palette);
            near(unit.maxHealth,base.health*1.75f,"health changed beyond Durability formula");unit.heal();
            float before=unit.health;unit.damagePierce(50);near(before-unit.health,42.5f,"immediate DR missing");
            for(int tick=0;tick<10;tick++)unit.update();
            near(unit.healthMultiplier,1f/.85f,"DR accumulated over updates");
            unit.apply(StatusEffects.boss,120);unit.update();
            near(unit.healthMultiplier,StatusEffects.boss.healthMultiplier/.85f,"DR failed native status composition");
            before=unit.health;unit.damagePierce(30);near(before-unit.health,25.5f/StatusEffects.boss.healthMultiplier,"status/DR damage");
            unit.clearStatuses();unit.update();
            Object reduced=ExperimentalChecks.model.getMethod("parse",String.class).invoke(null,"0,0,0,-1,0");
            install.invoke(null,unit,ExperimentalChecks.tiers.getEnumConstants()[i],reduced,ExperimentalChecks.palette);
            near(unit.healthMultiplier,1f,"negative Durability retained DR");unit.remove();
            near(base.armor,0f,"shared native armour changed");
        }
        Unit owner=owner(0,320,400),enemy=ExperimentalChecks.enemy(UnitTypes.dagger,352,400);
        Unit other=ExperimentalChecks.enemy(UnitTypes.dagger,352,430);
        Unit ally=UnitTypes.dagger.create(Team.sharded);ally.set(enemy);ally.add();float hp=enemy.health;
        aim(owner,enemy.x,enemy.y,true);step();check(id(owner)==enemy.id,"Operator failed to grab one enemy");
        near(e(owner),99.7f,"light-target drain");
        aim(owner,enemy.x,enemy.y+40,true);step();check(enemy.vel.y>0,"cursor did not drag target");
        check(enemy.canShoot(),"Operator disarmed target");near(enemy.health,hp,"Operator inflicted damage");
        check(other.vel.isZero() && ally.vel.isZero(),"Operator dragged extra enemies/allies");
        Unit competing=owner(0,300,400);aim(competing,enemy.x,enemy.y,true);step();check(id(competing)==-1,"two Operators captured the same enemy");
        float lightSpeed=enemy.vel.y;
        Unit heavyOwner=owner(0,600,400),heavy=ExperimentalChecks.enemy(UnitTypes.scepter,632,400);
        aim(heavyOwner,heavy.x,heavy.y,true);step();near(e(heavyOwner),98.8f,"heavy energy cost");
        aim(heavyOwner,heavy.x,heavy.y+40,true);step();check(heavy.vel.y>0 && heavy.vel.y<lightSpeed,"heavy enemy did not resist force");
        aim(heavyOwner,heavy.x,heavy.y,false);step();check(id(heavyOwner)==-1,"release did not drop target");
        aim(owner,enemy.x,enemy.y,true);
        for(int tick=0;tick<360;tick++)step();
        check((boolean)operators.getMethod("locked",Unit.class).invoke(null,owner),"empty meter did not lock acquisition");
        check(id(owner)==-1,"empty meter kept its target");
        aim(owner,enemy.x,enemy.y,false);for(int tick=0;tick<60;tick++)step();
        check(!(boolean)operators.getMethod("locked",Unit.class).invoke(null,owner),"released recovered meter stayed locked");
        aim(owner,enemy.x,enemy.y,true);step();check(id(owner)==enemy.id,"recovered meter could not grab");
        enemy.set(owner.x+300,owner.y);step();check(id(owner)==-1,"out-of-range enemy stayed tethered");
        aim(owner,owner.x+32,owner.y,false);step();enemy.set(owner.x+32,owner.y);
        aim(owner,enemy.x,enemy.y,true);step();check(id(owner)==enemy.id,"fresh grab failed");
        // Save/join restore the energy reserve, but release the selected enemy safely.
        int savedId=owner.id;float reserve=e(owner);
        var file=Vars.dataDirectory.child("operator-energy.msav");SaveIO.write(file);SaveIO.load(file);
        owner=Groups.unit.getByID(savedId);check(owner!=null,"saved Operator ship missing");near(e(owner),reserve,"save refilled energy");check(id(owner)==-1,"save retained a stale grab");
        var bytes=new ByteArrayOutputStream();SaveIO.getVersion().writeCustomChunks(new DataOutputStream(bytes),true);
        Events.fire(new ResetEvent());SaveIO.getVersion().readCustomChunks(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        near(e(owner),reserve,"join chunk lost energy");check(id(owner)==-1,"join retained stale target");
        Vars.state.set(mindustry.core.GameState.State.playing);
        aim(owner,owner.x+32,owner.y,false);step();
        Unit next=ExperimentalChecks.enemy(UnitTypes.flare,owner.x-32,owner.y);aim(owner,next.x,next.y,true);step();
        check(id(owner)==next.id,"Operator could not grab air");owner.remove();step();
        check(id(owner)==-1,"removed owner retained a tether");
        System.out.println("PASS: immediate/stack-safe/status-composed Durability DR, single-target air/ground Operator, mass resistance/drain, exclusive capture, release/exhaustion/range safety, private save/join energy");
    }
    static Unit owner(int tier,float x,float y)throws Exception{
        Unit u=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
        u.dead=false;u.spawnedByCore=true;u.set(x,y);u.add();
        Object value=ExperimentalChecks.loadout("operator");registry.getMethod("install",Unit.class,ExperimentalChecks.tiers,ExperimentalChecks.model,ExperimentalChecks.colours)
            .invoke(null,u,ExperimentalChecks.tiers.getEnumConstants()[tier],value,ExperimentalChecks.palette);return u;
    }
    static void aim(Unit u,float x,float y,boolean held){u.aim(x,y);u.isShooting=held;}
    static void step()throws Exception{Vars.state.teams.updateTeamStats();update.invoke(null);}
    static float e(Unit u)throws Exception{return (float)energy.invoke(null,u);}
    static int id(Unit u)throws Exception{return (int)target.invoke(null,u);}
    static void near(float a,float b,String why){ExperimentalChecks.near(a,b,why);}
    static void check(boolean ok,String why){ExperimentalChecks.check(ok,why);}
}
