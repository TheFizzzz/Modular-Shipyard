package shipyard;

import arc.util.Time;
import java.io.*;
import java.util.Arrays;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.game.Team;
import mindustry.gen.*;

/** Remote continuous aim advances between native snapshots, without modifying authoritative unit state. */
final class WeaponMotionChecks{
    static void run()throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;var service=loader.loadClass("shipyard.service.WeaponMotion");
        var encode=service.getDeclaredMethod("encode",Unit.class,int.class);encode.setAccessible(true);
        var receive=service.getDeclaredMethod("receive",byte[].class);receive.setAccessible(true);
        var update=service.getDeclaredMethod("update");update.setAccessible(true);var clear=service.getDeclaredMethod("clear");clear.setAccessible(true);
        var remote=service.getMethod("remote",Unit.class);var shooting=service.getMethod("shooting",Unit.class);
        Vars.logic.reset();Vars.state.rules.unitCap=1000;Vars.world.loadGenerator(80,80,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});Vars.state.set(State.playing);
        Unit ship=UnitTypes.alpha.create(Team.sharded);ship.set(300,300);ship.add();ExperimentalChecks.equip(ship,0,"caustic");ship.aim(400,300);ship.isShooting=true;
        float originalClock=Time.time;
        Time.time=100;byte[] first=(byte[])encode.invoke(null,ship,1);
        ship.aim(415,307.5f);Time.time=103;byte[] middle=(byte[])encode.invoke(null,ship,2);
        ship.aim(430,315);Time.time=106;byte[] next=(byte[])encode.invoke(null,ship,3);Time.time=originalClock;
        var active=Vars.net.getClass().getDeclaredField("active");var server=Vars.net.getClass().getDeclaredField("server");active.setAccessible(true);server.setAccessible(true);
        boolean wasActive=active.getBoolean(Vars.net),wasServer=server.getBoolean(Vars.net);float clock=Time.time;
        Player previous=Vars.player;Vars.player=Player.create();Vars.player.team(Team.sharded);
        try{
            active.setBoolean(Vars.net,true);server.setBoolean(Vars.net,false);clear.invoke(null);Time.time=500;
            receive.invoke(null,(Object)first);Object aim=remote.invoke(null,ship);check(aim!=null,"valid aim frame rejected");
            var x=aim.getClass().getField("x");var y=aim.getClass().getField("y");
            var finalAim=service.getMethod("finalAim",Unit.class,arc.math.geom.Vec2.class);
            Time.time=503;receive.invoke(null,(Object)middle);Time.time=506;receive.invoke(null,(Object)next);
            var finalPoint=new arc.math.geom.Vec2();
            check((boolean)finalAim.invoke(null,ship,finalPoint),"last cursor position missing");near(finalPoint.x,430,"finish uses buffered aim instead of last cursor");
            float nativeX=ship.aimX,nativeY=ship.aimY,health=ship.health;
            for(int sample=0;sample<=24;sample++){
                Time.time=506+sample*.25f;update.invoke(null);
                near(x.getFloat(aim),400+sample*1.25f,"sub-frame aim X");near(y.getFloat(aim),300+sample*.625f,"sub-frame aim Y");
            }
            check((boolean)shooting.invoke(null,ship),"fresh shooting state missing");
            ship.isShooting=false;ship.rotation=180f;
            float cone=ship.mounts[0].weapon.shootCone;ship.mounts[0].weapon.update(ship,ship.mounts[0]);
            check(ship.mounts[0].bullet!=null && ship.mounts[0].bullet.isAdded(),"older hull/shooting snapshot suppressed fresh remote Caustic aim");
            near(ship.mounts[0].weapon.shootCone,cone,"remote presentation changed the gun's normal cone");
            receive.invoke(null,(Object)first);receive.invoke(null,(Object)Arrays.copyOf(next,next.length-1));receive.invoke(null,(Object)new byte[81]);
            update.invoke(null);near(x.getFloat(aim),430,"reordered/malformed frame rewound aim");
            near(ship.aimX,nativeX,"visual feed changed native aim X");near(ship.aimY,nativeY,"visual feed changed native aim Y");near(ship.health,health,"visual feed changed health");
            Vars.player.unit(ship);check(remote.invoke(null,ship)==null,"visual interpolation delayed local player input");Vars.player.clearUnit();
            Time.time+=61;update.invoke(null);check(remote.invoke(null,ship)==null,"stale aim never expired");
            clear.invoke(null);
            // Guest aim must reach observers without inheriting the native 200ms input steps.
            var incoming=service.getDeclaredMethod("receiveInput",Player.class,byte[].class);incoming.setAccessible(true);
            var visual=service.getMethod("visual",Unit.class);
            Player guest=Player.create();guest.team(Team.sharded);guest.unit(ship);
            ship.aim(325,300);Time.time=700;byte[] near=(byte[])encode.invoke(null,ship,10);
            ship.aim(425,340);Time.time=706;byte[] far=(byte[])encode.invoke(null,ship,11);
            ship.aim(390,310);ship.isShooting=false;
            server.setBoolean(Vars.net,true);Time.time=800;incoming.invoke(null,guest,near);
            Time.time=806;incoming.invoke(null,guest,far);update.invoke(null);
            Object hostAim=visual.invoke(null,ship);check(hostAim!=null,"listen host has no smooth guest beam");
            for(int sample=0;sample<=12;sample++){
                Time.time=806+sample*.5f;update.invoke(null);
                near(x.getFloat(hostAim),325+sample*(100f/12f),"guest beam extension on host");
            }
            near(ship.aimX,390,"guest visual input changed authoritative aim");
            check(!ship.isShooting,"guest visual input changed authoritative shooting");
            Unit foreign=UnitTypes.alpha.create(Team.sharded);foreign.set(320,320);foreign.add();
            Player intruder=Player.create();intruder.unit(foreign);
            incoming.invoke(null,intruder,near);incoming.invoke(null,guest,Arrays.copyOf(far,far.length-1));
            near(x.getFloat(hostAim),425,"foreign or malformed input changed guest beam");
            Time.time+=61;update.invoke(null);check(visual.invoke(null,ship)==null,"host guest stream never expired");
            clear.invoke(null);
        }finally{clear.invoke(null);Time.time=clock;Vars.player=previous;active.setBoolean(Vars.net,wasActive);server.setBoolean(Vars.net,wasServer);}
        System.out.println("PASS: continuous remote aim interpolation, native aim/health isolation, local input bypass, malformed/reordered rejection and stale-stream cleanup");
    }
    static void check(boolean valid,String why){if(!valid)throw new AssertionError(why);}
    static void near(float actual,float expected,String why){check(Math.abs(actual-expected)<.02f,why+": "+actual+" != "+expected);}
}
