package shipyard;

import arc.graphics.Color;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.io.*;
import java.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.TypeIO;
import mindustry.type.UnitType;

/** Real unit physics, cold charge, swap cooldowns, edge audio and native visual payload codecs. */
final class CombatPresentationChecks{
    static void run()throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var units=loader.loadClass("shipyard.service.UnitLoadouts");
        var vector=loader.loadClass("shipyard.service.VectorReload");
        var install=units.getMethod("install",Unit.class,ExperimentalChecks.tiers,ExperimentalChecks.model,ExperimentalChecks.colours);
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.waves=false;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(80,80,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});Vars.state.set(State.playing);
        Time.setDeltaProvider(()->1f);Time.delta=1f;
        Unit owner=UnitTypes.alpha.create(Team.sharded);owner.set(200,200);owner.add();
        Unit ally=UnitTypes.alpha.create(Team.sharded);ally.set(240,200);ally.add();
        if(Vars.asyncCore==null)Vars.asyncCore=new mindustry.async.AsyncCore();
        check(!owner.dead && !ally.dead,"live physics actors exceeded native unit cap");
        Vars.unitPhysics.init();Vars.unitPhysics.add(owner);Vars.unitPhysics.add(ally);
        check(owner.hasPhysicsRef && ally.hasPhysicsRef,"physics fixture did not register normal ships");
        var content=loader.loadClass("shipyard.content.EigenContent");
        for(Object tier:ExperimentalChecks.tiers.getEnumConstants()){
            UnitType type=(UnitType)content.getMethod("type",ExperimentalChecks.tiers).invoke(null,tier);
            check(!type.physics,"drone retained physical pushing");near(type.speed,4.8f,"commanded speed changed");
            Unit drone=type.create(Team.sharded);drone.set(owner);drone.add();Vars.unitPhysics.add(drone);
            check(!drone.hasPhysicsRef,"native physics accepted a drone body");
            drone.vel.set(4.8f,0);for(int tick=0;tick<3;tick++)drone.moveAt(new Vec2(-4.8f,0));
            check(drone.vel.x<0f,"drone guidance did not reverse in three updates");
        }
        for(int tick=0;tick<30;tick++){Vars.asyncCore.begin();Vars.asyncCore.end();}
        near(owner.x,200,"drones pushed owner X");near(owner.y,200,"drones pushed owner Y");near(ally.x,240,"drones pushed ally X");near(ally.y,200,"drones pushed ally Y");
        for(int tier=0;tier<3;tier++){
            Unit ship=new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);ship.set(300,300);ship.add();ship.rotation=0;
            install.invoke(null,ship,ExperimentalChecks.tiers.getEnumConstants()[tier],ExperimentalChecks.loadout("railgun"),ExperimentalChecks.palette);
            near(ship.mounts[0].reload,0,"fresh Vector waits through an extra reload");check(ship.mounts[0].weapon.shootSound==Sounds.shootForeshadow,"Vector retained thin laser shot sound");
            ship.isShooting=true;ship.aim(400,300);
            var mount=ship.mounts[0];mount.shoot=true;mount.aimX=400;mount.aimY=300;mount.weapon.update(ship,mount);
            check(mount.charging,"first held update did not begin the charge");
            float remaining=(float)vector.getMethod("remaining",Unit.class).invoke(null,ship);check(remaining>170,"charge did not record its reload cycle");
            install.invoke(null,ship,ExperimentalChecks.tiers.getEnumConstants()[tier],ExperimentalChecks.loadout("standard"),ExperimentalChecks.palette);
            install.invoke(null,ship,ExperimentalChecks.tiers.getEnumConstants()[tier],ExperimentalChecks.loadout("railgun"),ExperimentalChecks.palette);
            check(ship.mounts[0].reload>170,"weapon swap erased Vector cooldown");
            ship.remove();
        }
        Unit laser=UnitTypes.alpha.create(Team.sharded);laser.set(300,300);laser.add();laser.rotation=0;ExperimentalChecks.equip(laser,0,"caustic");
        var on=Sounds.shootBeamPlasmaSmall;var off=Sounds.shootSap;var onProbe=new WeaponFlareChecks.Probe();var offProbe=new WeaponFlareChecks.Probe();
        Sounds.shootBeamPlasmaSmall=onProbe;Sounds.shootSap=offProbe;
        try{
            for(var mount:laser.mounts)mount.reload=0;
            ExperimentalChecks.fire(laser,380,300,20);
            check(onProbe.count==1 && offProbe.count==0,"Caustic startup cue missing or duplicated by two guns");
            laser.isShooting=false;for(var mount:laser.mounts){mount.shoot=false;mount.weapon.update(laser,mount);}
            check(offProbe.count==1,"Caustic shutdown cue missing or doubled");
            for(int tick=0;tick<10;tick++)for(var mount:laser.mounts)mount.weapon.update(laser,mount);
            check(offProbe.count==1,"Caustic shutdown cue repeated during idle");
            near(onProbe.volume,.8f,"Caustic startup volume");near(laser.mounts[0].weapon.shootSoundVolume,.8f,"Caustic sustain volume changed");
        }finally{Sounds.shootBeamPlasmaSmall=on;Sounds.shootSap=off;}
        // These are the native RPC object types used by the replicated lightning arcs and hit rings.
        for(Object details:new Object[]{new Vec2(430,312),Float.valueOf(5.5f)}){
            var bytes=new ByteArrayOutputStream();TypeIO.writeObject(new Writes(new DataOutputStream(bytes)),details);
            Object decoded=TypeIO.readObject(new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
            check(details.equals(decoded),"lightning RPC detail round trip failed");
        }
        ReloadWorkChecks.run();
        System.out.println("PASS: Eigen native non-pushing physics and faster reversal on all tiers; immediate cold Vector charge with swap-proof cycle and Foreshadow sound; one Caustic on/off cue per burst; native lightning detail codecs and reload effects");
    }
    static void check(boolean valid,String why){if(!valid)throw new AssertionError(why);}
    static void near(float actual,float expected,String why){check(Math.abs(actual-expected)<.03f,why+": "+actual+" != "+expected);}
}
