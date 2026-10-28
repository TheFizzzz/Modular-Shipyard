package shipyard.content;

import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.type.UnitType;
import shipyard.model.ShipLoadout;
import shipyard.model.ShipCosmetics;
import shipyard.model.ShipTier;
import shipyard.type.ShipVariant;

/** Three registered hulls; private views and old-save aliases never enter the content registry. */
public final class ShipVariants{
    private static final ShipVariant[] hulls = new ShipVariant[3];

    private ShipVariants(){}

    public static void load(){
        for(ShipTier tier : ShipTier.values()) hulls[tier.ordinal()] = new ShipVariant(tier);
    }

    public static UnitType get(ShipTier tier, ShipLoadout loadout){
        return get(tier, loadout, ShipCosmetics.defaults);
    }

    public static UnitType get(ShipTier tier, ShipLoadout loadout, ShipCosmetics cosmetics){
        if(!loadout.valid(tier) || !cosmetics.valid()) throw new IllegalArgumentException("Invalid ship loadout");
        return loadout.equals(ShipLoadout.defaults) && cosmetics.equals(ShipCosmetics.defaults) ? tier.base()
            : hulls[tier.ordinal()].view(loadout, cosmetics, false);
    }

    public static ShipCosmetics cosmetics(UnitType type){
        return type instanceof ShipVariant variant ? variant.cosmetics() : ShipCosmetics.defaults;
    }

    public static ShipLoadout current(UnitType type){
        return type instanceof ShipVariant variant ? variant.loadout() : ShipLoadout.defaults;
    }

    public static void addLegacyAliases(){
        // Fixed migration cost for v0.2/v0.3 saves. No new IDs, icons or content-list entries.
        for(ShipTier tier : ShipTier.values()){
            for(int speed = -4; speed <= 5; speed++){
                for(int cargo = -4; cargo <= 5; cargo++){
                    for(int construction = -4; construction <= 5; construction++){
                        String name = "modular-shipyard-ship-" + tier.name() + "-s" + (speed + 4) + "-c" + (cargo + 4)
                            + (construction == 0 ? "" : "-b" + (construction + 4));
                        Vars.content.getNamesBy(ContentType.unit).put(name,
                            hulls[tier.ordinal()].view(new ShipLoadout(speed, cargo, construction), true));
                    }
                }
            }
        }
    }
}
