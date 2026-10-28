package shipyard.content;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Intersector;
import arc.util.Time;
import arc.struct.FloatSeq;
import mindustry.content.Fx;
import mindustry.Vars;
import arc.audio.Sound;
import mindustry.entities.bullet.*;
import mindustry.gen.*;

/** One appended projectile ID. Each blade owns its flight state and collision flags. */
final class InvolutionWeapons{
    private static Blade blade;
    private InvolutionWeapons(){}
    static void load(){blade=new Blade();}
    static BulletType template(){return blade;}
    static BulletType painted(Color color){
        try{Blade b=blade.copyBlade();b.hitColor=color.cpy();b.trailColor=b.lightColor=b.hitColor;return b;}
        catch(CloneNotSupportedException e){throw new AssertionError(e);}
    }
    private static final class Flight{
        final Unit owner;final int ownerId;
        final FloatSeq path=new FloatSeq(128);
        int phase,index;float travel;
        boolean steerable,returnBoosted;
        Flight(Unit owner,float x,float y){this.owner=owner;ownerId=owner.id;record(x,y);}
        void record(float x,float y){
            if(path.size==0 || Mathf.dst2(path.items[path.size-2],path.items[path.size-1],x,y)>.0001f)path.add(x,y);
        }
    }
    private static final class Blade extends BasicBulletType{
        public Sound returnSound=Sounds.shootSap;
        Blade(){
            super(5f,60f);lifetime=140f;rangeOverride=198.72f;keepVelocity=false;
            hitSize=8f;drawSize=460f;pierce=pierceBuilding=true;
            pierceCap=2;removeAfterPierce=false;pierceDamageFactor=0f;
            reflectable=false;trailLength=0;
            shootEffect=Fx.shootSmallColor;smokeEffect=Fx.none;
            hitEffect=Fx.hitBulletColor;despawnEffect=Fx.none;
        }
        Blade copyBlade()throws CloneNotSupportedException{return (Blade)clone();}
        @Override public void init(Bullet b){
            super.init(b);
            if(!(b.owner instanceof Unit owner)){b.remove();return;}
            try{b.type=copyBlade();}catch(CloneNotSupportedException e){throw new AssertionError(e);}
            Flight flight=new Flight(owner,b.x,b.y);b.data=flight;
            // Native mounts supply a launch aim; scripted bullets without one keep their straight fallback.
            flight.steerable=Float.isFinite(b.aimX) && Float.isFinite(b.aimY) && !(b.aimX==-1f && b.aimY==-1f);
        }
        private void pauseCollisions(Bullet b){
            if(b.collided.size>=2){b.type.collides=false;b.type.collidesTiles=false;}
        }
        @Override public boolean testCollision(Bullet b,Building building){
            return b.collided.size<2 && super.testCollision(b,building);
        }
        @Override public void hitEntity(Bullet b,Hitboxc entity,float health){
            if(b.collided.size<=2)super.hitEntity(b,entity,health);
            pauseCollisions(b);
        }
        @Override public void hitTile(Bullet b,Building building,float x,float y,float health,boolean direct){
            super.hitTile(b,building,x,y,health,direct);pauseCollisions(b);
        }
        @Override public void update(Bullet b){
            super.update(b);
            if(!(b.data instanceof Flight flight) || !flight.owner.isAdded() || flight.owner.dead
                || flight.owner.id!=flight.ownerId || flight.owner.team!=b.team){b.remove();return;}
            if(flight.phase==0){
                // Include the preceding post-collision position as well as this movement endpoint.
                flight.record(b.lastX,b.lastY);flight.record(b.x,b.y);
                flight.travel+=Mathf.dst(b.lastX,b.lastY,b.x,b.y);
                // The travel cap prevents cursor steering from orbiting forever inside the range circle.
                if(b.dst(b.originX,b.originY)<198.72f && flight.travel<238.464f){
                    if(flight.steerable){
                        Unit owner=flight.owner;
                        if(Float.isFinite(owner.aimX) && Float.isFinite(owner.aimY) && b.dst(owner.aimX,owner.aimY)>2f){
                            b.vel.setAngle(Angles.moveToward(b.rotation(),b.angleTo(owner.aimX,owner.aimY),8f*Time.delta));
                        }
                    }
                    return;
                }
                flight.phase=1;flight.index=flight.path.size-4;
                // Only authority announces the transition; remote clients receive one native positional cue.
                if(!Vars.net.client()){
                    if(Vars.net.server())Call.soundAt(returnSound,b.x,b.y,1f,1.25f);
                    else returnSound.at(b.x,b.y,1.25f,1f);
                }
                b.collided.clear();b.type.collides=b.type.collidesTiles=true;
                b.vel.setZero();
                return;
            }
            if(flight.phase==1){
                // Boost only once return movement actually begins, not on the last outward collision step.
                if(!flight.returnBoosted){b.damage*=1.25f;flight.returnBoosted=true;}
                // Native velocity is zero during retracing; spend movement exactly along stored segments.
                float remaining=6f*Time.delta;
                while(flight.index>=0 && remaining>0f){
                    float x=flight.path.items[flight.index],y=flight.path.items[flight.index+1];
                    float distance=b.dst(x,y);
                    if(distance<=remaining+.0001f){
                        b.set(x,y);remaining=Math.max(0f,remaining-distance);flight.index-=2;
                    }else{
                        float fraction=remaining/distance;
                        b.set(Mathf.lerp(b.x,x,fraction),Mathf.lerp(b.y,y,fraction));remaining=0f;
                    }
                }
                if(flight.index>=0)return;
                flight.phase=2;flight.path.clear();
                b.vel.trns(b.angleTo(flight.owner),6f);
            }
            Unit owner=flight.owner;
            float catchRadius=owner.hitSize*.5f+5f;
            if(Intersector.distanceSegmentPoint(b.lastX,b.lastY,b.x,b.y,owner.x,owner.y)<=catchRadius){b.remove();return;}
            // Final catch has no turn-radius orbit or arbitrary timeout. Owner validation above still cleans up orphans.
            b.keepAlive=true;
            float speed=Math.min(Math.max(18f,owner.vel.len()+6f),b.vel.len()+.45f*Time.delta);
            b.vel.trns(b.angleTo(owner),speed);
        }

