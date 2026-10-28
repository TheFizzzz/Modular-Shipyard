package shipyard;

import arc.Events;
import arc.util.Time;
import java.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.units.UnitController;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;

/** Exposure accounting, native status persistence/DOT and changing strand geometry. */
final class CorrosionChecks{
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;
        Vars.world.loadGenerator(64,64,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Time.setDeltaProvider(()->1f);Time.delta=1f;
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var exposure=loader.loadClass("shipyard.service.CausticExposure");
        var touch=exposure.getMethod("touch",Unit.class,Unit.class);
        var update=exposure.getMethod("update");
        var progress=exposure.getMethod("progress",Unit.class,Unit.class);
        Unit a=UnitTypes.alpha.create(Team.sharded),b=UnitTypes.beta.create(Team.sharded),target=UnitTypes.dagger.create(Team.crux);
        for(Unit u:new Unit[]{a,b,target}){u.set(200,200);u.add();u.health=u.maxHealth=10000;u.dead=false;freeze(u);}
        float nativeDamage=StatusEffects.corroded.intervalDamage,nativePeriod=StatusEffects.corroded.intervalDamageTime;
        for(int tick=0;tick<89;tick++){
            for(int strand=0;strand<6;strand++)touch.invoke(null,a,target);
            update.invoke(null);
        }
        near((float)progress.invoke(null,a,target),89,"strand/mount count multiplied exposure");
        check(!target.hasEffect(StatusEffects.corroded),"early status application");
        touch.invoke(null,a,target);update.invoke(null);
        near(target.getDuration(StatusEffects.corroded),180,"native status not applied");
        for(int tick=0;tick<10;tick++)update.invoke(null);
        near((float)progress.invoke(null,a,target),82.5f,"brief miss did not decay gently");
        for(int tick=0;tick<30;tick++){touch.invoke(null,b,target);update.invoke(null);}
        near((float)progress.invoke(null,b,target),30,"players shared exposure");
        near((float)progress.invoke(null,a,target),60,"inactive player's exposure failed to decay");
        touch.invoke(null,a,b);update.invoke(null);near((float)progress.invoke(null,a,b),0,"ally gained exposure");
        for(int tick=0;tick<50;tick++)update.invoke(null);
        near((float)progress.invoke(null,b,target),0,"abandoned target retained exposure indefinitely");
        for(int tick=0;tick<90;tick++){touch.invoke(null,a,target);update.invoke(null);}
        for(int tick=0;tick<15;tick++){target.update();touch.invoke(null,a,target);update.invoke(null);}
        check(target.health<10000,"native Corroded did not deal periodic damage");
        near(target.getDuration(StatusEffects.corroded),180,"continued fire did not refresh Corroded");
        check(StatusEffects.corroded.intervalDamage==nativeDamage && StatusEffects.corroded.intervalDamageTime==nativePeriod,"shared native status was mutated");
        // Native statuses, not a custom packet/chunk, survive both disk and sync codecs.
        int id=target.id;var file=Vars.dataDirectory.child("caustic-corroded.msav");SaveIO.write(file);SaveIO.load(file);
        target=Groups.unit.getByID(id);check(target!=null && target.getDuration(StatusEffects.corroded)>0,"disk save lost Corroded");
        var bytes=new ByteArrayOutputStream();target.writeSync(new arc.util.io.Writes(new DataOutputStream(bytes)));
        Unit remote=UnitTypes.dagger.create(Team.crux);remote.readSync(new arc.util.io.Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        check(remote.getDuration(StatusEffects.corroded)>0,"native unit sync lost Corroded");
        near((float)progress.invoke(null,a,target),0,"world load retained transient buildup");
        var geometry=loader.loadClass("shipyard.graphics.CausticBeam");
        var points=geometry.getMethod("points",Bullet.class,int.class);
        Bullet shape=Bullet.create();shape.set(100,100);shape.aimX=200;shape.aimY=100;
        double oldTick=Vars.state.tick;Vars.state.tick=0d;
        float[] first=(float[])points.invoke(null,shape,0);float midpoint=first[first.length/2|1];
        for(int strand=0;strand<3;strand++){
            float[] path=(float[])points.invoke(null,shape,strand);
            near(path[0],100,"strand left muzzle");near(path[1],100,"strand left muzzle Y");
            near(path[path.length-2],200,"strand missed convergence");near(path[path.length-1],100,"strand missed convergence Y");
            for(int i=1;i<path.length;i+=2)check(Math.abs(path[i]-100)<=6.71f,"strand envelope became a broad spray");
        }
        Vars.state.tick=9d;float[] moved=(float[])points.invoke(null,shape,0);
        check(Math.abs(moved[moved.length/2|1]-midpoint)>.1f,"strands do not animate");Vars.state.tick=oldTick;
        Events.fire(new ResetEvent());
        System.out.println("PASS: independent per-owner corrosion, six-strand dedup, exposure threshold/decay/refresh, ally safety, native DOT/disk/sync and narrow animated muzzle-to-focus geometry");
    }
    static void freeze(Unit u){u.controller(new UnitController(){Unit unit;public void unit(Unit v){unit=v;}public Unit unit(){return unit;}});}
    static void near(float a,float b,String why){check(Math.abs(a-b)<.02f,why+": "+a+" vs "+b);}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
