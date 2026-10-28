package shipyard.service;

import arc.Events;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Time;
import java.io.*;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import shipyard.content.ShipVariants;
import shipyard.model.*;

/** Small, fog-filtered aim frames for continuous weapon visuals. Never changes native unit aim or damage. */
public final class WeaponMotion{
    private static final String packet="modular-shipyard-weapon-motion-v1";
    private static final IntMap<Aim> aims=new IntMap<>();
    private static final String inputPacket="modular-shipyard-weapon-aim-v1";
    private static final IntMap<Input> inputs=new IntMap<>();
    private record Frame(int serial,int id,int kind,float tick,float x,float y,boolean shooting){}
    private record Input(Unit owner,Frame frame,float received){}
    private static float timer;
    private static int sequence;
    public static final class Aim{
        public float x,y;
        public boolean shooting;
        private float offset,lastTick,received;
        private final Seq<Sample> samples=new Seq<>(8);
        private int sequence,kind;
    }
    private record Sample(float tick,float x,float y){}
    private WeaponMotion(){}
    public static void init(){
        Events.on(ResetEvent.class,event->clear());Events.on(WorldLoadBeginEvent.class,event->clear());
        Events.run(Trigger.beforeGameUpdate,WeaponMotion::update);
        if(Vars.netClient!=null)Vars.netClient.addBinaryPacketHandler(packet,WeaponMotion::receive);
        if(Vars.netServer!=null)Vars.netServer.addBinaryPacketHandler(inputPacket,WeaponMotion::receiveInput);
    }
    private static void clear(){aims.clear();inputs.clear();timer=0;sequence=0;}
    private static boolean supported(ShipWeapon kind){return kind==ShipWeapon.caustic || kind==ShipWeapon.invariant;}
    private static void update(){
        if(!Vars.state.isPlaying())return;
        if(Vars.net.server()){
            var incoming=inputs.entries();while(incoming.hasNext()){
                var input=incoming.next().value;
                if(!input.owner.isAdded() || Time.time-input.received>60f){incoming.remove();aims.remove(input.owner.id);}
            }
            for(Aim aim:aims.values())evaluate(aim,Time.time+aim.offset);
            timer+=Math.max(0f,Time.delta);
            if(timer<3f)return;timer%=3f;
            for(Unit ship:Groups.unit)if(!ship.dead && ShipTier.of(ship.type)!=null && supported(ShipVariants.current(ship.type).weapon())){
                byte[] frame=encode(ship,++sequence);
                if(!Vars.state.rules.fog)Call.clientBinaryPacketUnreliable(packet,frame);
                else for(Player viewer:Groups.player)if(viewer.con!=null && viewer.con.hasConnected && !ship.inFogTo(viewer.team()))
                    Call.clientBinaryPacketUnreliable(viewer.con,packet,frame);
            }
        }else if(Vars.net.client()){
            timer+=Math.max(0f,Time.delta);
            Unit local=Vars.player==null?null:Vars.player.unit();
            if(timer>=3f){
                timer%=3f;
                if(local!=null && local.isValid() && ShipTier.of(local.type)!=null && supported(ShipVariants.current(local.type).weapon()))
                    Call.serverBinaryPacketUnreliable(inputPacket,encode(local,++sequence));
            }
            var entries=aims.entries();while(entries.hasNext()){
                var entry=entries.next();
                Aim aim=entry.value;Unit ship=Groups.unit.getByID(entry.key);
                if(Time.time-aim.received>60f || ship!=null && (ship.dead || ShipTier.of(ship.type)==null
                    || ShipVariants.current(ship.type).weapon().ordinal()!=aim.kind)) {entries.remove();continue;}
                evaluate(aim,Time.time+aim.offset);
            }
        }
    }
    private static byte[] encode(Unit ship,int serial){
        var bytes=new ByteArrayOutputStream(64);
        try{
            var output=new DataOutputStream(bytes);output.writeUTF(UnitLoadouts.worldEpoch());output.writeInt(serial);output.writeInt(ship.id);output.writeFloat(Time.time);
            Input input=Vars.net.server()?inputs.get(ship.id):null;
            Frame fresh=input!=null && input.owner==ship && Time.time-input.received<=15f
                && input.frame.kind==ShipVariants.current(ship.type).weapon().ordinal()?input.frame:null;
            output.writeByte(ShipVariants.current(ship.type).weapon().ordinal());output.writeFloat(fresh==null?ship.aimX:fresh.x);output.writeFloat(fresh==null?ship.aimY:fresh.y);
            output.writeBoolean((fresh==null?ship.isShooting:fresh.shooting) && ship.canShoot());
        }catch(IOException encodingFailure){throw new UncheckedIOException("Cannot encode continuous weapon aim",encodingFailure);}
        return bytes.toByteArray();
    }
    /** A guest's native snapshots are too sparse for a changing beam length. Relay only
     * visual input from the exact unit they control; simulation and damage still use native input. */
    private static void receiveInput(Player player,byte[] payload){
        if(!Vars.net.server() || player==null || player.dead())return;
        Frame frame=decode(payload);if(frame==null)return;
        Unit ship=player.unit();
        if(ship==null || !ship.isValid() || ship.id!=frame.id || ShipTier.of(ship.type)==null
            || ShipVariants.current(ship.type).weapon().ordinal()!=frame.kind)return;
        Input old=inputs.get(ship.id);
        if(old!=null && old.owner==ship && (frame.serial-old.frame.serial<=0 || frame.tick<=old.frame.tick || Time.time-old.received<1f))return;
        inputs.put(ship.id,new Input(ship,frame,Time.time));
        accept(new Frame(frame.serial,frame.id,frame.kind,Time.time,frame.x,frame.y,frame.shooting));
    }
    private static Frame decode(byte[] payload){
        if(payload==null || payload.length>80)return null;
        try{
            var input=new DataInputStream(new ByteArrayInputStream(payload));if(!input.readUTF().equals(UnitLoadouts.worldEpoch()))return null;
            int serial=input.readInt(),id=input.readInt();float tick=input.readFloat();int kind=input.readUnsignedByte();
            float x=input.readFloat(),y=input.readFloat();boolean shooting=input.readBoolean();
            if(input.available()!=0 || !Float.isFinite(tick) || id<0 || kind>=ShipWeapon.values().length || !supported(ShipWeapon.values()[kind])
                || !Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x)>10000000f || Math.abs(y)>10000000f)return null;
            return new Frame(serial,id,kind,tick,x,y,shooting);
        }catch(IOException malformedFrame){return null;}
    }
    private static void receive(byte[] payload){
        if(!Vars.net.client())return;
        Frame frame=decode(payload);if(frame!=null)accept(frame);
    }
    private static void accept(Frame frame){
        int serial=frame.serial,id=frame.id,kind=frame.kind;float tick=frame.tick,x=frame.x,y=frame.y;boolean shooting=frame.shooting;
        Aim old=aims.get(id);if(old!=null && (serial-old.sequence<=0 || tick<=old.lastTick))return;
        Unit ship=Groups.unit.getByID(id);
        if(ship!=null && (ShipTier.of(ship.type)==null || ShipVariants.current(ship.type).weapon().ordinal()!=kind))return;
        Aim aim=old==null?new Aim():old;
        float desired=tick-Time.time-6f;
        boolean reset=old==null || aim.kind!=kind || Math.abs(desired-aim.offset)>30f;
        if(reset){aim.x=x;aim.y=y;aim.samples.clear();}
        aim.offset=reset?desired:Mathf.lerp(aim.offset,desired,.05f);
        if(aim.samples.size==8)aim.samples.remove(0);
        aim.samples.add(new Sample(tick,x,y));aim.lastTick=tick;aim.received=Time.time;
        aim.shooting=shooting;aim.sequence=serial;aim.kind=kind;aims.put(id,aim);
    }
    private static void evaluate(Aim aim,float tick){
        Sample first=aim.samples.first(),last=aim.samples.peek();
        if(tick<=first.tick){aim.x=first.x;aim.y=first.y;return;}
        if(tick>=last.tick){
            aim.x=last.x;aim.y=last.y;
            if(aim.samples.size>1){
                Sample previous=aim.samples.get(aim.samples.size-2);
                float ahead=Mathf.clamp(tick-last.tick,0f,3f)/(last.tick-previous.tick);
                aim.x+=(last.x-previous.x)*ahead;aim.y+=(last.y-previous.y)*ahead;
            }
            return;
        }
        for(int index=1;index<aim.samples.size;index++){
            Sample end=aim.samples.get(index);if(end.tick<tick)continue;
            Sample start=aim.samples.get(index-1);float progress=(tick-start.tick)/(end.tick-start.tick);
            aim.x=Mathf.lerp(start.x,end.x,progress);aim.y=Mathf.lerp(start.y,end.y,progress);return;
        }
    }
    public static Aim remote(Unit ship){
        if(!Vars.net.client() || ship.isLocal() || !ship.isValid())return null;
        Aim aim=aims.get(ship.id);
        return aim!=null && Time.time-aim.received<=60f && aim.kind==ShipVariants.current(ship.type).weapon().ordinal()?aim:null;
    }
    /** Listen hosts also need smooth guest visuals; this accessor is never used for damage or mount input. */
    public static Aim visual(Unit ship){
        if(Vars.net.client())return remote(ship);
        if(!Vars.net.server() || ship.isLocal() || !ship.isValid())return null;
        Aim aim=aims.get(ship.id);
        return aim!=null && Time.time-aim.received<=15f && aim.kind==ShipVariants.current(ship.type).weapon().ordinal()?aim:null;
    }
    /** Finish at the last received cursor position, rather than the buffered visual position. */
    public static boolean finalAim(Unit ship,arc.math.geom.Vec2 point){
        Aim aim=visual(ship);if(aim==null || aim.samples.isEmpty())return false;
        Sample last=aim.samples.peek();point.set(last.x,last.y);return true;
    }
    public static boolean shooting(Unit ship){Aim aim=remote(ship);return (aim==null?ship.isShooting:aim.shooting) && ship.canShoot();}
}
