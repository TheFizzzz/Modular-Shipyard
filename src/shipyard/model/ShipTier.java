package shipyard.model;

import mindustry.content.UnitTypes;
import mindustry.type.UnitType;
import shipyard.type.ShipVariant;

/** Identity checks intentionally exclude Erekir and modded ships. */
public enum ShipTier{
    alpha(6), beta(10), gamma(14);

    public final int budget;

    ShipTier(int budget){
        this.budget = budget;
    }

    public UnitType base(){
        return switch(this){
            case alpha -> UnitTypes.alpha;
            case beta -> UnitTypes.beta;
            case gamma -> UnitTypes.gamma;
        };
    }

    public static ShipTier of(UnitType type){
        if(type instanceof ShipVariant variant) return variant.tier;
        if(type == UnitTypes.alpha) return alpha;
        if(type == UnitTypes.beta) return beta;
        if(type == UnitTypes.gamma) return gamma;
        return null;
    }
}
