package shipyard.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.*;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.game.EventType.BlockBuildEndEvent;
import shipyard.type.ShipVariant;
import mindustry.graphics.Drawf;
import mindustry.graphics.MultiPacker;
import mindustry.core.Renderer;
import mindustry.world.blocks.ConstructBlock;

/** v160.2 native work-beam geometry with a private colour; never modifies Pal or deconstruction. */
public final class WorkBeams{
    private static final Effect completion=new Effect(Fx.placeBlock.lifetime,Fx.placeBlock.clip,e->{
        Draw.color(e.color);Lines.stroke(3f-e.fin()*2f);
        Lines.square(e.x,e.y,4f*e.rotation+e.fin()*3f);
        EngineHighlights.draw(e.color,.55f);Lines.stroke((3f-e.fin()*2f)*.38f);
        Lines.square(e.x,e.y,4f*e.rotation+e.fin()*3f);
        Draw.reset();Lines.stroke(1f);
    }).layer(Fx.placeBlock.layer).followParent(false);
    private WorkBeams(){}
    public static void init(){Events.on(BlockBuildEndEvent.class,WorkBeams::completed);}
    /** ConstructBlock fires this event after creating the local native placement effect. */
    private static void completed(BlockBuildEndEvent event){
        if(!Fx.placeBlock.shouldCreate() || event.breaking || event.unit==null || event.tile==null
            || !(event.unit.type instanceof ShipVariant variant) || variant.cosmetics().engine()==-1
            || event.tile.block().placeEffect!=Fx.placeBlock)return;
        EffectState newest=null;
        for(EffectState effect:Groups.effect){
            if(effect.effect==Fx.placeBlock && effect.time==0f
                && Mathf.equal(effect.x,event.tile.drawx(),.01f) && Mathf.equal(effect.y,event.tile.drawy(),.01f)
                && effect.rotation==event.tile.block().size && (newest==null || effect.id>newest.id))newest=effect;
        }
        // Reuse exactly the effect native visibility/settings allowed; never add a second flash.
        if(newest!=null){newest.effect=completion;newest.color.set(event.unit.type.engineColor);}
    }
    /** Neutral copies remove the gold baked into vanilla mining textures, preserving their alpha. */
    public static void pack(MultiPacker packer){
        for(String source:new String[]{"minelaser","minelaser-end"}){
            String name="modular-shipyard-"+source;
            if(packer.has(MultiPacker.PageType.main,name))continue;
            Pixmap pixels=Core.atlas.getPixmap(source).crop();
            try{
                for(int y=0;y<pixels.height;y++)for(int x=0;x<pixels.width;x++){
                    int rgba=pixels.getRaw(x,y);
                    int shade=Math.round((rgba>>>24)*.2126f+((rgba>>>16)&255)*.7152f+((rgba>>>8)&255)*.0722f);
                    pixels.setRaw(x,y,(shade<<24)|(shade<<16)|(shade<<8)|(rgba&255));
                }
                packer.add(MultiPacker.PageType.main,name,pixels);
            }finally{pixels.dispose();}
        }
    }
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
        Draw.z(115.1f);EngineHighlights.draw(colour,.12f+Mathf.absin(Time.time,.5f,.18f));
        Draw.alpha(Renderer.unitLaserOpacity);
        // The shorter overload always adds an orange light, independently of Draw.color.
        var beam=Core.atlas.find("modular-shipyard-minelaser");
        var end=Core.atlas.find("modular-shipyard-minelaser-end");
        Drawf.laser(beam,end,end,x,y,tx,ty,.75f,false);
        if(EngineHighlights.mix(colour,0f)>0f){
            EngineHighlights.draw(colour,.65f);Draw.alpha(Renderer.unitLaserOpacity*.8f);
            Drawf.laser(beam,end,end,x,y,tx,ty,.24f,false);
        }
        Drawf.light(x,y,tx,ty,30f,unit.type instanceof ShipVariant variant?variant.workLight():colour,.3f*Renderer.unitLaserOpacity);
        if(unit.isLocal()){
            Lines.stroke(1f,colour);
            Lines.poly(unit.mineTile.worldx(),unit.mineTile.worldy(),4,4f*Mathf.sqrt2,Time.time);
        }
        Draw.reset();
    }
}
