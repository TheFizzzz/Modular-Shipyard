package shipyard.content;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.Time;
import mindustry.content.Fx;
import arc.struct.IntSet;
import mindustry.Vars;
import shipyard.graphics.CausticBeam;
import shipyard.graphics.WeaponVisuals;
import shipyard.service.CausticExposure;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import shipyard.model.ShipWeapon;

/** Two appended templates; per-player palette/tier clones and per-shot state never register content. */
final class ExperimentalWeapons{
    static final float focusRange=124.8f;
    private static Caustic caustic;
    private static Asymptote asymptote;
    private ExperimentalWeapons(){}
    static void load(){caustic=new Caustic();asymptote=new Asymptote();}
    static BulletType template(ShipWeapon kind){return kind==ShipWeapon.caustic?caustic:asymptote;}
    static BulletType painted(ShipWeapon kind,Color color){
        try{
            BulletType b=kind==ShipWeapon.caustic?caustic.paletteClone():asymptote.paletteClone();
            b.hitColor=color.cpy();b.trailColor=b.lightColor=b.hitColor;
            return b;
        }catch(CloneNotSupportedException impossible){throw new AssertionError(impossible);}
    }

    static void finish(Bullet bullet,float x,float y){
        if(Vars.headless || !shipyard.graphics.CausticVisuals.finish.shouldCreate()
            || bullet.owner instanceof Unit owner && owner.inFogTo(Vars.player.team()) || !(bullet.type instanceof Caustic) || !(bullet.data instanceof Caustic.Frame frame) || frame.finished)return;
        frame.finished=true;
        var finalTraces=new CausticBeam.Trace[CausticBeam.strands];
        for(int i=0;i<finalTraces.length;i++)finalTraces[i]=CausticBeam.trace(bullet,i,x,y);
        var visible=frame.visible[0]==null?frame.traces:frame.visible;
        shipyard.graphics.CausticVisuals.finish.at(bullet.x,bullet.y,bullet.rotation(),bullet.type.hitColor,
            shipyard.graphics.CausticVisuals.snapshot(visible,finalTraces,x,y));
    }

    private static final class Caustic extends ContinuousBulletType{
        private Caustic penetrator;
        private static final class Frame{
            final CausticBeam.Trace[] traces=new CausticBeam.Trace[CausticBeam.strands];
            final IntSet damaged=new IntSet();
            final CausticBeam.Trace[] visible=new CausticBeam.Trace[CausticBeam.strands];
            boolean finished;
        }
        Caustic(){
            damage=3f;damageInterval=6f;length=focusRange+8f;rangeOverride=focusRange;
            lifetime=12f;pierceCap=1;laserAbsorb=true;largeHit=false;
            drawSize=300f;hitEffect=Fx.hitLaserColor;shootEffect=smokeEffect=despawnEffect=Fx.none;
        }
        Caustic paletteClone()throws CloneNotSupportedException{Caustic copy=(Caustic)clone();copy.penetrator=null;return copy;}
        @Override protected float calculateRange(){return focusRange;}
        @Override public float currentLength(Bullet b){return Math.min(length,b.dst(b.aimX,b.aimY));}
        @Override public void update(Bullet b){
            if(!(b.owner instanceof Unit owner) || !owner.isAdded() || !shipyard.service.WeaponMotion.shooting(owner)){
                b.remove();return;
            }
            boolean attached=false;
            for(var mount:owner.mounts)if(mount.bullet==b && mount.weapon.bullet==this){attached=true;break;}
            if(!attached){b.remove();return;}
            Frame frame=b.data instanceof Frame cached?cached:new Frame();b.data=frame;
            for(int i=0;i<frame.traces.length;i++){
                frame.traces[i]=CausticBeam.trace(b,i);
                if(frame.traces[i].target() instanceof Unit target)CausticExposure.touch(owner,target);
            }
            // Continuous guns do not reload between ticks; preserve the Fire Rate/Construction trade-off.
            float rate=Math.max(.05f,owner.reloadMultiplier);
            if(b.timer(1,damageInterval/rate))applyDamage(b);
        }
        @Override public void applyDamage(Bullet b){
            if(Vars.net.client() || !(b.data instanceof Frame frame))return;
            frame.damaged.clear();float original=b.damage;
            for(var trace:frame.traces){
                Healthc target=trace.target();
                if(target==null || !frame.damaged.add(target.id()))continue;
                try{
                    if(target instanceof Unit unit){
                        Caustic hit=this;
                        if(CausticExposure.armorMultiplier((Unit)b.owner,unit)<1f){
                            if(penetrator==null)try{
                                penetrator=paletteClone();penetrator.armorMultiplier=armorMultiplier*.5f;
                            }catch(CloneNotSupportedException impossible){throw new AssertionError(impossible);}
                            hit=penetrator;
                        }
                        // A cached unregistered adapter changes this hit's native armour calculation,
                        // without mutating the live projectile type, target armour or other players.
                        hit.hitEntity(b,unit,unit.health);
                    }
                    else if(target instanceof Building building)building.collision(b);
                }finally{b.damage=original;}
            }
        }
        @Override public void draw(Bullet b){
            if(!(b.data instanceof Frame frame))return;
            var aim=b.owner instanceof Unit owner?shipyard.service.WeaponMotion.visual(owner):null;
            float aimX=b.aimX,aimY=b.aimY;
            if(aim!=null && b.owner instanceof Unit owner){
                float angle=Angles.angle(owner.x,owner.y,aim.x,aim.y);
                float distance=Mathf.clamp(Mathf.dst(owner.x,owner.y,aim.x,aim.y),16f,focusRange);
                aimX=owner.x+Angles.trnsx(angle,distance);aimY=owner.y+Angles.trnsy(angle,distance);
            }
            for(int strand=0;strand<frame.traces.length;strand++){
                // Render with the smoothly changing length, including on a listen host. Never
                // replace the authoritative traces that applyDamage consumes.
                var trace=aim==null || !Vars.net.server()?frame.traces[strand]:CausticBeam.trace(b,strand,aimX,aimY);
                frame.visible[strand]=trace;
                if(trace==null)continue;
                float[] path=trace.points();
                int end=(trace.count()-1)*2;
                // A resized dummy can envelop the muzzle. Do not submit a zero-length polyline.
                if(Mathf.dst2(path[0],path[1],path[end],path[end+1])<.0001f){
                    Draw.color(hitColor);Draw.alpha(.6f);Fill.circle(path[0],path[1],.8f);continue;
                }
                for(int pass=0;pass<2;pass++){
                    Draw.color(hitColor);Draw.alpha(pass==0?.16f:.65f);Lines.stroke(pass==0?2.4f:.65f);
                    Lines.beginLine();
                    for(int i=0;i<trace.count();i++)Lines.linePoint(path[i*2],path[i*2+1]);
                    Lines.endLine();
                }
                Draw.color(hitColor);Draw.alpha(.6f);Fill.circle(path[end],path[end+1],.8f);
            }
            if(shipyard.graphics.CausticVisuals.converges(frame.visible,aimX,aimY))
                shipyard.graphics.CausticVisuals.focus(aimX,aimY,hitColor,(float)Vars.state.tick,1f);
            WeaponVisuals.causticMuzzle(b);Draw.reset();Lines.stroke(1f);
        }
    }

