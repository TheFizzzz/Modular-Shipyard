package shipyard.model;

/** Initial utility values; time is in native 60-Hz ticks, range in world units. */
public final class UtilityBalance{
    private UtilityBalance(){}
    public static float range(ShipTier tier,ShipWeapon weapon){
        if(weapon==ShipWeapon.operator)return pick(tier,96f,128f,160f);
        return weapon==ShipWeapon.divergence ? pick(tier,60f,80f,100f) : pick(tier,32f,40f,48f);
    }
    public static float operatorForce(ShipTier tier){return pick(tier,125f,250f,437.5f);}
    public static final float shieldArc=200f;
    public static final float shieldDepth=.8f;
    public static final int shieldSegments=24;
    public static float shieldWidth(ShipTier tier){return pick(tier,6f,7f,8f);}
    public static float force(ShipTier tier){return pick(tier,2500f,4500f,7000f);}
    public static float pulseReload(ShipTier tier){return 150f;}
    public static float capacity(ShipTier tier){return pick(tier,120f,360f,900f);}
    public static float recovery(ShipTier tier){return 600f;}
    public static float regen(ShipTier tier){return pick(tier,.5f,1.25f,2.5f);}
    private static float pick(ShipTier tier,float alpha,float beta,float gamma){
        return switch(tier){case alpha->alpha;case beta->beta;case gamma->gamma;};
    }
}
