package shipyard.api;

import mindustry.type.UnitType;
import shipyard.model.ShipCosmetics;

/** Addon entry point for the shared cosmetics menu. Each family owns its saved preferences. */
public interface CosmeticFamily{
    String id();
    String name();
    String hint();
    UnitType[] ships();
    ShipCosmetics saved();
    void save(ShipCosmetics colours);
    /** Return a private preview type; never change the supplied vanilla type or register content. */
    UnitType preview(UnitType ship,ShipCosmetics colours);
    /** Optional non-colour draft key for in-world addon stat/equipment previews. */
    default Object previewKey(){return null;}
}
