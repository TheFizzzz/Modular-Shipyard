package shipyard.graphics;

import arc.math.*;
import arc.math.geom.*;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.core.World;
import mindustry.gen.*;

/** Shared rendering/contact geometry. Every strand terminates at its first collidable enemy hit. */
public final class CausticBeam{
    public static final int strands=3,segments=24;
    public record Trace(float[] points,int count,Healthc target){}
    private CausticBeam(){}
    public static float[] points(Bullet bullet,int strand){return points(bullet,strand,bullet.aimX,bullet.aimY);}
    private static float[] points(Bullet bullet,int strand,float aimX,float aimY){
        float[] path=new float[(segments+1)*2];
        float dx=aimX-bullet.x,dy=aimY-bullet.y,length=Mathf.len(dx,dy);
        float nx=length==0f?0f:-dy/length,ny=length==0f?0f:dx/length;
        float spread=Math.min(1f,length/32f);
        // World time is supplied on late join; application-local Time.time has a different origin per client.
        float phase=(float)(Vars.state.tick%(Math.PI*18d))/9f;
        for(int i=0;i<=segments;i++){
            float t=i/(float)segments,envelope=i==0 || i==segments?0f:Mathf.sin(t*Mathf.pi);
            float wave=Mathf.sin(t*Mathf.pi*3f-phase+strand*Mathf.pi*2f/3f)*2.2f;
            float offset=((strand-1)*4.5f+wave)*envelope*spread;
            path[i*2]=Mathf.lerp(bullet.x,aimX,t)+nx*offset;
            path[i*2+1]=Mathf.lerp(bullet.y,aimY,t)+ny*offset;
        }
        return path;
    }
    private static final class Cast{
        Healthc target;float x,y,distance;
        final Rect bounds=new Rect();
        void consider(Healthc candidate,float sx,float sy,float ex,float ey){
            if(candidate instanceof Unit unit)unit.hitbox(bounds);
            else if(candidate instanceof Building building)building.hitbox(bounds);
            bounds.grow(3f);
            if(bounds.contains(sx,sy)){
                if(distance>0f){target=candidate;x=sx;y=sy;distance=0f;}
                return;
            }
            Vec2 point=Geometry.raycastRect(sx,sy,ex,ey,bounds);
            if(point==null)return;
            float dst=Mathf.dst2(sx,sy,point.x,point.y);
            if(dst<distance){target=candidate;x=point.x;y=point.y;distance=dst;}
        }
    }
    public static Trace trace(Bullet bullet,int strand){return trace(bullet,strand,bullet.aimX,bullet.aimY);}
    public static Trace trace(Bullet bullet,int strand,float aimX,float aimY){
        float[] path=points(bullet,strand,aimX,aimY);Cast cast=new Cast();Rect query=new Rect();
        for(int i=2;i<path.length;i+=2){
            float x=path[i-2],y=path[i-1],tx=path[i],ty=path[i+1];
            cast.target=null;cast.distance=Float.POSITIVE_INFINITY;
            // TargetDummy's base deliberately rejects bullet collisions; its tethered unit takes the hit.
            World.raycastEachWorld(x,y,tx,ty,(cx,cy)->{
                Building building=Vars.world.build(cx,cy);
                if(building==null || building.team==bullet.team || !building.collide(bullet))return false;
                cast.consider(building,x,y,tx,ty);return cast.target!=null;
            });
            query.set(Math.min(x,tx),Math.min(y,ty),Math.abs(tx-x),Math.abs(ty-y)).grow(3f);
            Units.nearbyEnemies(bullet.team,query,unit->{
                if(unit.checkTarget(bullet.type.collidesAir,bullet.type.collidesGround) && unit.targetable(bullet.team))
                    cast.consider(unit,x,y,tx,ty);
            });
            if(cast.target==null)continue;
            path[i]=cast.x;path[i+1]=cast.y;
            return new Trace(path,i/2+1,cast.target);
        }
        return new Trace(path,path.length/2,null);
    }
}
