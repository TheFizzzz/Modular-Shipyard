package shipyard.service;

import arc.*;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.Scl;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.IntMap;
import arc.util.Time;
import java.io.*;
import java.util.UUID;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.Layer;
import mindustry.io.SaveFileReader.CustomChunk;
import mindustry.io.SaveVersion;
import shipyard.content.ShipVariants;
import shipyard.model.*;

/** Authority-owned utility state survives weapon/view changes, docking, saves and join snapshots. */
public final class UtilityService{
    private static final String packet="modular-shipyard-utility-v1",pulsePacket="modular-shipyard-pulse-v1";
    private static final IntMap<State> states=new IntMap<>();
    private static String epoch=UUID.randomUUID().toString();
    private static float syncTimer;
    private static final class State{
        final int id;final ShipTier tier;
        float health,cooldown,pulse,angle,renderAngle;
        boolean active,dirty=true;Unit bound;
        State(int id,ShipTier tier){this.id=id;this.tier=tier;health=UtilityBalance.capacity(tier);}
    }
    private static final Effect burst=new Effect(30f,300f,e->{
        float radius=e.rotation*e.fin(arc.math.Interp.pow2Out);
        Draw.color(e.color);Draw.alpha(.8f*e.fout());Lines.stroke(2.8f*e.fout());Lines.circle(e.x,e.y,radius);
        Draw.alpha(.25f*e.fout());Lines.stroke(5f*e.fout());Lines.circle(e.x,e.y,radius*.92f);
        Draw.alpha(.65f*e.fout());Lines.stroke(.8f*e.fout());Lines.circle(e.x,e.y,radius*.7f);
        for(int i=0;i<12;i++){
            float angle=i*30f+e.id%30,inner=radius*.8f;
            Lines.line(e.x+Angles.trnsx(angle,inner),e.y+Angles.trnsy(angle,inner),e.x+Angles.trnsx(angle,radius),e.y+Angles.trnsy(angle,radius));
        }
        Draw.reset();Lines.stroke(1f);
    });
    private static final Effect[] breaks=makeBreaks();
    private static Effect[] makeBreaks(){
        Effect[] effects=new Effect[ShipTier.values().length];
        for(ShipTier tier:ShipTier.values())effects[tier.ordinal()]=new Effect(28f,300f,e->{
            float[] v=vertices(e.x,e.y,UtilityBalance.range(tier,ShipWeapon.invariant),UtilityBalance.shieldWidth(tier),e.rotation);
            Draw.color(e.color);Draw.alpha(e.fout());Lines.stroke(2f*e.fout());
            Lines.polyline(v,v.length,true);
        });
        return effects;
    }
    private UtilityService(){}
    public static void init(){
        Events.on(ResetEvent.class,e->clear());Events.on(WorldLoadBeginEvent.class,e->clear());
        // Before native bullet collisions, so an incoming shot can be absorbed before damaging the ship.
        Events.run(Trigger.beforeGameUpdate,UtilityService::update);
        SaveVersion.addCustomChunk("modular-shipyard-utilities-v1",new CustomChunk(){
            public void write(DataOutput out)throws IOException{
                out.writeInt(1);out.writeUTF(epoch);
                var present=states.values().toArray().select(state->unit(state.id)!=null);
                out.writeInt(present.size);
                for(State state:present){out.writeInt(state.id);out.writeByte(state.tier.ordinal());out.writeFloat(state.health);out.writeFloat(state.cooldown);out.writeFloat(state.pulse);out.writeFloat(state.angle);}
            }
            public void read(DataInput in)throws IOException{
                if(in.readInt()!=1)throw new IOException("Unsupported utility state");
                String saved=in.readUTF();if(Vars.net.client())epoch=saved;
                int count=in.readInt();if(count<0 || count>100000)throw new IOException("Invalid utility count");
                for(int i=0;i<count;i++){
                    int id=in.readInt(),tier=in.readUnsignedByte();float hp=in.readFloat(),cd=in.readFloat(),pulse=in.readFloat(),angle=in.readFloat();
                    if(!accept(id,tier,hp,cd,pulse,angle,false))throw new IOException("Invalid utility state");
                }
            }
        });
        if(Vars.netClient!=null){
            Vars.netClient.addPacketHandler(packet,text->{
                if(!Vars.net.client() || text==null || text.length()>240)return;
                String[] fields=text.split(",",-1);if(fields.length!=8 || !epoch.equals(fields[0]))return;
                try{accept(Integer.parseInt(fields[1]),Integer.parseInt(fields[2]),Float.parseFloat(fields[3]),Float.parseFloat(fields[4]),Float.parseFloat(fields[5]),Float.parseFloat(fields[6]),Boolean.parseBoolean(fields[7]));}catch(NumberFormatException malformedPacket){return;}
            });
            Vars.netClient.addPacketHandler(pulsePacket,text->{
                if(!Vars.net.client() || text==null || text.length()>160)return;
                String[] fields=text.split(",",-1);if(fields.length!=5 || !epoch.equals(fields[0]))return;
                try{
                    int team=Integer.parseInt(fields[1]),tier=Integer.parseInt(fields[2]);float x=Float.parseFloat(fields[3]),y=Float.parseFloat(fields[4]);
                    if(tier<0 || tier>=ShipTier.values().length || !Float.isFinite(x) || !Float.isFinite(y))return;
                    // Remote units follow authority snapshots; only local enemy movement needs the impulse replay.
                    Unit local=Vars.player==null?null:Vars.player.unit();
                    if(local!=null && local.isAdded() && !(local instanceof BlockUnitc) && local.team.id!=team && local.hittable())push(local,x,y,ShipTier.values()[tier]);
                }catch(NumberFormatException malformedPacket){return;}
            });
        }
    }
    private static void clear(){states.clear();epoch=UUID.randomUUID().toString();syncTimer=0;}
    private static Unit unit(int id){Unit ship=Groups.unit.getByID(id);return ship==null?ShipyardSessions.docked(id):ship;}
    private static State state(Unit unit){
        ShipTier tier=ShipTier.of(unit.type);if(tier==null)return null;
        State state=states.get(unit.id);
        if(state==null || state.tier!=tier){state=new State(unit.id,tier);states.put(unit.id,state);}
        state.bound=unit;return state;
    }
    private static boolean accept(int id,int ordinal,float hp,float cd,float pulse,float angle,boolean active){
        if(ordinal<0 || ordinal>=ShipTier.values().length || !Float.isFinite(hp) || !Float.isFinite(cd) || !Float.isFinite(pulse) || !Float.isFinite(angle))return false;
        ShipTier tier=ShipTier.values()[ordinal];
        if(hp<0 || hp>UtilityBalance.capacity(tier)+.01f || cd<0 || cd>UtilityBalance.recovery(tier)+.01f || pulse<0 || pulse>600f)return false;
        State old=states.get(id),state=new State(id,tier);state.health=hp;state.cooldown=cd;state.pulse=pulse;state.angle=angle;state.active=active;state.renderAngle=old==null?angle:old.renderAngle;
        if(old!=null){state.bound=old.bound;}
        states.put(id,state);return true;
    }
    /** Called by the engine trigger and the native-engine regression checks. */
    public static void update(){
        if(!Vars.state.isPlaying())return;
        float dt=Math.max(0f,Time.delta);syncTimer+=dt;
        // Every supported equipped utility gets private state, never shared Weapon/UnitType health.
        for(Unit ship:Groups.unit)if(ShipTier.of(ship.type)!=null && ShipVariants.current(ship.type).weapon().utility() && ShipVariants.current(ship.type).weapon()!=ShipWeapon.operator)state(ship);
        float travel=0f;
        if(!Vars.net.client())for(Bullet b:Groups.bullet)travel=Math.max(travel,b.vel.len()*dt);
        for(State state:states.values().toArray()){
            Unit ship=unit(state.id);
            if(ship==null || ship.dead){if(state.bound!=null)states.remove(state.id);continue;}
            state.bound=ship;
            ShipWeapon kind=ShipVariants.current(ship.type).weapon();
            boolean wasLocked=state.cooldown>0f;
            state.cooldown=Math.max(0f,state.cooldown-dt);
            state.pulse=Math.max(0f,state.pulse-dt*(kind==ShipWeapon.divergence?ship.reloadMultiplier:1f));
            if(wasLocked && state.cooldown==0f){
                state.health=UtilityBalance.capacity(state.tier);state.dirty=true;
                if(!Vars.net.client()){Fx.absorb.at(ship.x,ship.y,0f,colour(ship));sound(Sounds.shieldHit,ship,.9f);}
            }
            if(!Vars.net.client() || ship.isLocal())state.angle=Mathf.dst(ship.x,ship.y,ship.aimX,ship.aimY)>.1f?ship.angleTo(ship.aimX,ship.aimY):ship.rotation;
            WeaponMotion.Aim aim=WeaponMotion.remote(ship);
            float visualAngle=aim==null?state.angle:ship.angleTo(aim.x,aim.y);
            state.renderAngle=Vars.net.client() && !ship.isLocal()?Mathf.slerpDelta(state.renderAngle,visualAngle,.45f):state.angle;
            boolean shooting=ship.isAdded() && WeaponMotion.shooting(ship);
            boolean active=kind==ShipWeapon.invariant && shooting && state.cooldown==0f && !wasLocked && state.health>0;
            if(active!=state.active){
                // Only the authority announces transitions; clients receive native positional sound calls.
                if(!Vars.net.client())sound(Sounds.shieldWave,ship,active?1.25f:.7f);
                state.active=active;state.dirty=true;
            }
            if(!Vars.net.client()){
                if(kind==ShipWeapon.divergence && shooting && state.pulse==0f){
                    pulse(ship,state.tier);state.pulse=UtilityBalance.pulseReload(state.tier);state.dirty=true;
                }
                if(state.active)absorb(ship,state,travel);
                else if(state.cooldown==0f && state.health<UtilityBalance.capacity(state.tier)){
                    state.health=Math.min(UtilityBalance.capacity(state.tier),state.health+UtilityBalance.regen(state.tier)*dt);
                }
                if(Vars.net.server() && (state.dirty || syncTimer>=12f && (kind.utility() || state.cooldown>0f || state.pulse>0f))){
                    Call.clientPacketReliable(packet,epoch+","+state.id+","+state.tier.ordinal()+","+state.health+","+state.cooldown+","+state.pulse+","+state.angle+","+state.active);state.dirty=false;
                }
            }
        }
        if(syncTimer>=12f)syncTimer=0f;
    }
    private static void sound(Sound sound,Unit ship,float pitch){
        if(Vars.net.server())Call.soundAt(sound,ship.x,ship.y,1f,pitch);
        else sound.at(ship.x,ship.y,pitch,1f);
    }
    private static Color colour(Unit ship){return ship.type.engineColor==null?ship.team.color:ship.type.engineColor;}
    private static void pulse(Unit ship,ShipTier tier){
        float radius=UtilityBalance.range(tier,ShipWeapon.divergence);
        Units.nearbyEnemies(ship.team,ship.x-radius,ship.y-radius,radius*2f,radius*2f,target->{
            if(target.hittable() && !target.dead)push(target,ship.x,ship.y,tier);
        });
        if(Vars.net.server()){
            Call.effect(burst,ship.x,ship.y,radius,colour(ship));
            Call.clientPacketReliable(pulsePacket,epoch+","+ship.team.id+","+tier.ordinal()+","+ship.x+","+ship.y);
        }else burst.at(ship.x,ship.y,radius,colour(ship));
        sound(Sounds.shootArc,ship,.6f);
    }
    private static void push(Unit target,float x,float y,ShipTier tier){
        if(target.mass()<=0f)return;
        float distance=target.dst(x,y),range=UtilityBalance.range(tier,ShipWeapon.divergence);
        if(distance>range)return;
        // Native mass scales with hitbox area. Clamp tiny-target velocity without flattening heavy-unit resistance.
        float force=Math.min(UtilityBalance.force(tier)*(.35f+.65f*(1f-distance/range)),target.mass()*12f);
        float angle=distance<.01f?target.rotation:Angles.angle(x,y,target.x,target.y);
        target.impulse(Angles.trnsx(angle,force),Angles.trnsy(angle,force));
    }
    /** Closed polygon of a thin, forward C-shaped band. The rear opening is not shielded. */
    public static float[] vertices(Unit ship,float angle){
        ShipTier tier=ShipTier.of(ship.type);
        return vertices(ship.x,ship.y,UtilityBalance.range(tier,ShipWeapon.invariant),UtilityBalance.shieldWidth(tier),angle);
    }
    private static float[] vertices(float x,float y,float radius,float width,float angle){
        int points=UtilityBalance.shieldSegments+1;
        float[] v=new float[points*4];
        for(int i=0;i<points;i++){
            float direction=angle-UtilityBalance.shieldArc/2f+UtilityBalance.shieldArc*i/(points-1);
            int outside=i*2,inside=(points*2-1-i)*2;
            float localAngle=direction-angle;
            float outerX=Mathf.cosDeg(localAngle)*(radius+width/2f)*UtilityBalance.shieldDepth;
            float outerY=Mathf.sinDeg(localAngle)*(radius+width/2f);
            float innerX=Mathf.cosDeg(localAngle)*(radius-width/2f)*UtilityBalance.shieldDepth;
            float innerY=Mathf.sinDeg(localAngle)*(radius-width/2f);
            v[outside]=x+Angles.trnsx(angle,outerX,outerY);
            v[outside+1]=y+Angles.trnsy(angle,outerX,outerY);
            v[inside]=x+Angles.trnsx(angle,innerX,innerY);
            v[inside+1]=y+Angles.trnsy(angle,innerX,innerY);
        }
        return v;
    }
    private static void fillBand(float[] v){
        int points=v.length/4;
        for(int i=0;i<points-1;i++){
            int a=i*2,b=a+2,c=(points*2-2-i)*2,d=c+2;
            Fill.quad(v[a],v[a+1],v[b],v[b+1],v[c],v[c+1],v[d],v[d+1]);
        }
    }
    public static boolean intersects(float[] v,float x,float y,float nx,float ny){
        if(Intersector.isInPolygon(v,0,v.length,x,y) || Intersector.isInPolygon(v,0,v.length,nx,ny))return true;
        for(int i=0;i<v.length;i+=2){int j=(i+2)%v.length;if(Intersector.intersectSegments(x,y,nx,ny,v[i],v[i+1],v[j],v[j+1],null))return true;}
        return false;
    }
    private static void absorb(Unit ship,State state,float travel){
        float[] polygon=vertices(ship,state.angle);
        float minX=polygon[0],maxX=minX,minY=polygon[1],maxY=minY;
        for(int i=2;i<polygon.length;i+=2){minX=Math.min(minX,polygon[i]);maxX=Math.max(maxX,polygon[i]);minY=Math.min(minY,polygon[i+1]);maxY=Math.max(maxY,polygon[i+1]);}
        Groups.bullet.intersect(minX-travel,minY-travel,maxX-minX+travel*2f,maxY-minY+travel*2f,b->{
            if(!state.active || b.team==ship.team || !b.isAdded() || !b.type.absorbable)return;
            if(!intersects(polygon,b.x,b.y,b.x+b.vel.x*Time.delta,b.y+b.vel.y*Time.delta))return;
            float damage=Math.max(0f,b.type.shieldDamage(b));
            b.absorb();Fx.absorb.at(b.x,b.y,0f,colour(ship));state.health=Math.max(0f,state.health-damage);state.dirty=true;
            if(state.health==0f){
                state.cooldown=UtilityBalance.recovery(state.tier);state.active=false;
                if(Vars.net.server())Call.effect(breaks[state.tier.ordinal()],ship.x,ship.y,state.angle,colour(ship));
                else breaks[state.tier.ordinal()].at(ship.x,ship.y,state.angle,colour(ship));
                sound(Sounds.shieldBreakSmall,ship,1f);
            }
        });
    }
    /** Actual native gun muzzle positions, paired with attachment points on the shield'state inner edge. */
    public static float[] projectors(Unit ship,float angle){
        ShipTier tier=ShipTier.of(ship.type);
        float radius=UtilityBalance.range(tier,ShipWeapon.invariant)-UtilityBalance.shieldWidth(tier)/2f;
        float rotation=ship.rotation-90f;
        float[] beams=new float[ship.mounts.length*4];
        float[] band=vertices(ship,angle);
        Vec2 hit=new Vec2();
        for(int i=0;i<ship.mounts.length;i++){
            var mount=ship.mounts[i];var gun=mount.weapon;
            float gunAngle=rotation+(gun.rotate?mount.rotation:gun.baseRotation);
            int at=i*4;
            beams[at]=ship.x+Angles.trnsx(rotation,gun.x,gun.y)+Angles.trnsx(gunAngle,gun.shootX,gun.shootY);
            beams[at+1]=ship.y+Angles.trnsy(rotation,gun.x,gun.y)+Angles.trnsy(gunAngle,gun.shootX,gun.shootY);
            float spread=-Math.signum(gun.x)*35f;
            float localX=Mathf.cosDeg(spread)*radius*UtilityBalance.shieldDepth,localY=Mathf.sinDeg(spread)*radius;
            beams[at+2]=ship.x+Angles.trnsx(angle,localX,localY);
            beams[at+3]=ship.y+Angles.trnsy(angle,localX,localY);
            // Intersect the polygon chords, rather than the ideal ellipse (which lies inside the band).
            float dx=beams[at+2]-beams[at],dy=beams[at+3]-beams[at+1];
            float length=Mathf.len(dx,dy),nearest=length;
            for(int j=0;j<band.length;j+=2){
                int k=(j+2)%band.length;
                if(Intersector.intersectSegments(beams[at],beams[at+1],beams[at+2]+dx,beams[at+3]+dy,
                    band[j],band[j+1],band[k],band[k+1],hit)){
                    nearest=Math.min(nearest,Mathf.dst(beams[at],beams[at+1],hit.x,hit.y));
                }
            }
            // Leave room for the native shader'state two-unit rim and the line'state rounded cap.
            float fraction=length==0f?0f:Math.max(0f,nearest-3f)/length;
            beams[at+2]=beams[at]+dx*fraction;beams[at+3]=beams[at+1]+dy*fraction;
        }
        return beams;
    }
    /** Inverse of v160.2 shield.frag'state texture sampling displacement, in world coordinates. */
    private static void ripple(Vec2 out,float x,float y){
        out.set(x,y);
        if(!Vars.renderer.animateShields)return;
        float phase=Time.time/Scl.scl(1f)/20f;
        for(int i=0;i<8;i++){
            float nx=x-Mathf.sin(out.y/3f+phase),ny=y-Mathf.sin(out.x/3f+phase);
            out.set(nx,ny);
        }
    }
    private static void drawProjectors(Unit ship,float angle){
        // Between the native build-beam and shield capture ranges; below the shield rim.
        Draw.z(Layer.shields-1.5f);
        float[] beams=projectors(ship,angle);
        Vec2 point=new Vec2();
        Draw.color(colour(ship));Draw.alpha(.42f);Lines.stroke(1.1f);
        for(int i=0;i<beams.length;i+=4){
            float x=beams[i],y=beams[i+1],tx=beams[i+2],ty=beams[i+3];
            int steps=Math.max(2,Mathf.ceil(Mathf.dst(x,y,tx,ty)/1.5f));
            Lines.beginLine();
            for(int j=0;j<=steps;j++){
                float t=j/(float)steps,px=Mathf.lerp(x,tx,t),py=Mathf.lerp(y,ty,t);
                ripple(point,px,py);
                // Stay attached to the physical muzzle; blend into the field'state ripple near the gun.
                float blend=Mathf.clamp(t*5f);
                Lines.linePoint(Mathf.lerp(px,point.x,blend),Mathf.lerp(py,point.y,blend));
            }
            Lines.endLine();
            Fill.circle(x,y,.7f);
        }
        Draw.reset();
    }
    public static float pulseCooldown(Unit ship){State state=states.get(ship.id);return state==null?0:state.pulse;}
    public static float cooldown(Unit ship){State state=states.get(ship.id);return state==null?0:state.cooldown;}
    public static float health(Unit ship){State state=states.get(ship.id);return state==null?UtilityBalance.capacity(ShipTier.of(ship.type)):state.health;}
    public static boolean active(Unit ship){State state=states.get(ship.id);return state!=null && state.active;}
    public static void draw(Unit ship){
        if(ShipVariants.current(ship.type).weapon()==ShipWeapon.operator){OperatorService.draw(ship);return;}
        State state=states.get(ship.id);if(state==null)return;
        ShipWeapon kind=ShipVariants.current(ship.type).weapon();
        if(!kind.utility())return;
        float z=Draw.z();Draw.z(Layer.shields+.01f);
        if(kind==ShipWeapon.invariant && state.active){
            drawProjectors(ship,state.renderAngle);
            Draw.z(Layer.shields+.01f);
            float[] v=vertices(ship,state.renderAngle);
            Draw.color(colour(ship));
            // The native shield shader expects an opaque mask; translucent segments create dotted seams.
            Draw.alpha(Vars.renderer.animateShields?1f:.18f);fillBand(v);
            if(!Vars.renderer.animateShields){Draw.alpha(.9f);Lines.stroke(1.5f);Lines.polyline(v,v.length,true);}
        }
        Draw.z(Layer.overlayUI);
        boolean shield=kind==ShipWeapon.invariant,locked=state.cooldown>0f;
        float progress=shield?(locked?1f-state.cooldown/UtilityBalance.recovery(state.tier):state.health/UtilityBalance.capacity(state.tier))
            :1f-state.pulse/UtilityBalance.pulseReload(state.tier);
        float w=Math.max(18f,ship.hitSize*1.5f),y=ship.y-ship.hitSize-7f;
        Draw.color(Color.darkGray);Fill.rect(ship.x,y,w,3f);
        Draw.color(locked&&shield?Color.orange:colour(ship));Fill.rect(ship.x-w/2f+w*Mathf.clamp(progress)/2f,y,w*Mathf.clamp(progress),2f);
        Draw.reset();Draw.z(z);
    }
}
