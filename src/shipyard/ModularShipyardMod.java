package shipyard;

import mindustry.mod.Mod;
import shipyard.content.ShipyardBlocks;
import shipyard.content.ShipVariants;
import shipyard.service.LoadoutService;
import shipyard.service.UnitLoadouts;
import shipyard.service.ProjectileColours;
import arc.Events;
import mindustry.game.EventType.ContentInitEvent;

public class ModularShipyardMod extends Mod{
    @Override
    public void init(){
        UnitLoadouts.init();
        shipyard.content.ShipWeapons.init();
        ProjectileColours.init();
        shipyard.service.WeaponMotion.init();
        shipyard.service.VectorReload.init();
        LoadoutService.init();
        shipyard.service.CosmeticPreferences.init();
        shipyard.ui.CosmeticsDialog.init();
        shipyard.service.ShipyardSessions.init();
        shipyard.service.PresetService.init();
        shipyard.service.ProfileSync.init();
        shipyard.service.UtilityService.init();
        shipyard.service.OperatorService.init();
        shipyard.service.CausticExposure.init();
        shipyard.service.EigenService.init();
        shipyard.service.EigenMotion.init();
    }

    @Override
    public void loadContent(){
        ShipyardBlocks.load();
        ShipVariants.load();
        ProjectileColours.load();
        shipyard.content.ShipWeapons.load();
        Events.on(ContentInitEvent.class, event -> ShipVariants.addLegacyAliases());
    }
}
