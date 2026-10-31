package shipyard.ui;

import arc.Core;
import mindustry.content.Planets;
import mindustry.content.UnitTypes;
import mindustry.type.UnitType;
import shipyard.api.CosmeticFamily;
import shipyard.content.ShipVariants;
import shipyard.model.*;
import shipyard.service.CosmeticPreferences;

final class SerpuloCosmetics implements CosmeticFamily{
    @Override public String id(){return "serpulo";}
    @Override public String name(){return Planets.serpulo.localizedName;}
    @Override public String hint(){return Core.bundle.get("shipyard.defaultcolourshint");}
    @Override public UnitType[] ships(){return new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma};}
    @Override public ShipCosmetics saved(){return CosmeticPreferences.saved();}
    @Override public void save(ShipCosmetics colours){CosmeticPreferences.save(colours);}
    @Override public UnitType preview(UnitType ship,ShipCosmetics colours){
        return ShipVariants.get(ShipTier.of(ship),ShipLoadout.defaults,colours);
    }
}
