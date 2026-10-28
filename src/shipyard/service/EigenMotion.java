package shipyard.service;

import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.*;
import arc.util.Time;
import java.io.*;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.Trail;
import shipyard.content.EigenContent;

/** Small, unreliable motion frames; interpolation changes presentation only, never native authority. */
public final class EigenMotion{
    private static final String packet="modular-shipyard-eigen-motion-v1";
    private static final float delay=6f,maxExtrapolation=3f;
    private static final IntMap<Stream> streams=new IntMap<>();
    private static final IntMap<Track> tracks=new IntMap<>();
    private static int sequence;
    public static final class Pose{
        public float x,y,rotation;
        public final Trail trail=new Trail(14);
        public final Color colour=new Color(Color.white);
        public boolean drawn;
    }
    private record Sample(int id,float tick,float x,float y,float vx,float vy,float rotation){}
    private static final class Stream{
        final int owner;int sequence;float offset,lastTick,lastReceived;boolean initialized;
        final IntMap<Track> tracks=new IntMap<>();
        Stream(int owner){this.owner=owner;}
    }
    private static final class Track{
        final int id;final Stream stream;final Pose pose=new Pose();final Seq<Sample> samples=new Seq<>(8);
        final float created=Time.time;boolean observed;
        Track(int id,Stream stream){this.id=id;this.stream=stream;}
    }
    private EigenMotion(){}
    public static void init(){
        Events.on(ResetEvent.class,event->clear());Events.on(WorldLoadBeginEvent.class,event->clear());
        Events.run(Trigger.afterGameUpdate,EigenMotion::update);
        if(Vars.netClient!=null)Vars.netClient.addBinaryPacketHandler(packet,EigenMotion::receive);
    }
    private static void clear(){streams.clear();tracks.clear();sequence=0;}
    static void send(Unit owner,int[] ids,String epoch){
        if(!Vars.net.server())return;
        boolean hasDrones=false;for(int id:ids)if(id>=0){hasDrones=true;break;}
        if(!hasDrones)return;
        int frameSequence=++sequence;
        if(!Vars.state.rules.fog){Call.clientBinaryPacketUnreliable(packet,encode(owner,ids,epoch,frameSequence,null));return;}
        // Match native fog visibility instead of leaking hidden enemy positions in a side channel.
        for(Player viewer:Groups.player)if(viewer.con!=null && viewer.con.hasConnected)
            Call.clientBinaryPacketUnreliable(viewer.con,packet,encode(owner,ids,epoch,frameSequence,viewer));
    }
    private static byte[] encode(Unit owner,int[] ids,String epoch,int sequence,Player viewer){
        var visible=new Seq<Unit>(EigenService.capacity);
        for(int id:ids){Unit drone=Groups.unit.getByID(id);if(drone!=null && EigenContent.isDrone(drone) && (viewer==null || !drone.inFogTo(viewer.team())))visible.add(drone);}
        var bytes=new ByteArrayOutputStream(280);
        try{
            var output=new DataOutputStream(bytes);output.writeUTF(epoch);output.writeInt(owner.id);output.writeInt(sequence);output.writeFloat(Time.time);output.writeByte(visible.size);
            for(Unit drone:visible){output.writeInt(drone.id);output.writeFloat(drone.x);output.writeFloat(drone.y);output.writeFloat(drone.vel.x);output.writeFloat(drone.vel.y);output.writeFloat(drone.rotation);}
        }catch(IOException encodingFailure){throw new UncheckedIOException("Cannot encode Eigen movement",encodingFailure);}
        return bytes.toByteArray();
    }
    private static void receive(byte[] payload){
        if(!Vars.net.client() || payload==null || payload.length>512)return;
        int owner,frameSequence;float tick;Sample[] samples;
        try{
            var input=new DataInputStream(new ByteArrayInputStream(payload));if(!input.readUTF().equals(EigenService.worldEpoch()))return;
            owner=input.readInt();frameSequence=input.readInt();tick=input.readFloat();int count=input.readUnsignedByte();
            if(owner<0 || !Float.isFinite(tick) || count>EigenService.capacity)return;
            samples=new Sample[count];var ids=new IntSet();
            for(int index=0;index<count;index++){
                int id=input.readInt();float x=input.readFloat(),y=input.readFloat(),vx=input.readFloat(),vy=input.readFloat(),rotation=input.readFloat();
                if(id<0 || id==owner || !ids.add(id) || !finite(x,y,vx,vy,rotation))return;
                Track claimed=tracks.get(id);if(claimed!=null && claimed.stream.owner!=owner)return;
                samples[index]=new Sample(id,tick,x,y,vx,vy,rotation);
            }
            if(input.available()!=0)return;
        }catch(IOException malformedFrame){return;}
        Stream stream=streams.get(owner);
        if(stream!=null && stream.initialized && (frameSequence-stream.sequence<=0 || tick<=stream.lastTick))return;
        if(stream==null){stream=new Stream(owner);streams.put(owner,stream);}
        Unit local=Vars.player==null?null:Vars.player.unit();
        float buffering=local!=null && local.id==owner?3f:delay;
        float desired=tick-Time.time-buffering;
        boolean reset=!stream.initialized || Math.abs(desired-stream.offset)>30f;
        stream.offset=reset?desired:Mathf.lerp(stream.offset,desired,.05f);
        stream.sequence=frameSequence;stream.lastTick=tick;stream.lastReceived=Time.time;stream.initialized=true;
        var present=new IntSet();
        for(Sample sample:samples){
            present.add(sample.id);Track track=tracks.get(sample.id);
            if(track==null){track=new Track(sample.id,stream);tracks.put(sample.id,track);stream.tracks.put(sample.id,track);}
            if(reset){track.samples.clear();track.pose.trail.clear();}
            if(track.samples.size==8)track.samples.remove(0);
            track.samples.add(sample);
        }
        for(Track track:stream.tracks.values().toArray())if(!present.contains(track.id))remove(track,true);
        if(samples.length==0)streams.remove(owner);
    }
    private static boolean finite(float... components){for(float component:components)if(!Float.isFinite(component) || Math.abs(component)>10000000f)return false;return true;}
    private static void update(){
        if(!Vars.net.client() || !Vars.state.isPlaying())return;
        for(Stream stream:streams.values().toArray()){
            if(Time.time-stream.lastReceived>120f){forgetOwner(stream.owner);continue;}
            for(Track track:stream.tracks.values().toArray()){
                Unit drone=Groups.unit.getByID(track.id);
                if(drone==null){if(track.observed || Time.time-track.created>60f)remove(track,true);continue;}
                if(!EigenContent.isDrone(drone) || drone.dead){remove(track,true);continue;}
                track.observed=true;
                float beforeX=track.pose.x,beforeY=track.pose.y;
                evaluate(track,Time.time+stream.offset);
                if(Mathf.dst(beforeX,beforeY,track.pose.x,track.pose.y)>128f)track.pose.trail.clear();
                track.pose.trail.update(track.pose.x,track.pose.y);
                // Native low-rate interpolation remains intact, but does not feed this visual trail.
                drone.trail=null;
            }
        }
    }
    private static void evaluate(Track track,float tick){
        if(track.samples.isEmpty())return;
        Sample first=track.samples.first(),last=track.samples.peek();Pose pose=track.pose;
        if(tick<=first.tick){pose.x=first.x;pose.y=first.y;pose.rotation=first.rotation;return;}
        if(tick>=last.tick){float ahead=Mathf.clamp(tick-last.tick,0,maxExtrapolation);pose.x=last.x+last.vx*ahead;pose.y=last.y+last.vy*ahead;pose.rotation=last.rotation;return;}
        for(int index=1;index<track.samples.size;index++){
            Sample end=track.samples.get(index);if(end.tick<tick)continue;Sample start=track.samples.get(index-1);
            float span=end.tick-start.tick,t=Mathf.clamp((tick-start.tick)/span),t2=t*t,t3=t2*t;
            float a=2*t3-3*t2+1,b=t3-2*t2+t,c=-2*t3+3*t2,d=t3-t2;
            pose.x=a*start.x+b*span*start.vx+c*end.x+d*span*end.vx;
            pose.y=a*start.y+b*span*start.vy+c*end.y+d*span*end.vy;
            pose.rotation=Mathf.slerp(start.rotation,end.rotation,t);return;
        }
    }
    public static Pose pose(Unit drone){Track track=Vars.net.client()?tracks.get(drone.id):null;return track==null || !track.observed?null:track.pose;}
    static void forgetOwner(int owner){Stream stream=streams.remove(owner);if(stream!=null)for(Track track:stream.tracks.values().toArray())remove(track,true);}
    public static void removed(Unit drone){Track track=tracks.get(drone.id);if(track!=null)remove(track,true);}
    private static void remove(Track track,boolean fade){
        tracks.remove(track.id);track.stream.tracks.remove(track.id);
        Pose pose=track.pose;if(fade && !Vars.headless && pose.drawn && pose.trail.size()>0)Fx.trailFade.at(pose.x,pose.y,1.6f,pose.colour,pose.trail.copy());
    }
}
