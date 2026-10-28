package shipyard.service;

import arc.*;
import arc.graphics.Color;
import arc.audio.Sound;
import arc.math.*;
import arc.math.geom.Vec2;
import arc.math.geom.Intersector;
import arc.struct.*;
import arc.util.Time;
import java.io.*;
import java.util.UUID;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.Units;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.SaveFileReader.CustomChunk;
import mindustry.io.SaveVersion;
import shipyard.content.*;
import shipyard.model.*;

/** Authority-owned nine-slot native swarm. Native unit snapshots carry position and removal. */
public final class EigenService{
    public static final int capacity=9;
    public static final float range=192f,replacement=180f,contactInterval=30f,launchInterval=180f/(capacity-1),damageShare=6f/capacity;
    private static final String packet="modular-shipyard-eigen-v3";
    private static final IntMap<State> states=new IntMap<>();
    private static final IntMap<State> links=new IntMap<>();
    private static String epoch=UUID.randomUUID().toString();
    private static float timer,motionTimer;
    private static final Vec2 movement=new Vec2();
    private static final class State{
        final int owner;final ShipTier tier;
        final int[] ids=new int[capacity];final float[] regen=new float[capacity],cooldown=new float[capacity],charge=new float[capacity];
        // Transient motion samples: never trust client-supplied velocity for damage.
        final float[] dx=new float[capacity],dy=new float[capacity];
        final float[] lastX=new float[capacity],lastY=new float[capacity];
        final boolean[] sampled=new boolean[capacity];
        float sampleDelta=1f,contactSoundAt=-100f,launchCooldown;
        int launchMount;
        Unit bound;boolean dirty=true;
        State(int owner,ShipTier tier){this.owner=owner;this.tier=tier;for(int i=0;i<capacity;i++){ids[i]=-1;regen[i]=0f;charge[i]=budget(tier);}}
    }
    private EigenService(){}
    public static void init(){
        Events.on(ResetEvent.class,e->clear());Events.on(WorldLoadBeginEvent.class,e->clear());
        Events.run(Trigger.beforeGameUpdate,EigenService::update);
        SaveVersion.addCustomChunk("modular-shipyard-eigen-v1",new CustomChunk(){
            public void write(DataOutput out)throws IOException{
                out.writeInt(4);out.writeUTF(epoch);var present=states.values().toArray().select(state->owner(state.owner)!=null);
                out.writeInt(present.size);for(State state:present){out.writeInt(state.owner);out.writeByte(state.tier.ordinal());out.writeFloat(state.launchCooldown);out.writeInt(state.launchMount);for(int i=0;i<capacity;i++){out.writeInt(state.ids[i]);out.writeFloat(state.regen[i]);out.writeFloat(state.cooldown[i]);out.writeFloat(state.charge[i]);}}
            }
            public void read(DataInput in)throws IOException{
                int version=in.readInt();if(version<1 || version>4)throw new IOException("Unsupported Eigen state");String saved=in.readUTF();if(Vars.net.client())epoch=saved;
                int count=in.readInt();if(count<0 || count>100000)throw new IOException("Invalid Eigen count");
                states.clear();links.clear();IntSet owners=new IntSet();
                for(int n=0;n<count;n++){
                    int id=in.readInt(),tier=in.readUnsignedByte();if(tier>=ShipTier.values().length || !owners.add(id))throw new IOException("Invalid Eigen owner");
                    State state=new State(id,ShipTier.values()[tier]);
                    if(version>=3){state.launchCooldown=in.readFloat()*(version<4?launchInterval/36f:1f);state.launchMount=in.readInt();
                        if(!Float.isFinite(state.launchCooldown) || state.launchCooldown<0 || state.launchCooldown>launchInterval || state.launchMount<0 || state.launchMount>=capacity)throw new IOException("Invalid Eigen launch state");}
                    for(int i=0;i<(version<4?6:capacity);i++){state.ids[i]=in.readInt();state.regen[i]=in.readFloat();state.cooldown[i]=in.readFloat();state.charge[i]=version>=2?in.readFloat()*(version<4?damageShare:1f):budget(state.tier);if(!valid(state,i))throw new IOException("Invalid Eigen slot");}
                    if(!accept(state))throw new IOException("Duplicate Eigen drone");
                }
            }
        });
        if(Vars.netClient!=null)Vars.netClient.addPacketHandler(packet,text->{
            if(!Vars.net.client() || text==null || text.length()>700)return;
            String[] fields=text.split(",",-1);if(!epoch.equals(fields[0]))return;
            if(fields.length==3 && fields[2].equals("remove")){
                int ownerId;
                try{ownerId=Integer.parseInt(fields[1]);}catch(NumberFormatException malformedOwner){return;}
                EigenMotion.forgetOwner(ownerId);
                State old=states.remove(ownerId);if(old!=null)for(int id:old.ids)links.remove(id);return;
            }
            if(fields.length!=3+capacity*4)return;
            State snapshot;
            try{
                int id=Integer.parseInt(fields[1]),tier=Integer.parseInt(fields[2]);if(tier<0 || tier>=3)return;
                snapshot=new State(id,ShipTier.values()[tier]);
                for(int i=0;i<capacity;i++){snapshot.ids[i]=Integer.parseInt(fields[3+i*4]);snapshot.regen[i]=Float.parseFloat(fields[4+i*4]);snapshot.cooldown[i]=Float.parseFloat(fields[5+i*4]);snapshot.charge[i]=Float.parseFloat(fields[6+i*4]);if(!valid(snapshot,i))return;}
            }catch(NumberFormatException malformedPacket){return;}
            accept(snapshot);
        });
    }
    private static boolean valid(State state,int i){return state.owner>=0 && state.ids[i]!=state.owner && state.ids[i]>=-1 && Float.isFinite(state.regen[i]) && state.regen[i]>=0 && state.regen[i]<=replacement && Float.isFinite(state.cooldown[i]) && state.cooldown[i]>=0 && state.cooldown[i]<=300 && Float.isFinite(state.charge[i]) && state.charge[i]>=0 && state.charge[i]<=budget(state.tier);}
    private static boolean accept(State state){
        IntSet ids=new IntSet();for(int id:state.ids)if(id!=-1 && (!ids.add(id) || links.containsKey(id) && links.get(id).owner!=state.owner))return false;
        State old=states.get(state.owner);if(old!=null)for(int id:old.ids)if(id!=-1)links.remove(id);
        states.put(state.owner,state);for(int id:state.ids)if(id!=-1){links.put(id,state);Unit drone=Groups.unit.getByID(id);if(drone!=null && EigenContent.isDrone(drone))EigenContent.bind(drone,state.tier);}return true;
    }
    static String worldEpoch(){return epoch;}
    private static void clear(){states.clear();links.clear();timer=motionTimer=0;epoch=UUID.randomUUID().toString();}
    private static Unit owner(int id){Unit ship=Groups.unit.getByID(id);return ship==null?ShipyardSessions.docked(id):ship;}
    private static boolean equipped(Unit ship){return ShipTier.of(ship.type)!=null && ShipVariants.current(ship.type).weapon()==ShipWeapon.eigen;}
    private static void retire(State state){
        for(int i=0;i<capacity;i++)if(state.ids[i]!=-1){Unit drone=Groups.unit.getByID(state.ids[i]);if(drone!=null && EigenContent.isDrone(drone))Call.unitDespawn(drone);links.remove(state.ids[i]);state.ids[i]=-1;state.regen[i]=replacement;state.sampled[i]=false;state.dirty=true;}
    }
    public static void update(){
        if(!Vars.state.isPlaying())return;float dt=Math.max(0,Time.delta);timer+=dt;motionTimer+=dt;
        if(!Vars.net.client())for(Unit ship:Groups.unit)if(equipped(ship)){
            State state=states.get(ship.id);ShipTier tier=ShipTier.of(ship.type);
            if(state==null || state.tier!=tier || state.bound!=null && state.bound!=ship){if(state!=null)retire(state);state=new State(ship.id,tier);states.put(ship.id,state);}state.bound=ship;
        }
        for(State state:states.values().toArray()){
            Unit ship=owner(state.owner);
            if(Vars.net.client()){
                for(int id:state.ids){Unit drone=Groups.unit.getByID(id);if(drone!=null && EigenContent.isDrone(drone))EigenContent.bind(drone,state.tier);}continue;
            }
            if(ship==null || ship.dead || state.bound!=null && state.bound!=ship){retire(state);if(Vars.net.server())Call.clientPacketReliable(packet,epoch+","+state.owner+",remove");states.remove(state.owner);continue;}
            state.bound=ship;boolean active=equipped(ship) && ship.isAdded() && ShipTier.of(ship.type)==state.tier;
            if(!active)retire(state);
            boolean attack=active && ship.isShooting && ship.canShoot() && Float.isFinite(ship.aimX) && Float.isFinite(ship.aimY);
            state.launchCooldown=Math.max(0f,state.launchCooldown-dt);
            state.sampleDelta=Math.max(.001f,dt);
            for(int i=0;i<capacity;i++){
                state.cooldown[i]=Math.max(0,state.cooldown[i]-dt);
                Unit drone=Groups.unit.getByID(state.ids[i]);
                if(drone!=null && (!EigenContent.isDrone(drone) || drone.team!=ship.team || drone.dead))drone=null;
                if(drone==null){
                    if(state.ids[i]!=-1){links.remove(state.ids[i]);state.ids[i]=-1;state.regen[i]=replacement;state.sampled[i]=false;state.dirty=true;}
                    state.regen[i]=Math.max(0,state.regen[i]-dt);
                    if(attack && state.regen[i]==0 && state.launchCooldown==0 && ship.mounts.length>0)
                        drone=launch(ship,state,i);
                }
                if(drone==null)continue;
                EigenContent.bind(drone,state.tier);
                state.dx[i]=state.sampled[i]?drone.x-state.lastX[i]:0f;state.dy[i]=state.sampled[i]?drone.y-state.lastY[i]:0f;
                // Teleports must not produce a damaging line across the map.
                if(Mathf.dst(0,0,state.dx[i],state.dy[i])>32f+drone.vel.len()*dt){state.dx[i]=state.dy[i]=0f;}
                state.lastX[i]=drone.x;state.lastY[i]=drone.y;state.sampled[i]=true;
                float angle=attack?Angles.angle(ship.x,ship.y,ship.aimX,ship.aimY):Time.time*1.5f+i*360f/capacity;
                float distance=attack?Math.min(range,Mathf.dst(ship.x,ship.y,ship.aimX,ship.aimY)):18f;
                float x=ship.x+Angles.trnsx(angle,distance),y=ship.y+Angles.trnsy(angle,distance);
                if(attack){
                    // Offset phases fill a broad figure-eight, repeatedly passing through the cursor.
                    float phase=Time.time*4f+i*360f/capacity+(ship.id%19)*7f;
                    float forward=Mathf.sinDeg(phase*2f)*6f,side=Mathf.cosDeg(phase)*12f;
                    x+=Angles.trnsx(angle,forward,side);y+=Angles.trnsy(angle,forward,side);
                }
                // Orbit correction rides on the owner's velocity, so fast ships do not leave it behind.
                // Attack guidance deliberately retains its existing 4.8 world-unit speed.
                float speed=Math.min(attack?4.8f:Math.max(5.5f,ship.vel.len()+3f),drone.dst(x,y)*.24f);
                movement.trns(drone.angleTo(x,y),speed);
                if(!attack)movement.add(ship.vel);
                drone.moveAt(movement);if(movement.len()>.1f)drone.rotation=drone.vel.angle();
                if(attack && state.cooldown[i]==0)contact(ship,drone,state,i);
            }
            if(Vars.net.server() && motionTimer>=3f)EigenMotion.send(ship,state.ids,epoch);
            if(Vars.net.server() && (state.dirty || timer>=12f)){
                StringBuilder text=new StringBuilder(epoch).append(',').append(state.owner).append(',').append(state.tier.ordinal());
                for(int i=0;i<capacity;i++)text.append(',').append(state.ids[i]).append(',').append(state.regen[i]).append(',').append(state.cooldown[i]).append(',').append(state.charge[i]);
                Call.clientPacketReliable(packet,text.toString());state.dirty=false;
            }
        }
        if(!Vars.net.client())for(Unit ship:Groups.unit)if(EigenContent.isDrone(ship) && !links.containsKey(ship.id))Call.unitDespawn(ship);
        if(timer>=12f)timer=0;
        if(motionTimer>=3f)motionTimer%=3f;
    }
    /** Launch through alternating physical muzzles, using the same native transform as the gun sprite. */
    private static Unit launch(Unit ship,State state,int slot){
        var mount=ship.mounts[state.launchMount%ship.mounts.length];var gun=mount.weapon;
        state.launchMount=(state.launchMount+1)%ship.mounts.length;
        float rotation=ship.rotation-90f,weaponRotation=rotation+(gun.rotate?mount.rotation:gun.baseRotation);
        float recoil=Mathf.pow(mount.recoil,gun.recoilPow)*gun.recoil;
        float x=ship.x+Angles.trnsx(rotation,gun.x,gun.y)+Angles.trnsx(weaponRotation,gun.shootX,gun.shootY-recoil);
        float y=ship.y+Angles.trnsy(rotation,gun.x,gun.y)+Angles.trnsy(weaponRotation,gun.shootX,gun.shootY-recoil);
        float direction=weaponRotation+90f;
        Unit drone=EigenContent.type(state.tier).create(ship.team);drone.set(x,y);drone.rotation=direction;drone.vel.trns(direction,4.8f);drone.add();
        state.ids[slot]=drone.id;links.put(drone.id,state);state.sampled[slot]=false;state.charge[slot]=budget(state.tier);
        state.launchCooldown=launchInterval;state.dirty=true;mount.recoil=mount.heat=1f;mount.totalShots++;
        sound(Sounds.shootPulsar,drone,1.3f);
        if(Vars.net.server())Call.effect(Fx.shootSmallColor,x,y,direction,colour(drone));else Fx.shootSmallColor.at(x,y,direction,colour(drone));
        return drone;
    }
    private static void contact(Unit owner,Unit drone,State state,int slot){
        Teamc target=Units.closestTarget(owner.team,drone.x,drone.y,64f,
            enemy->enemy.isAdded() && enemy.hittable() && enemy.targetable(owner.team) && crosses(drone,state,slot,enemy.x,enemy.y,(enemy.hitSize+drone.hitSize)*.5f+1f),
            build->!build.block.underBullets && crosses(drone,state,slot,build.x,build.y,build.hitSize()*.5f+drone.hitSize*.5f+1f));
        if(target==null)return;
        var type=owner.mounts[0].weapon.bullet;Bullet hit=type.create(owner,owner.team,drone.x,drone.y,drone.rotation);
        float before=durability(target);
        float vx=state.dx[slot]/state.sampleDelta,vy=state.dy[slot]/state.sampleDelta;
        float travelSpeed=Mathf.dst(0,0,vx,vy);
        if(target instanceof Unit enemy){vx-=enemy.vel.x;vy-=enemy.vel.y;}
        // Reward relative travel, not orbit animation, requested velocity or coasting with an enemy.
        float bonus=1f+Mathf.clamp((Math.min(travelSpeed,Mathf.dst(0,0,vx,vy))-1.2f)/2.4f);
        hit.damage*=bonus;
        if(target instanceof Unit enemy)type.hitEntity(hit,enemy,enemy.health);
        else if(target instanceof Building build)build.collision(hit);
        state.charge[slot]=Math.max(0,state.charge[slot]-Math.max(0,before-durability(target))/bonus);
        hit.remove();state.cooldown[slot]=contactInterval/Math.max(.1f,owner.reloadMultiplier);state.dirty=true;
        Color hitColour=colour(drone);
        if(Time.time-state.contactSoundAt>=6f){sound(Sounds.shootSap,drone,1.25f);state.contactSoundAt=Time.time;}
        if(state.charge[slot]<=.001f){
            sound(Sounds.explosionPlasmaSmall,drone,1.4f);
            if(Vars.net.server())Call.effect(Fx.disperseTrail,drone.x,drone.y,drone.rotation,hitColour);else Fx.disperseTrail.at(drone.x,drone.y,drone.rotation,hitColour);
            Call.unitDespawn(drone);links.remove(state.ids[slot]);state.ids[slot]=-1;state.regen[slot]=replacement;
        }
        if(Vars.net.server())Call.effect(Fx.hitLaserColor,drone.x,drone.y,drone.rotation,hitColour);else Fx.hitLaserColor.at(drone.x,drone.y,drone.rotation,hitColour);
    }
    private static boolean crosses(Unit drone,State state,int slot,float x,float y,float radius){
        return Intersector.distanceSegmentPoint(drone.x-state.dx[slot],drone.y-state.dy[slot],drone.x,drone.y,x,y)<=radius;
    }
    private static float durability(Teamc target){
        return target instanceof Unit ship?Math.max(0,ship.health)+Math.max(0,ship.shield):target instanceof Building b?Math.max(0,b.health):0f;
    }
    public static float budget(ShipTier tier){return switch(tier){case alpha->20f*damageShare;case beta->55f*damageShare;case gamma->130f*damageShare;};}
    private static void sound(Sound sound,Unit drone,float pitch){
        if(Vars.net.server())Call.soundAt(sound,drone.x,drone.y,1f,pitch);
        else if(!Vars.net.client())sound.at(drone.x,drone.y,pitch,1f);
    }
    public static int[] drones(Unit owner){State state=states.get(owner.id);return state==null?new int[0]:state.ids.clone();}
    public static Color colour(Unit drone){State state=links.get(drone.id);Unit ship=state==null?null:owner(state.owner);return ship==null?drone.team.color:ship.type.engineColor==null?ship.team.color:ship.type.engineColor;}
}
