package shipyard;

import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Unit;

final class FeedbackChecks{
    static void run() throws Exception{
        var loader = Vars.mods.getMod("modular-shipyard").loader;
        var model = loader.loadClass("shipyard.model.ShipLoadout");
        var tierClass = loader.loadClass("shipyard.model.ShipTier");
        Object alpha = tierClass.getEnumConstants()[0];
        var create = model.getConstructor(int.class, int.class, int.class);
        Object reduced = create.newInstance(0, -2, 0);
        check((int)model.getMethod("refundPoints").invoke(reduced) == 2, "manual refunds missing");
        check((int)model.getMethod("remainingPoints", tierClass).invoke(reduced, alpha) == 8,
            "refunds not added above base budget");
        Object funded = create.newInstance(3, -2, 0);
        check((boolean)model.getMethod("valid", tierClass).invoke(funded, alpha), "refund-funded upgrade rejected");
        check((int)model.getMethod("remainingPoints", tierClass).invoke(funded, alpha) == 2,
            "refund-funded available balance incorrect");
        Object automatic = create.newInstance(0, 1, 1);
        check((int)model.getMethod("refundPoints").invoke(automatic) == 0,
            "automatic speed/fire-rate penalties earned refunds");
        var player = LoadoutChecks.player("feedback-test-player");
        Unit ship = UnitTypes.alpha.create(Team.sharded);
        ship.spawnedByCore = true;
        var yard = Vars.world.build(40, 40);
        ship.set(yard);
        ship.add();
        player.unit(ship);
        var apply = loader.loadClass("shipyard.service.LoadoutService")
            .getMethod("apply", mindustry.gen.Player.class, int.class, int.class, int.class, int.class, int.class);
        ship.set(yard.x + 64f, yard.y);
        check(yard.canControlSelect(ship), "8-tile boundary rejected");
        ship.set(yard.x + 64.1f, yard.y);
        check(!yard.canControlSelect(ship), "outside interaction radius accepted");
        String rejected = (String)DockTest.apply(apply, player, yard.pos(), ship.id, 0, -2, 0);
        check(rejected.contains("Possess") && ship.type == UnitTypes.alpha,
            "remote Apply bypassed range check");
        ship.set(yard);
        String result = (String)DockTest.apply(apply, player, yard.pos(), ship.id, 3, -2, 0);
        check(result.equals("Loadout applied and saved."), "server rejected refund-funded upgrade: " + result);
        check(ship.itemCapacity() == 18 && Math.abs(ship.type.speed - 3.9f) < .001f,
            "refund-funded loadout did not apply");
        check(((String)DockTest.apply(apply, player, yard.pos(), ship.id, 3, 0, 0)).contains("applied"), "Alpha 6-point boundary rejected");
        check(!((String)DockTest.apply(apply, player, yard.pos(), ship.id, 3, 1, 0)).contains("applied"), "Alpha budget exceeded");
        for(var base : new mindustry.type.UnitType[]{UnitTypes.beta,UnitTypes.gamma}){
            Unit boundary = base.create(Team.sharded);boundary.spawnedByCore=true;boundary.set(yard);boundary.add();player.unit(boundary);
            int cargo = base == UnitTypes.beta ? 0 : 2;
            check(((String)DockTest.apply(apply, player, yard.pos(), boundary.id, 5, cargo, 0)).contains("applied"), "tier boundary rejected: " + base.name);
            var accepted = boundary.type;
            check(!((String)DockTest.apply(apply, player, yard.pos(), boundary.id, 5, cargo + 1, base == UnitTypes.gamma ? -1 : 0)).contains("applied"), "over-budget boundary accepted: " + base.name);
            check(boundary.type == accepted, "rejection changed existing valid loadout");
        }
        // A previously valid 15-point Gamma profile must not restore under the new cap.
        var service = loader.loadClass("shipyard.service.LoadoutService");
        var field = service.getDeclaredField("profiles");field.setAccessible(true);
        var profiles = (java.util.Properties)field.get(null);
        profiles.setProperty("budget-old-gamma.gamma", "5,3,-1,0,0");
        var oldPlayer = LoadoutChecks.player("budget-old-gamma");
        Unit oldGamma = UnitTypes.gamma.create(Team.sharded);oldGamma.spawnedByCore=true;oldGamma.set(yard);oldGamma.add();oldPlayer.unit(oldGamma);
        service.getMethod("restore",mindustry.gen.Player.class).invoke(null,oldPlayer);
        check(oldGamma.type == UnitTypes.gamma, "old 15-point Gamma profile bypassed new budget");
        System.out.println("PASS: visible refund credit, funded upgrades, no automatic refunds, 8-tile eligibility and server distance rejection");
    }

    private static void check(boolean value, String reason){
        if(!value) throw new AssertionError(reason);
    }
}
