package shipyard;

import arc.util.Time;
import java.io.*;
import java.lang.reflect.*;
import java.util.Arrays;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.game.Team;
import mindustry.gen.*;

/** Buffered presentation must survive jitter without changing native collision/damage authority. */
final class EigenMotionChecks{
    static Class<?> motion;static Method encode,receive,update,pose,clear;static String epoch;
    static void run()throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;motion=loader.loadClass("shipyard.service.EigenMotion");
        encode=method("encode",Unit.class,int[].class,String.class,int.class,Player.class);receive=method("receive",byte[].class);update=method("update");clear=method("clear");pose=motion.getMethod("pose",Unit.class);
        var epochMethod=loader.loadClass("shipyard.service.EigenService").getDeclaredMethod("worldEpoch");epochMethod.setAccessible(true);
        Vars.logic.reset();if(Vars.fogControl==null)Vars.fogControl=new mindustry.game.FogControl();Vars.state.rules.waves=false;Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;
        Vars.world.loadGenerator(48,48,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));t.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);});Vars.state.set(State.playing);
        epoch=(String)epochMethod.invoke(null);
        Unit owner=UnitTypes.alpha.create(Team.sharded);owner.set(100,100);owner.add();
        var content=loader.loadClass("shipyard.content.EigenContent");var tiers=loader.loadClass("shipyard.model.ShipTier");
        var droneType=(mindustry.type.UnitType)content.getMethod("type",tiers).invoke(null,tiers.getEnumConstants()[0]);
        Unit drone=droneType.create(Team.sharded);drone.set(100,100);drone.vel.set(1,0);drone.add();
        var active=Vars.net.getClass().getDeclaredField("active");var server=Vars.net.getClass().getDeclaredField("server");active.setAccessible(true);server.setAccessible(true);
        boolean wasActive=active.getBoolean(Vars.net),wasServer=server.getBoolean(Vars.net);float previousTime=Time.time;
        try{
            active.setBoolean(Vars.net,true);server.setBoolean(Vars.net,false);clear.invoke(null);
            byte[] first=frame(owner,drone,1,100,100),second=frame(owner,drone,2,103,103),third=frame(owner,drone,3,106,106);
            Time.time=500;receive.invoke(null,(Object)first);Time.time=503;receive.invoke(null,(Object)second);Time.time=506;receive.invoke(null,(Object)third);
            int units=Groups.unit.size();float nativeX=drone.x,nativeY=drone.y,health=drone.health;
            update.invoke(null);near(x(drone),100,"buffer delay");
            for(int index=1;index<=24;index++){Time.time=506+index*.25f;update.invoke(null);near(x(drone),100+index*.25f,"smooth sub-frame Hermite movement");}
            near(drone.x,nativeX,"presentation moved collision X");near(drone.y,nativeY,"presentation moved collision Y");near(drone.health,health,"presentation changed health");check(Groups.unit.size()==units,"presentation spawned units");
            // Reordering, duplicates and invalid frames cannot rewind or replace the stream.
            receive.invoke(null,(Object)first);receive.invoke(null,(Object)third);receive.invoke(null,(Object)Arrays.copyOf(third,third.length-1));receive.invoke(null,(Object)new byte[513]);
            byte[] stale=frame(owner,drone,4,109,900,"old-"+epoch);receive.invoke(null,(Object)stale);
            Unit thief=UnitTypes.alpha.create(Team.sharded);thief.add();receive.invoke(null,(Object)frame(thief,drone,4,109,900));
            update.invoke(null);near(x(drone),106,"reordered/stale/cross-owner frames replaced stream");
            Time.time=560;update.invoke(null);near(x(drone),109,"unbounded extrapolation");
            // A late frame reanchors the clock, while the sample history stays bounded at eight.
            for(int index=4;index<=25;index++){byte[] next=frame(owner,drone,index,100+index*3,100+index*3);Time.time=500+index*3;receive.invoke(null,(Object)next);update.invoke(null);}
            var tracks=motion.getDeclaredField("tracks");tracks.setAccessible(true);Object track=((arc.struct.IntMap<?>)tracks.get(null)).get(drone.id);var history=track.getClass().getDeclaredField("samples");history.setAccessible(true);check(((arc.struct.Seq<?>)history.get(track)).size==8,"unbounded motion history");
            Unit secondDrone=droneType.create(Team.sharded);secondDrone.set(150,100);secondDrone.vel.set(1,0);secondDrone.add();
            try{
                receive.invoke(null,(Object)frame(thief,secondDrone,30,180,150));update.invoke(null);
                check(pose.invoke(null,drone)!=null && pose.invoke(null,secondDrone)!=null,"owners did not retain independent tracks");
                motion.getMethod("removed",Unit.class).invoke(null,secondDrone);
                check(pose.invoke(null,secondDrone)==null && pose.invoke(null,drone)!=null,"one removal erased another owner's motion");
            }finally{secondDrone.remove();}
            Time.time+=121;update.invoke(null);check(pose.invoke(null,drone)==null,"stale stream never expired");
            byte[] next=frame(owner,drone,26,200,200);Time.time=700;receive.invoke(null,(Object)next);update.invoke(null);check(pose.invoke(null,drone)!=null,"stream failed to recover");
            Time.time=203;byte[] empty=(byte[])encode.invoke(null,owner,new int[0],epoch,27,null);Time.time=703;receive.invoke(null,(Object)empty);check(pose.invoke(null,drone)==null,"empty owner frame retained visuals");
            clear.invoke(null);check(((arc.struct.IntMap<?>)tracks.get(null)).isEmpty(),"world cleanup leaked tracks");thief.remove();
            Player previous=Vars.player;
            try{
                Vars.player=Player.create();Vars.player.team(Team.sharded);Vars.player.unit(owner);
                Time.time=800;receive.invoke(null,(Object)frame(owner,drone,40,300,100));
                Time.time=803;receive.invoke(null,(Object)frame(owner,drone,41,303,103));
                Time.time=806;receive.invoke(null,(Object)frame(owner,drone,42,306,106));update.invoke(null);
                near(x(drone),103,"owner did not receive the shorter three-tick visual buffer");
            }finally{Vars.player=previous;clear.invoke(null);}
            Player viewer=Player.create();viewer.team(Team.blue);Vars.state.rules.fog=true;
            check(drone.inFogTo(viewer.team()),"fog fixture did not hide drone from enemy viewer");
            check(count((byte[])encode.invoke(null,owner,new int[]{drone.id},epoch,28,viewer))==0,"motion packet revealed a hidden enemy drone");
            viewer.team(Team.sharded);
            check(count((byte[])encode.invoke(null,owner,new int[]{drone.id},epoch,29,viewer))==1,"fog hid an allied drone");
        }finally{Vars.state.rules.fog=false;clear.invoke(null);Time.time=previousTime;active.setBoolean(Vars.net,wasActive);server.setBoolean(Vars.net,wasServer);owner.remove();drone.remove();}
        System.out.println("PASS: Eigen buffered sub-frame motion, native authority isolation, bounded history/extrapolation, reordered/duplicate/malformed/stale-world/cross-owner rejection, timeouts, independent owners, native fog visibility and cleanup");
    }
    static int count(byte[] packet)throws IOException{var input=new DataInputStream(new ByteArrayInputStream(packet));input.readUTF();input.readInt();input.readInt();input.readFloat();return input.readUnsignedByte();}
    static Method method(String name,Class<?>...parameters)throws Exception{var method=motion.getDeclaredMethod(name,parameters);method.setAccessible(true);return method;}
    static byte[] frame(Unit owner,Unit drone,int sequence,float tick,float x)throws Exception{return frame(owner,drone,sequence,tick,x,epoch);}
    static byte[] frame(Unit owner,Unit drone,int sequence,float tick,float x,String world)throws Exception{float clock=Time.time;try{Time.time=tick;drone.x=x;return(byte[])encode.invoke(null,owner,new int[]{drone.id},world,sequence,null);}finally{Time.time=clock;}}
    static float x(Unit drone)throws Exception{Object position=pose.invoke(null,drone);check(position!=null,"no buffered drone pose");return position.getClass().getField("x").getFloat(position);}
    static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<.01f,message+": "+actual+" != "+expected);}
    static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
