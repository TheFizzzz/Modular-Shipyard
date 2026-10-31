package shipyard.type;

import arc.graphics.Color;
import mindustry.entities.bullet.*;
import shipyard.model.ShipTier;

/** Three stable adapters; unregistered private clones change only native colour fields. */
public final class EngineBolt extends LaserBoltBulletType{
    public final ShipTier tier;
    public EngineBolt(ShipTier tier){ this.tier = tier; }

    public BulletType source(){ return tier.base().weapons.first().bullet; }

    private void borrow(){
        BulletType original = source();
        if(!(original instanceof LaserBoltBulletType)) throw new IllegalStateException("Unexpected Serpulo projectile type");
        NativeFields.copy(original,this,LaserBoltBulletType.class,mindustry.ctype.Content.class);
    }

    public EngineBolt painted(Color colour){
        try{
            EngineBolt paintedBolt = (EngineBolt)super.clone();
            paintedBolt.backColor = colour.cpy();
            paintedBolt.frontColor = shipyard.graphics.EngineHighlights.colour(colour,.65f);
            paintedBolt.trailColor = paintedBolt.hitColor = paintedBolt.backColor;
            paintedBolt.lightColor=shipyard.graphics.EngineHighlights.colour(colour,0f);
            return paintedBolt;
        }catch(CloneNotSupportedException impossible){ throw new AssertionError(impossible); }
    }

    @Override public void init(){ borrow(); }
    @Override public void load(){ borrow(); }
}