    private static final class Asymptote extends BulletType{
        private static final float initialSpeed=.25f,topSpeed=12f,launchTime=18f,rampTime=12f,chargeDistance=200f,reach=240f;
        Asymptote(){
            super(initialSpeed,10f);lifetime=75f;keepVelocity=false;
            splashDamage=64f;splashDamageRadius=32f;despawnHit=true;
            hitSize=4f;drawSize=90f;trailLength=9;trailWidth=.85f;
            shootEffect=Fx.shootSmallColor;smokeEffect=Fx.none;
            hitEffect=WeaponVisuals.asymptoteBlast;despawnEffect=Fx.none;
            hitSound=Sounds.explosionPlasmaSmall;hitSoundVolume=1f;
        }
        @Override protected float calculateRange(){
            return reach;
        }
        Asymptote paletteClone()throws CloneNotSupportedException{return (Asymptote)clone();}
        private float charge(Bullet b){return Mathf.clamp(b.dst(b.originX,b.originY)/chargeDistance);}
        @Override public void init(Bullet b){super.init(b);b.fdata=b.damage;b.damage*=.2f;}
        @Override public void update(Bullet b){
            super.update(b);
            float phase=Mathf.clamp((b.time-launchTime)/rampTime);
            float speed=Mathf.lerp(initialSpeed,topSpeed,phase*phase*(3f-2f*phase));
            // Bound physical travel as well as the picker range, including the final partial step.
            float travelled=b.dst(b.originX,b.originY);
            if(travelled>reach){
                float fraction=reach/travelled;
                b.set(b.originX+(b.x-b.originX)*fraction,b.originY+(b.y-b.originY)*fraction);
            }
            float remaining=Math.max(0f,reach-travelled);
            b.vel.setLength(Math.min(speed,remaining/Math.max(Time.delta,.001f)));
            if(remaining<.01f)b.lifetime=Math.min(b.lifetime,b.time+Time.delta*2f);
            b.damage=b.fdata*(.2f+.8f*charge(b));
        }
        @Override public void despawned(Bullet b){if(!b.absorbed)super.despawned(b);}
        @Override public void createSplashDamage(Bullet b,float x,float y){
            if(b.absorbed || damage<=0f)return;
            // Direct damage already snapshots the launch multiplier and distance charge.
            // Derive splash from that snapshot instead of rereading the owner's current status.
            mindustry.entities.Damage.damage(b.team,x,y,splashDamageRadius,splashDamage*b.damage/damage,
                splashDamagePierce,collidesAir,collidesGround,scaledSplashDamage,b,armorMultiplier);
        }
        @Override public void draw(Bullet b){
            drawTrail(b);WeaponVisuals.asymptote(b,charge(b));
        }
    }
}
