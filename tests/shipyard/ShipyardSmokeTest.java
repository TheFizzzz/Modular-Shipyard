package shipyard;

import arc.ApplicationListener;
import arc.Core;
import arc.backend.headless.HeadlessApplication;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.content.UnitTypes;
import mindustry.core.Platform;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.input.InputHandler;
import mindustry.net.Net;
import mindustry.ui.Fonts;
import mindustry.core.UI;
import mindustry.world.meta.Env;
import mindustry.gen.Building;
import mindustry.world.Block;

/** Runs real v160.2 content, world placement and native control-selection code, without GL. */
public class ShipyardSmokeTest{
    public static void main(String[] args){
        Vars.platform = new Platform(){};
        Vars.net = new Net(Vars.platform.getNet());
        new HeadlessApplication(new ApplicationListener(){
            @Override
            public void init(){
                try{
                    runChecks();
                    System.out.println("PASS: content, placement, tiers, native control, ownership, lifecycle, environment, grid connection and power loss");
                    System.exit(0);
                }catch(Throwable failure){
                    failure.printStackTrace();
                    System.exit(1);
                }
            }
        });
    }

    private static void runChecks() throws Exception{
        Vars.headless = true;
        Vars.loadLocales = false;
        Core.settings.setDataDirectory(Core.files.local("config"));
        Vars.loadSettings();
        Vars.init();
        UI.loadColors();
        Fonts.loadContentIconsHeadless();
        Vars.content.createBaseContent();
        Vars.content.createModContent();
        Vars.content.init();
        check(!Vars.mods.hasContentErrors(), "mod content errors");
        Block block = Vars.content.block("modular-shipyard-shipyard");
        check(block != null, "mod jar did not load");
        check(block.size == 3, "wrong size");
        check(block.hasPower && block.update && block.consPower != null, "power consumer missing");
        check(Math.abs(block.consPower.usage - 1f) < 0.0001f, "unexpected power demand");
        check(!block.configurable, "configuration access still enabled");
        check(block.supportsEnv(Planets.serpulo.defaultEnv), "Serpulo disabled");
        check(!block.supportsEnv(Planets.erekir.defaultEnv), "Erekir enabled");
        // Use the actual mod classloader, which intentionally loads its own classes first.
        Class<?> tiers = Vars.mods.getMod("modular-shipyard").loader.loadClass("shipyard.model.ShipTier");
        var detect = tiers.getMethod("of", mindustry.type.UnitType.class);
        var budget = tiers.getField("budget");
        check(budget.getInt(detect.invoke(null, UnitTypes.alpha)) == 6, "Alpha budget");
        check(budget.getInt(detect.invoke(null, UnitTypes.beta)) == 10, "Beta budget");
        check(budget.getInt(detect.invoke(null, UnitTypes.gamma)) == 14, "Gamma budget");
        check(detect.invoke(null, UnitTypes.evoke) == null, "Erekir ship accepted");

        Vars.world.loadGenerator(24, 24, tiles -> {
            tiles.fill();
            tiles.eachTile(tile -> tile.setFloor(Blocks.stone.asFloor()));
        });
        Vars.state.rules.env = Env.terrestrial;
        Vars.state.rules.possessionAllowed = true;
        Vars.world.tile(12, 12).setBlock(block, Team.sharded, 0);
        Building yard = Vars.world.build(12, 12);
        check(yard != null, "building entity missing");
        int footprint = 0;
        for(var tile : Vars.world.tiles){
            if(tile.build == yard) footprint++;
        }
        check(footprint == 9, "not a 3x3 footprint");
        Player player = Player.create();
        player.team(Team.sharded);
        for(var type : new mindustry.type.UnitType[]{UnitTypes.alpha, UnitTypes.beta, UnitTypes.gamma}){
            Unit ship = type.create(Team.sharded);
            ship.set(yard.x, yard.y);
            ship.spawnedByCore = true;
            ship.add();
            player.unit(ship);
            yard.power.graph.reflow(yard);
            yard.power.graph.update();
            check(!yard.canControlSelect(ship), "isolated yard accepted ship");
            // Sandbox cheats can set status to one; a real grid connection is still required.
            yard.power.status = 1f;
            check(!yard.canControlSelect(ship), "isolated yard bypassed connection requirement");
            Vars.world.tile(14, 12).setBlock(Blocks.powerSource, Team.sharded, 0);
            Building source = Vars.world.build(14, 12);
            yard.power.graph.reflow(yard);
            yard.power.graph.update();
            check(yard.power.graph == source.power.graph, "source not connected");
            check(yard.canControlSelect(ship), "powered supported ship rejected");
            source.enabled = false;
            yard.power.graph.update();
            check(!yard.canControlSelect(ship), "blackout accepted ship");
            source.enabled = true;
            yard.power.graph.update();
            check(yard.canControlSelect(ship), "power recovery failed");
            yard.power.status = 0.5f;
            check(!yard.canControlSelect(ship), "brownout accepted ship");
            yard.power.graph.update();
            InputHandler.buildingControlSelect(player, yard);
            check(player.unit() instanceof mindustry.gen.BlockUnitc && !ship.isAdded() && !ship.dead,
                "ship was not safely absorbed during possession");
            yard.getClass().getMethod("release",Player.class).invoke(yard,player);
            check(player.unit()==ship && ship.isValid(),"possession did not return original ship");
            yard.buildConfiguration(new arc.scene.ui.layout.Table());
            yard.enabled = false;
            check(!yard.canControlSelect(ship), "disabled yard accepted ship");
            yard.enabled = true;
            Vars.state.rules.env = Planets.erekir.defaultEnv;
            check(!yard.canControlSelect(ship), "Erekir environment accepted");
            Vars.state.rules.env = Env.terrestrial;
            yard.changeTeam(Team.crux);
            check(!yard.canControlSelect(ship), "enemy yard accepted ship");
            yard.changeTeam(Team.sharded);
            ship.dead = true;
            check(!yard.canControlSelect(ship), "dead ship accepted");
            ship.remove();
            Vars.world.tile(14, 12).remove();
        }
        Unit unsupported = UnitTypes.evoke.create(Team.sharded);
        unsupported.set(yard);
        unsupported.spawnedByCore = true;
        unsupported.add();
        player.unit(unsupported);
        check(!yard.canControlSelect(unsupported), "unsupported controlled ship accepted");
        Unit ai = UnitTypes.alpha.create(Team.sharded);
        ai.set(yard);
        ai.spawnedByCore = true;
        ai.add();
        check(!yard.canControlSelect(ai), "AI accepted");
        check(!yard.canControlSelect(null), "null ship accepted");
        player.unit(ai);
        Vars.world.tile(14, 12).setBlock(Blocks.powerSource, Team.sharded, 0);
        yard.power.graph.reflow(yard);
        yard.power.graph.update();
        check(yard.canControlSelect(ai), "valid ship rejected before removal");
        yard.tile.remove();
        check(!yard.canControlSelect(ai), "removed yard accepted ship");
        LoadoutChecks.run(block);
        ConstructionChecks.run(block);
        FeedbackChecks.run();
        MiningChecks.run(block);
        DurabilityChecks.run(block);
        ColourChecks.run(block);
        PaintChecks.run(block);
        WeaponChecks.run(block);
        SessionChecks.run(block);
        PresetChecks.run(block);
        ErrorBoundaryChecks.run(block);
        PreferenceChecks.run(block);
        UtilityChecks.run(block);
        ExperimentalChecks.run(block);
        ConvergeChecks.run();
        OperatorProtectionChecks.run();
        InvolutionChecks.run();
        WeaponBudgetChecks.run(block);
        SuperfactorialChecks.run();
        CorrosionChecks.run();
        CausticPenChecks.run();
        WeaponFlareChecks.run();
        EigenChecks.run();
        EigenSwipeChecks.run();
        EigenSwarmChecks.run();
        EigenMotionChecks.run();
        CombatPresentationChecks.run();
        WeaponMotionChecks.run();
        CausticFeedbackChecks.run();
        DummyChecks.run();
        if(System.getenv("SHIPYARD_MOVING_COMBAT")!=null)MovingCombatChecks.run();
        if(System.getenv("SHIPYARD_PAIRS")!=null)WeaponPairChecks.run();
        if(System.getenv("SHIPYARD_DODGE")!=null)DodgePressureChecks.run();
        if(System.getenv("SHIPYARD_TTK")!=null)TtkChecks.run();
        if(System.getenv("SHIPYARD_PACK_TTK")!=null)PackTtkChecks.run();
    }

    private static void check(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }
}
