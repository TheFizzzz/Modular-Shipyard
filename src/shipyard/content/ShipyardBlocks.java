package shipyard.content;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import shipyard.world.Shipyard;

public final class ShipyardBlocks{
    public static Shipyard shipyard;

    private ShipyardBlocks(){}

    public static void load(){
        shipyard = new Shipyard("shipyard");
        shipyard.requirements(Category.units, ItemStack.with(
            Items.copper, 200, Items.lead, 200, Items.silicon, 150, Items.titanium, 100
        ));
        // Prototype: available without research so interaction can be tested immediately.
        shipyard.alwaysUnlocked = true;
    }
}
