package shipyard.model;

/** First TTK-guided tier curves. Factors scale direct and splash damage, never shared definitions. */
public final class WeaponBalance{
    private WeaponBalance(){}
    public static float damageFactor(ShipTier tier,ShipWeapon weapon){
        return switch(weapon){
            case standard -> 1f;
            case eigen -> pick(tier,.35f,.8f,2f);
            case divergence,invariant,operator -> 0f;
            case flamethrower -> pick(tier,.25f,.85f,2.5f);
            case caustic -> pick(tier,.3f,.9f,2.8f);
            case superfactorial -> pick(tier,.25f,.75f,2f);
            case asymptote -> pick(tier,.25f,.85f,2.5f);
            case lancer -> pick(tier,.25f,.65f,2f);
            case railgun -> pick(tier,.15f,.65f,2f);
            case fuse -> pick(tier,.18f,.65f,1.6f);
            case chain,missiles -> pick(tier,.5f,1.1f,3f);
        };
    }
    private static float pick(ShipTier tier,float alpha,float beta,float gamma){
        return switch(tier){case alpha->alpha;case beta->beta;case gamma->gamma;};
    }
}
