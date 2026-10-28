package shipyard.content;

import arc.graphics.g2d.*;
import arc.graphics.Color;
import mindustry.ai.types.FlyingAI;
import mindustry.content.Fx;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.UnitType;
import shipyard.model.ShipTier;
import shipyard.service.EigenService;
import shipyard.service.EigenMotion;

/** One fixed native drone ID, with three unregistered stat views. No per-player content registration. */
public final class EigenContent{
    private static DroneType drone;
    private static final DroneType[] views=new DroneType[3];
    private EigenContent(){}
    public static void load(){drone=new DroneType();}
    public static boolean isDrone(Unit unit){return unit.type instanceof DroneType;}
    public static UnitType type(ShipTier tier){
        int index=tier.ordinal();
        if(views[index]==null){
            try{views[index]=drone.clone();}
            catch(CloneNotSupportedException e){throw new AssertionError(e);}
            views[index].health=new float[]{20f,55f,130f}[index];
        }
        return views[index];
    }
    public static void bind(Unit unit,ShipTier tier){unit.type=type(tier);unit.maxHealth=unit.type.health;unit.health=unit.maxHealth;}
    private static final class DroneAI extends FlyingAI{
        final Color trailColour=new Color(Color.white);
        @Override public void updateUnit(){} // Movement is owned by EigenService.
        @Override public void removed(Unit unit){
            EigenMotion.removed(unit);
            if(unit.trail!=null && unit.trail.size()>0){
                Fx.trailFade.at(unit.x,unit.y,1.6f,trailColour,unit.trail.copy());
                unit.trail=null; // Suppress the generic team-coloured fade.
            }
        }
    }
    private static final class DroneType extends UnitType implements Cloneable{
        DroneType(){
            super("eigen-drone");constructor=UnitEntity::create;
            controller=u->new DroneAI();
            flying=true;physics=false;speed=4.8f;accel=.35f;drag=.03f;health=20f;hitSize=5f;
            targetable=hittable=killable=false;useUnitCap=false;isEnemy=false;playerControllable=logicControllable=controlSelectGlobal=false;
            allowedInPayloads=pickupUnits=false;createWreck=false;hidden=true;
            itemCapacity=0;engineSize=0f;engineOffset=0f;trailLength=14;crashDamageMultiplier=0f;deathExplosionEffect=Fx.none;deathSound=Sounds.explosionPlasmaSmall;
        }
        @Override public DroneType clone()throws CloneNotSupportedException{return (DroneType)super.clone();}
        @Override public void draw(Unit unit){
            if(mindustry.Vars.player!=null && unit.inFogTo(mindustry.Vars.player.team()))return;
            float z=Draw.z();Draw.z(Layer.flyingUnitLow);
            Color color=EigenService.colour(unit);
            if(unit.controller() instanceof DroneAI ai)ai.trailColour.set(color);
            EigenMotion.Pose pose=EigenMotion.pose(unit);
            float x=pose==null?unit.x:pose.x,y=pose==null?unit.y:pose.y;
            Trail trail;
            if(pose!=null){pose.colour.set(color);pose.drawn=true;trail=pose.trail;}
            else{if(unit.trail==null)unit.trail=new Trail(trailLength);trail=unit.trail;}
            trail.draw(color,1.6f);trail.draw(Color.white,.55f);
            Draw.color(color);Draw.alpha(.16f);Fill.circle(x,y,4f);
            Draw.alpha(1f);Fill.circle(x,y,1.7f);
            Draw.color(Color.white);Fill.circle(x,y,.85f);
            Drawf.light(x,y,15f,color,.5f);
            Draw.reset();Draw.z(z);
        }
    }
}
