package shipyard.graphics;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Vec2;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

/** Cosmetic beam finish snapshots contain no live bullets, owners or damage callbacks. */
public final class CausticVisuals{
    private CausticVisuals(){}
    public static void load(){} // Register effect IDs consistently during content load, including headless hosts.
    public record Finish(CausticBeam.Trace[] from,CausticBeam.Trace[] to,float x,float y,boolean focused){}
    public static final Effect ignition=new Effect(12f,70f,e->{
        EngineHighlights.draw(e.color);Draw.alpha(.3f*e.fout());Fill.circle(e.x,e.y,3f+e.fin()*5f);
        Draw.alpha(e.fout());Lines.stroke(1.2f*e.fout());Lines.circle(e.x,e.y,1f+e.fin()*6f);
        for(int side=-1;side<=1;side+=2)Drawf.tri(e.x,e.y,2.2f*e.fout(),8f*e.fout(),e.rotation+side*25f);
        EngineHighlights.draw(e.color,.8f);Fill.circle(e.x,e.y,2.2f*e.fout());
        Drawf.light(e.x,e.y,22f,e.color,.5f*e.fout());Draw.reset();Lines.stroke(1f);
    }).followParent(true);
    public static final Effect finish=new Effect(14f,320f,e->{
        if(!(e.data instanceof Finish snapshot))return;
        float travel=Mathf.clamp(e.time/10f),tail=e.fin();
        if(tail>=.999f)return;
        float strength=1f-Mathf.clamp((e.time-8f)/6f);
        Vec2 oldPoint=arc.util.Tmp.v1,newPoint=arc.util.Tmp.v2;
        for(int strand=0;strand<snapshot.from.length;strand++){
            var from=snapshot.from[strand];var to=snapshot.to[strand];
            if(from==null || to==null || !hasLength(from) && !hasLength(to))continue;
            sample(from,tail,oldPoint);sample(to,tail,newPoint);
            float startX=Mathf.lerp(oldPoint.x,newPoint.x,travel),startY=Mathf.lerp(oldPoint.y,newPoint.y,travel);
            sample(from,1f,oldPoint);sample(to,1f,newPoint);
            // Opposite final aims can briefly collapse the interpolated path to one point.
            if(Mathf.dst2(startX,startY,Mathf.lerp(oldPoint.x,newPoint.x,travel),Mathf.lerp(oldPoint.y,newPoint.y,travel))<.0001f)continue;
            for(int pass=0;pass<2;pass++){
                EngineHighlights.draw(e.color);Draw.alpha((pass==0?.16f:.65f)*strength);
                Lines.stroke((pass==0?2.4f:.65f)*strength);Lines.beginLine();
                for(int step=0;step<=CausticBeam.segments;step++){
                    float position=Mathf.lerp(tail,1f,step/(float)CausticBeam.segments);
                    sample(from,position,oldPoint);sample(to,position,newPoint);
                    Lines.linePoint(Mathf.lerp(oldPoint.x,newPoint.x,travel),Mathf.lerp(oldPoint.y,newPoint.y,travel));
                }
                Lines.endLine();
            }
        }
        if(snapshot.focused){
            sample(snapshot.from[1],1f,oldPoint);
            focus(Mathf.lerp(oldPoint.x,snapshot.x,travel),Mathf.lerp(oldPoint.y,snapshot.y,travel),e.color,e.time*2f,strength);
        }
        Draw.reset();Lines.stroke(1f);
    }).followParent(false);
    private static boolean hasLength(CausticBeam.Trace trace){
        float[] path=trace.points();int end=(trace.count()-1)*2;
        return trace.count()>1 && Mathf.dst2(path[0],path[1],path[end],path[end+1])>.0001f;
    }
    private static void sample(CausticBeam.Trace trace,float progress,Vec2 point){
        float position=Mathf.clamp(progress)*(trace.count()-1);
        int index=Math.min((int)position,trace.count()-2);float mix=position-index;float[] path=trace.points();
        point.set(Mathf.lerp(path[index*2],path[index*2+2],mix),Mathf.lerp(path[index*2+1],path[index*2+3],mix));
    }
    public static Finish snapshot(CausticBeam.Trace[] from,CausticBeam.Trace[] to,float x,float y){
        return new Finish(copy(from),copy(to),x,y,converges(to,x,y));
    }
    private static CausticBeam.Trace[] copy(CausticBeam.Trace[] traces){
        var copied=new CausticBeam.Trace[traces.length];
        for(int i=0;i<traces.length;i++)if(traces[i]!=null){
            var trace=traces[i];
            copied[i]=new CausticBeam.Trace(java.util.Arrays.copyOf(trace.points(),trace.count()*2),trace.count(),null);
        }
        return copied;
    }
    public static boolean converges(CausticBeam.Trace[] traces,float x,float y){
        if(traces.length!=CausticBeam.strands)return false;
        for(var trace:traces){
            if(trace==null || trace.count()<2)return false;
            int end=(trace.count()-1)*2;float[] path=trace.points();
            if(Mathf.dst2(path[end],path[end+1],x,y)>4f)return false;
        }
        return true;
    }
    public static void focus(float x,float y,Color colour,float clock,float strength){
        EngineHighlights.draw(colour);Draw.alpha(.2f*strength);Fill.circle(x,y,4.8f);
        Draw.alpha(.8f*strength);Lines.stroke(.65f*strength);
        Lines.arc(x,y,2.7f,.65f,clock*5f);
        for(int i=0;i<3;i++){
            float angle=clock*-4f+i*120f;
            Fill.circle(x+Angles.trnsx(angle,2.8f),y+Angles.trnsy(angle,2.8f),.55f*strength);
        }
        EngineHighlights.draw(colour,.65f);Draw.alpha(strength);
        Fill.poly(x,y,4,1.35f*strength,clock*3f);
        Drawf.light(x,y,14f,colour,.3f*strength);Draw.reset();Lines.stroke(1f);
    }
}
