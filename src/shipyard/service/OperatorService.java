package shipyard.service;

import arc.*;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Vec2;
import arc.struct.IntMap;
import arc.util.Time;
import java.io.*;
import java.util.UUID;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.Units;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.Layer;
import mindustry.io.SaveFileReader.CustomChunk;
import mindustry.io.SaveVersion;
import shipyard.content.ShipVariants;
import shipyard.model.*;

/** One authority-selected target per tether; native forces preserve movement, AI and collisions. */
public final class OperatorService{
    private static final String packet="modular-shipyard-operator-v1";
    private static final IntMap<State> states=new IntMap<>();
    private static String epoch=UUID.randomUUID().toString();
    private static float timer;
    private static final class State{
        final int id;final ShipTier tier;
        float energy=100f,x,y;
        boolean locked,held,dirty=true;
        Unit bound,target;int targetId=-1;
        State(int id,ShipTier tier){this.id=id;this.tier=tier;}
    }
    private OperatorService(){}
    public static void init(){
        Events.on(ResetEvent.class,e->clear());Events.on(WorldLoadBeginEvent.class,e->clear());
        Events.run(Trigger.beforeGameUpdate,OperatorService::update);
        SaveVersion.addCustomChunk("modular-shipyard-operator-v1",new CustomChunk(){
            public void write(DataOutput out)throws IOException{
                out.writeInt(1);out.writeUTF(epoch);
                var present=states.values().toArray().select(state->unit(state.id)!=null);
                out.writeInt(present.size);
                for(State state:present){out.writeInt(state.id);out.writeByte(state.tier.ordinal());out.writeFloat(state.energy);out.writeBoolean(state.locked);}
            }
            public void read(DataInput in)throws IOException{
                if(in.readInt()!=1)throw new IOException("Unsupported Operator state");
                String saved=in.readUTF();if(Vars.net.client())epoch=saved;
                int count=in.readInt();if(count<0 || count>100000)throw new IOException("Invalid Operator count");
                for(int i=0;i<count;i++){
                    int id=in.readInt(),tier=in.readUnsignedByte();float energy=in.readFloat();boolean locked=in.readBoolean();
                    State state=accept(id,tier,energy,locked,-1,0f,0f);
                    if(state==null)throw new IOException("Invalid Operator state");
                    // A loaded held-fire flag never silently grabs another enemy.
                    state.held=true;
                }
            }
        });
        if(Vars.netClient!=null)Vars.netClient.addPacketHandler(packet,payload->{
            if(!Vars.net.client() || payload==null || payload.length()>240)return;
            String[] fields=payload.split(",",-1);if(fields.length!=8 || !epoch.equals(fields[0]))return;
            try{accept(Integer.parseInt(fields[1]),Integer.parseInt(fields[2]),Float.parseFloat(fields[3]),Boolean.parseBoolean(fields[4]),Integer.parseInt(fields[5]),Float.parseFloat(fields[6]),Float.parseFloat(fields[7]));}
            catch(NumberFormatException malformedPacket){return;}
        });
    }
    private static void clear(){states.clear();epoch=UUID.randomUUID().toString();timer=0;}
    private static Unit unit(int id){Unit ship=Groups.unit.getByID(id);return ship==null?ShipyardSessions.docked(id):ship;}
    private static State state(Unit ship){
        ShipTier tier=ShipTier.of(ship.type);State state=states.get(ship.id);
        if(state==null || state.tier!=tier || state.bound!=null && state.bound!=ship){state=new State(ship.id,tier);states.put(ship.id,state);}
        state.bound=ship;return state;
    }
    private static State accept(int id,int tier,float energy,boolean locked,int target,float x,float y){
        if(tier<0 || tier>=ShipTier.values().length || !Float.isFinite(energy) || energy<0 || energy>100.01f || !Float.isFinite(x) || !Float.isFinite(y))return null;
        State old=states.get(id),state=new State(id,ShipTier.values()[tier]);
        state.energy=Mathf.clamp(energy,0,100);state.locked=locked;state.targetId=target;state.x=x;state.y=y;
        if(old!=null){state.bound=old.bound;state.held=old.held;}
        states.put(id,state);return state;
    }
    private static boolean equipped(Unit ship){return ShipTier.of(ship.type)!=null && ShipVariants.current(ship.type).weapon()==ShipWeapon.operator;}
    private static boolean valid(Unit ship,Unit target,float range){
        return target!=null && target.isAdded() && !target.dead && !(target instanceof BlockUnitc)
            && target.type.physics && target.hittable() && target.targetable(ship.team) && target.team!=ship.team
            && target.mass()>0 && target.within(ship,range);
    }
    private static boolean claimed(int id){for(State state:states.values())if(state.targetId==id)return true;return false;}
    private static void drop(State state){if(state.targetId!=-1){state.targetId=-1;state.target=null;state.dirty=true;}}
    public static void update(){
        if(!Vars.state.isPlaying())return;
        float dt=Math.max(0f,Time.delta);timer+=dt;
        if(!Vars.net.client())for(Unit ship:Groups.unit)if(equipped(ship))state(ship);
        for(State state:states.values().toArray()){
            Unit ship=unit(state.id);
            if(ship==null || ship.dead){if(state.bound!=null)states.remove(state.id);continue;}
            state.bound=ship;
            if(Vars.net.client()){
                // Server snapshots drive AI/remote targets; replay force only for the local player.
                Unit local=Vars.player==null?null:Vars.player.unit();
                if(state.targetId!=-1 && equipped(ship) && ship.isAdded() && local!=null && local.id==state.targetId
                    && valid(ship,local,UtilityBalance.range(state.tier,ShipWeapon.operator)))pull(local,state,dt);
                continue;
            }
            float range=UtilityBalance.range(state.tier,ShipWeapon.operator);
            boolean held=equipped(ship) && ship.isAdded() && ship.isShooting && ship.canShoot() && Float.isFinite(ship.aimX) && Float.isFinite(ship.aimY);
            if(state.locked && !held && state.energy>=25f){state.locked=false;state.dirty=true;}
            if(!held)drop(state);
            if(held && !state.held && !state.locked && state.energy>0f){
                Unit target=Units.closestEnemy(ship.team,ship.aimX,ship.aimY,16f,t->valid(ship,t,range) && !claimed(t.id) && t.within(ship.aimX,ship.aimY,16f+t.hitSize*.5f));
                if(target!=null){state.target=target;state.targetId=target.id;state.dirty=true;sound(ship,1.15f);}
            }
            if(state.targetId!=-1 && (state.target==null || state.target.id!=state.targetId || !valid(ship,state.target,range)))drop(state);
            if(state.targetId!=-1){
                Vec2 anchor=new Vec2(ship.aimX-ship.x,ship.aimY-ship.y).limit(range).add(ship.x,ship.y);state.x=anchor.x;state.y=anchor.y;
                float reference=UnitTypes.dagger.hitSize*UnitTypes.dagger.hitSize*Mathf.pi;
                state.energy=Math.max(0f,state.energy-.3f*Mathf.clamp(state.target.mass()/reference,1f,4f)*dt);
                if(state.energy==0f){state.locked=true;drop(state);state.dirty=true;sound(ship,.7f);}
                else pull(state.target,state,dt);
            }else state.energy=Math.min(100f,state.energy+.5f*dt);
            state.held=held;
            if(Vars.net.server() && (state.dirty || timer>=12f)){
                Call.clientPacketReliable(packet,epoch+","+state.id+","+state.tier.ordinal()+","+state.energy+","+state.locked+","+state.targetId+","+state.x+","+state.y);state.dirty=false;
            }
        }
        if(timer>=12f)timer=0;
    }
    private static void pull(Unit target,State state,float dt){
        float mass=target.mass();if(mass<=0)return;
        Vec2 change=new Vec2(state.x-target.x,state.y-target.y).scl(.08f).limit(3f).sub(target.vel)
            .limit(UtilityBalance.operatorForce(state.tier)/mass*dt).scl(mass);
        target.impulseNet(change);
    }
    private static void sound(Unit ship,float pitch){
        if(Vars.net.server())Call.soundAt(Sounds.beamParallax,ship.x,ship.y,1f,pitch);
        else Sounds.beamParallax.at(ship.x,ship.y,pitch,1f);
    }
    public static float energy(Unit ship){State state=states.get(ship.id);return state==null?100f:state.energy;}
    public static int target(Unit ship){State state=states.get(ship.id);return state==null?-1:state.targetId;}
    public static boolean locked(Unit ship){State state=states.get(ship.id);return state!=null && state.locked;}
    public static void draw(Unit ship){
        State state=states.get(ship.id);float energy=state==null?100f:state.energy;
        Color color=ship.type.engineColor==null?ship.team.color:ship.type.engineColor;
        float z=Draw.z();Draw.z(Layer.bullet+1f);
        Unit target=state==null?null:Groups.unit.getByID(state.targetId);
        if(target!=null && target.team!=ship.team && target.isAdded()){
            Draw.color(color);Draw.alpha(.65f);Lines.stroke(1.1f);
            for(var mount:ship.mounts){
                var gun=mount.weapon;float rotation=ship.rotation-90f,angle=rotation+(gun.rotate?mount.rotation:gun.baseRotation);
                float x=ship.x+Angles.trnsx(rotation,gun.x,gun.y)+Angles.trnsx(angle,gun.shootX,gun.shootY);
                float y=ship.y+Angles.trnsy(rotation,gun.x,gun.y)+Angles.trnsy(angle,gun.shootX,gun.shootY);
                Lines.line(x,y,target.x,target.y);
                for(int i=0;i<3;i++){
                    float p=(Time.time*.035f+i/3f)%1f;
                    float px=Mathf.lerp(x,target.x,p),py=Mathf.lerp(y,target.y,p);
                    Draw.alpha(.9f);Fill.circle(px,py,.8f);Draw.alpha(.65f);
                }
            }
            float radius=target.hitSize*.5f+3f;
            for(int i=0;i<4;i++){
                float angle=Time.time*2f+i*90f;
                float x=target.x+Angles.trnsx(angle,radius),y=target.y+Angles.trnsy(angle,radius);
                Lines.lineAngle(x,y,angle+140f,3f);Lines.lineAngle(x,y,angle-140f,3f);
            }
            Draw.alpha(.3f);Lines.circle(target.x,target.y,radius-1f);Draw.alpha(.65f);
            Lines.circle(state.x,state.y,3f);Lines.line(state.x-4f,state.y,state.x+4f,state.y);Lines.line(state.x,state.y-4f,state.x,state.y+4f);
        }
        Draw.z(Layer.overlayUI);float width=Math.max(18f,ship.hitSize*1.5f),y=ship.y-ship.hitSize-7f;
        Draw.color(Color.darkGray);Fill.rect(ship.x,y,width,3f);
        Draw.color(state!=null && state.locked?Color.orange:color);Fill.rect(ship.x-width/2f+width*energy/200f,y,width*energy/100f,2f);
        Draw.reset();Draw.z(z);
    }
}
