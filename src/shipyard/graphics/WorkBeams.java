package shipyard.graphics;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.core.Renderer;
import mindustry.world.blocks.ConstructBlock;

/** v160.2 native work-beam geometry with a private colour; never modifies Pal or deconstruction. */
public final class WorkBeams{
    private WorkBeams(){}
    public static void build(Unit unit,BuildPlan plan){
        var tile=Vars.world.tile(plan.x,plan.y);
        if(tile==null || !unit.within(plan,Vars.state.rules.infiniteResources?Float.MAX_VALUE:unit.type.buildRange))return;
        float offset=unit.type.buildBeamOffset+Mathf.absin(Time.time,3f,.6f);
        float x=unit.x+Angles.trnsx(unit.rotation,offset),y=unit.y+Angles.trnsy(unit.rotation,offset);
        // Invoke the original implementation, including its cached fading plan, for demolition.
        if(plan.breaking){unit.drawBuildingBeam(x,y);return;}
        int size=plan.block.size;
        Lines.stroke(1f,unit.type.engineColor);Draw.z(122f);Draw.alpha(unit.buildAlpha);
        if(!unit.activelyBuilding() && !(tile.build instanceof ConstructBlock.ConstructBuild))
            Fill.square(plan.drawx(),plan.drawy(),size*Vars.tilesize/2f);
        Drawf.buildBeam(x,y,plan.drawx(),plan.drawy(),size*Vars.tilesize/2f);
        Fill.square(x,y,1.8f+Mathf.absin(Time.time,2.2f,1.1f),unit.rotation+45f);
        Draw.reset();Draw.z(115f);
    }
    public static void mine(Unit unit,float x,float y,Color colour){
        if(!unit.mining())return;
        float tx=unit.mineTile.worldx()+Mathf.sin(Time.time+48f,12f,1f);
        float ty=unit.mineTile.worldy()+Mathf.sin(Time.time+48f,14f,1f);
        Draw.z(115.1f);Draw.color(colour,Color.white,.12f+Mathf.absin(Time.time,.5f,.18f));
        Draw.alpha(Renderer.unitLaserOpacity);
        Drawf.laser(unit.type.mineLaserRegion,unit.type.mineLaserEndRegion,x,y,tx,ty,.75f);
        if(unit.isLocal()){
            Lines.stroke(1f,colour);
            Lines.poly(unit.mineTile.worldx(),unit.mineTile.worldy(),4,4f*Mathf.sqrt2,Time.time);
        }
        Draw.reset();
    }
}
