package shipyard;

import arc.Core;
import java.util.Properties;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.world.Block;

/** Colours are local defaults, while named/stat profiles remain private authoritative server records. */
final class PreferenceChecks{
    static void run(Block block)throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var prefs=loader.loadClass("shipyard.service.CosmeticPreferences");var sync=loader.loadClass("shipyard.service.ProfileSync");
        var service=loader.loadClass("shipyard.service.LoadoutService");var variants=loader.loadClass("shipyard.content.ShipVariants");
        var tiers=loader.loadClass("shipyard.model.ShipTier");var cosmetics=loader.loadClass("shipyard.model.ShipCosmetics");var model=loader.loadClass("shipyard.model.ShipLoadout");
        var profilesField=service.getDeclaredField("profiles");profilesField.setAccessible(true);var profiles=(Properties)profilesField.get(null);Properties before=(Properties)profiles.clone();
        var receive=prefs.getDeclaredMethod("receive",Player.class,String.class);receive.setAccessible(true);
        var reconcile=sync.getDeclaredMethod("receive",Player.class,String.class);reconcile.setAccessible(true);
        var syncClear=sync.getDeclaredMethod("clear");syncClear.setAccessible(true);
        var unitService=loader.loadClass("shipyard.service.UnitLoadouts");var epoch=unitService.getDeclaredMethod("worldEpoch");epoch.setAccessible(true);
        var cache=prefs.getDeclaredField("saved");cache.setAccessible(true);
        String setting="modular-shipyard-default-colours-v1";Object previousSetting=Core.settings.get(setting,null);
        Player previousPlayer=Vars.player;boolean previousHeadless=Vars.headless;
        Object palette=cosmetics.getMethod("parse",String.class).invoke(null,"F7E2E1,3366FF,7BDDB3");
        Object otherPalette=cosmetics.getMethod("parse",String.class).invoke(null,"123456,ABCDEF,FF3366");
        Object defaults=model.getField("defaults").get(null);
        try{
            Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.possessionAllowed=true;
            Vars.world.loadGenerator(48,48,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));t.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);t.getn(24,24).setBlock(block,Team.sharded,0);t.getn(26,24).setBlock(Blocks.powerSource,Team.sharded,0);});
            Building yard=Vars.world.build(24,24);yard.power.graph.reflow(yard);yard.power.graph.update();
            // Loading a world is earlier than native connection confirmation; never spend the
            // one colour handshake while the joining player has no replicated unit yet.
            var active=Vars.net.getClass().getDeclaredField("active");var server=Vars.net.getClass().getDeclaredField("server");active.setAccessible(true);server.setAccessible(true);
            boolean wasActive=active.getBoolean(Vars.net),wasServer=server.getBoolean(Vars.net);
            try{
                Vars.player=Player.create();Vars.headless=false;Vars.state.set(mindustry.core.GameState.State.playing);active.setBoolean(Vars.net,true);server.setBoolean(Vars.net,false);
                var send=prefs.getDeclaredMethod("update");send.setAccessible(true);send.invoke(null);
                var sent=prefs.getDeclaredField("sent");sent.setAccessible(true);check(!sent.getBoolean(null),"colour handshake sent before a replicated player ship existed");
            }finally{Vars.headless=previousHeadless;active.setBoolean(Vars.net,wasActive);server.setBoolean(Vars.net,wasServer);}
            prefs.getMethod("save",cosmetics).invoke(null,palette);cache.set(null,null);
            check(prefs.getMethod("saved").invoke(null).equals(palette),"local preference did not reload");
            Player local=Player.create();local.team(Team.sharded);Vars.player=local;
            for(var base:new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}){
                Unit ship=ship(local,base);Vars.headless=false;service.getMethod("restore",Player.class).invoke(null,local);Vars.headless=true;
                check(paint(variants,ship).equals(palette),"local default palette missing for "+base.name);
                check(current(variants,ship).equals(defaults),"default colours changed stats or weapon");ship.remove();
            }
            Player remote=LoadoutChecks.player("100000017");Unit alpha=ship(remote,UnitTypes.alpha);
            service.getMethod("restore",Player.class).invoke(null,remote);
            check(paint(variants,alpha).equals(cosmetics.getField("defaults").get(null)),"local colours leaked to remote player");
            receive.invoke(null,remote,"0,0,0,0,0,railgun");receive.invoke(null,remote,"NaN,-,-");receive.invoke(null,remote,"x".repeat(25));
            check(alpha.type==UnitTypes.alpha,"malformed handshake changed ship");
            receive.invoke(null,remote,palette.getClass().getMethod("encode").invoke(palette));
            check(paint(variants,alpha).equals(palette),"remote default colours not applied");check(current(variants,alpha).equals(defaults),"cosmetic packet changed stats");
            receive.invoke(null,remote,otherPalette.getClass().getMethod("encode").invoke(otherPalette));
            check(paint(variants,alpha).equals(palette),"duplicate handshake changed active colours");
            Object stats=model.getMethod("parse",String.class).invoke(null,"1,0,0,1,0,flamethrower");
            alpha.set(yard.x,yard.y-25);var apply=service.getMethod("apply",Player.class,int.class,int.class,model,cosmetics,String.class);
            check(((String)DockTest.apply(apply,remote,yard.pos(),alpha.id,stats,otherPalette,"Steam Test")).contains("applied"),"saved Steam-format profile failed");
            Player reconnect=LoadoutChecks.player("100000017");Unit rejoined=ship(reconnect,UnitTypes.alpha);
            Properties savedProfiles=(Properties)profiles.clone();String token=epoch.invoke(null)+","+rejoined.id;
            // Wrong identity/unit/world and payloads cannot request another player's state.
            reconcile.invoke(null,reconnect,"old-"+token);reconcile.invoke(null,reconnect,epoch.invoke(null)+","+alpha.id);
            reconcile.invoke(null,reconnect,epoch.invoke(null)+",NaN");reconcile.invoke(null,reconnect,"x".repeat(61));
            check(rejoined.type==UnitTypes.alpha,"invalid restore request mutated ship");
            rejoined.spawnedByCore=false;reconcile.invoke(null,reconnect,token);check(rejoined.type==UnitTypes.alpha,"non-core unit accepted restore handshake");rejoined.spawnedByCore=true;
            syncClear.invoke(null);reconcile.invoke(null,reconnect,token);
            check(current(variants,rejoined).equals(stats),"explicit sync failed to restore server's saved stats/weapon");
            check(paint(variants,rejoined).equals(otherPalette),"saved colours did not override defaults");
            check(profiles.equals(savedProfiles),"reconnect sync rewrote server profiles");
            float hp=rejoined.health;Object mounts=rejoined.mounts;reconcile.invoke(null,reconnect,token);
            check(rejoined.health==hp && rejoined.mounts==mounts,"duplicate restore changed health/mounts");
            Object catalog=loader.loadClass("shipyard.service.PresetService").getMethod("catalog",Player.class,tiers).invoke(null,reconnect,tiers.getEnumConstants()[0]);
            check(catalog.getClass().getMethod("selected").invoke(catalog).equals("Steam Test"),"Steam-format identity lost selected name");
            check(((java.util.Map<?,?>)catalog.getClass().getMethod("presets").invoke(catalog)).containsKey("Steam Test"),"Steam-format identity lost named snapshot");
            Unit erekir=ship(reconnect,UnitTypes.evoke);service.getMethod("restore",Player.class).invoke(null,reconnect);check(erekir.type==UnitTypes.evoke,"preferences modified Erekir ship");
            System.out.println("PASS: persistent free local default colours, Alpha/Beta/Gamma fallback, remote isolation, cosmetic-only bounded/once-per-world handshake, server profile precedence, Steam-format identity restoration, named catalogue preservation and invalid/duplicate/stale/non-core sync rejection");
        }finally{
            Vars.player=previousPlayer;Vars.headless=previousHeadless;profiles.clear();profiles.putAll(before);syncClear.invoke(null);
            if(previousSetting==null)Core.settings.remove(setting);else Core.settings.put(setting,previousSetting);
            cache.set(null,null);Core.settings.manualSave();
        }
    }
    static Unit ship(Player player,mindustry.type.UnitType type){Unit ship=type.create(Team.sharded);ship.spawnedByCore=true;ship.set(100,100);ship.add();player.unit(ship);return ship;}
    static Object current(Class<?> variants,Unit ship)throws Exception{return variants.getMethod("current",mindustry.type.UnitType.class).invoke(null,ship.type);}
    static Object paint(Class<?> variants,Unit ship)throws Exception{return variants.getMethod("cosmetics",mindustry.type.UnitType.class).invoke(null,ship.type);}
    static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