        /** Render only the unconsumed portion of the very same path used by return movement. */
        private void drawRoute(Bullet b,Flight flight){
            int count=flight.phase==0?flight.path.size:flight.phase==1?flight.index+2:0;
            if(count<2)return;
            int points=count/2+(flight.phase==1?1:0);
            if(points<2)return;
            // A soft energy sheath and two flowing filaments, all clipped to the remaining route.
            for(int pass=0;pass<3;pass++){
                Draw.color(hitColor);Draw.alpha(pass==0?.1f:.55f);Lines.stroke(pass==0?3.5f:.65f);
                Lines.beginLine();float distance=0f,previousX=0f,previousY=0f;
                for(int point=0;point<points;point++){
                    boolean tip=point==count/2;
                    float x=tip?b.x:flight.path.items[point*2],y=tip?b.y:flight.path.items[point*2+1];
                    if(point>0)distance+=Mathf.dst(previousX,previousY,x,y);
                    float nextX=point+1<count/2?flight.path.items[(point+1)*2]:b.x;
                    float nextY=point+1<count/2?flight.path.items[(point+1)*2+1]:b.y;
                    float angle=point==points-1?Angles.angle(previousX,previousY,x,y):Angles.angle(x,y,nextX,nextY);
                    float taper=Math.min(1f,Math.min(point,points-1-point)*.5f);
                    float wave=pass==0?0f:(pass==1?1f:-1f)*Mathf.sin(distance*.24f-Time.time*.16f)*1.25f*taper;
                    Lines.linePoint(x+Angles.trnsx(angle+90f,wave),y+Angles.trnsy(angle+90f,wave));
                    previousX=x;previousY=y;
                }
                Lines.endLine();
            }
            Draw.reset();Lines.stroke(1f);
        }
        @Override public void draw(Bullet b){
            if(b.data instanceof Flight flight)drawRoute(b,flight);
            boolean returning=b.data instanceof Flight flight && flight.phase>0;
            float spin=b.time*(returning?-16f:16f)+b.id%360;
            Draw.color(hitColor);Draw.alpha(.22f);Fill.circle(b.x,b.y,7f);
            Draw.alpha(1f);Lines.stroke(2f);Lines.arc(b.x,b.y,5.5f,.7f,spin);
            mindustry.graphics.Drawf.tri(b.x+Angles.trnsx(spin,5f),b.y+Angles.trnsy(spin,5f),3f,6f,spin+90f);
            Draw.color(Color.white);Lines.stroke(.7f);Lines.arc(b.x,b.y,5.5f,.55f,spin+15f);
            Draw.reset();Lines.stroke(1f);
        }
    }
}
