package shipyard;

import java.lang.reflect.*;
import java.util.Map;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.world.Block;
import mindustry.world.meta.Env;

final class PresetChecks{
    static void run(Block block)throws Exception{
        Vars.logic.reset();Vars.state.rules.env=Env.terrestrial;Vars.state.rules.possessionAllowed=true;
        Vars.world.loadGenerator(48,48,tiles->{tiles.fill();tiles.eachTile(t->t.setFloor(Blocks.stone.asFloor()));
            tiles.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);tiles.getn(24,24).setBlock(block,Team.sharded,0);tiles.getn(26,24).setBlock(Blocks.powerSource,Team.sharded,0);});
        Building yard=Vars.world.build(24,24);yard.power.graph.reflow(yard);yard.power.graph.update();
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var service=loader.loadClass("shipyard.service.LoadoutService");var library=loader.loadClass("shipyard.service.PresetService");
        var model=loader.loadClass("shipyard.model.ShipLoadout");var colours=loader.loadClass("shipyard.model.ShipCosmetics");
        var kinds=loader.loadClass("shipyard.model.ShipWeapon");var tiers=loader.loadClass("shipyard.model.ShipTier");
        var apply=service.getMethod("apply",Player.class,int.class,int.class,model,colours,String.class);
        var catalog=library.getMethod("catalog",Player.class,tiers);
        Object alphaTier=tiers.getEnumConstants()[0],betaTier=tiers.getEnumConstants()[1];
        Object neutral=model.getField("defaults").get(null),nativeColours=colours.getField("defaults").get(null);
        Object a=model.getConstructor(int.class,int.class,int.class,int.class,int.class).newInstance(1,-2,-1,1,1);
        a=model.getMethod("withWeapon",kinds).invoke(a,kinds.getEnumConstants()[5]);
        Object b=model.getMethod("withSpeed",int.class).invoke(a,0);b=model.getMethod("withWeapon",kinds).invoke(b,kinds.getEnumConstants()[6]);
        Object pink=colours.getMethod("parse",String.class).invoke(null,"F7E2E1,FF4488,30EE70");
        Object blue=colours.getMethod("parse",String.class).invoke(null,"224466,55CCFF,AA66FF");
        String aliceId="preset-alice-"+System.nanoTime(),bobId="preset-bob-"+System.nanoTime();
        Player alice=LoadoutChecks.player(aliceId);alice.add();Unit alpha=ship(alice,UnitTypes.alpha,yard);
        check(((String)DockTest.apply(apply,alice,yard.pos(),alpha.id,a,pink,"Builder")).contains("applied"),"named Apply failed");
        Object cat=catalog.invoke(null,alice,alphaTier);check(selected(cat).equals("Builder"),"selected name missing");snapshot(cat,"Builder",a,pink);
        check(((String)DockTest.apply(apply,alice,yard.pos(),alpha.id,b,blue,"Builder")).contains("applied"),"named overwrite failed");
        snapshot(catalog.invoke(null,alice,alphaTier),"Builder",b,blue);
        check(((String)DockTest.apply(apply,alice,yard.pos(),alpha.id,neutral,nativeColours,"Miner")).contains("applied"),"second preset failed");
        Object excessive=model.getConstructor(int.class,int.class,int.class,int.class,int.class).newInstance(5,5,5,5,5);
        check(!((String)DockTest.apply(apply,alice,yard.pos(),alpha.id,excessive,pink,"Builder")).contains("applied"),"invalid preset applied");
        snapshot(catalog.invoke(null,alice,alphaTier),"Builder",b,blue);
        for(String bad:new String[]{"","a".repeat(33),"[red]bad","bad,name","bad\nname"}){
            try{library.getMethod("name",String.class).invoke(null,bad);throw new AssertionError("invalid name accepted");}catch(InvocationTargetException expected){}
        }
        for(int i=0;i<10;i++)check(((String)DockTest.apply(apply,alice,yard.pos(),alpha.id,neutral,nativeColours,"P"+i)).contains("applied"),"preset quota filled too early");
        check(((String)DockTest.apply(apply,alice,yard.pos(),alpha.id,neutral,nativeColours,"Extra")).contains("12"),"preset quota not enforced");
        mindustry.input.InputHandler.buildingControlSelect(alice,yard);
        String deleted=(String)library.getMethod("delete",Player.class,yard.getClass(),String.class).invoke(null,alice,yard,"P0");
        yard.getClass().getMethod("release",Player.class).invoke(yard,alice);check(deleted.contains("deleted"),"delete failed");
        check(!entries(catalog.invoke(null,alice,alphaTier)).containsKey("P0"),"deleted preset remained");
        // Failed writes must leave both the active configuration and the named snapshot intact.
        var file=service.getDeclaredField("file");file.setAccessible(true);Object previous=file.get(null);
        var obstruction=Vars.dataDirectory.child("preset-write-obstruction");obstruction.writeString("not a directory");
        try{
            file.set(null,obstruction.child("profiles.properties"));
            check(((String)DockTest.apply(apply,alice,yard.pos(),alpha.id,a,pink,"Builder")).contains("Could not save"),"save failure was ignored");
            snapshot(catalog.invoke(null,alice,alphaTier),"Builder",b,blue);
        }finally{file.set(null,previous);obstruction.delete();}
        Unit beta=ship(alice,UnitTypes.beta,yard);
        check(((String)DockTest.apply(apply,alice,yard.pos(),beta.id,a,pink,"Builder")).contains("applied"),"per-tier preset failed");
        snapshot(catalog.invoke(null,alice,betaTier),"Builder",a,pink);snapshot(catalog.invoke(null,alice,alphaTier),"Builder",b,blue);
        Player bob=LoadoutChecks.player(bobId);bob.add();Unit other=ship(bob,UnitTypes.alpha,yard);
        check(entries(catalog.invoke(null,bob,alphaTier)).isEmpty(),"other player's presets leaked");
        check(((String)DockTest.apply(apply,bob,yard.pos(),other.id,a,pink,"Builder")).contains("applied"),"independent same-name preset failed");
        snapshot(catalog.invoke(null,alice,alphaTier),"Builder",b,blue);
        alice.unit(alpha);alpha.set(yard);check(((String)DockTest.apply(apply,alice,yard.pos(),alpha.id,b,blue,"Builder")).contains("applied"),"reselect failed");
        service.getMethod("init").invoke(null);
        cat=catalog.invoke(null,alice,alphaTier);check(selected(cat).equals("Builder"),"disk reload lost selected preset");snapshot(cat,"Builder",b,blue);
        Player reconnect=LoadoutChecks.player(aliceId);Unit restored=ship(reconnect,UnitTypes.alpha,yard);service.getMethod("restore",Player.class).invoke(null,reconnect);
        var variants=loader.loadClass("shipyard.content.ShipVariants");
        check(variants.getMethod("current",mindustry.type.UnitType.class).invoke(null,restored.type).equals(b),"reconnect lost selected stats/weapon");
        check(variants.getMethod("cosmetics",mindustry.type.UnitType.class).invoke(null,restored.type).equals(blue),"reconnect lost selected colours");
        // A listen server has a local player without a remote connection. Its UI still needs a catalogue.
        var publish=library.getDeclaredMethod("publish",Player.class,tiers);publish.setAccessible(true);
        var active=Vars.net.getClass().getDeclaredField("active");var server=Vars.net.getClass().getDeclaredField("server");active.setAccessible(true);server.setAccessible(true);
        boolean wasActive=active.getBoolean(Vars.net),wasServer=server.getBoolean(Vars.net),wasHeadless=Vars.headless;Player previousPlayer=Vars.player;
        try{
            active.setBoolean(Vars.net,true);server.setBoolean(Vars.net,true);Vars.headless=false;Vars.player=alice;
            publish.invoke(null,alice,alphaTier);Object hostCatalog=library.getMethod("local",tiers).invoke(null,alphaTier);
            check(hostCatalog!=null && selected(hostCatalog).equals("Builder"),"listen host catalogue stayed waiting");
            snapshot(hostCatalog,"Builder",b,blue);
            int revision=library.getField("revision").getInt(null);
            publish.invoke(null,bob,alphaTier);
            check(library.getField("revision").getInt(null)==revision,"remote publication overwrote host UI cache");
            Vars.headless=true;publish.invoke(null,alice,alphaTier);
            check(library.getField("revision").getInt(null)==revision,"dedicated server populated a local UI cache");
        }finally{Vars.player=previousPlayer;Vars.headless=wasHeadless;active.setBoolean(Vars.net,wasActive);server.setBoolean(Vars.net,wasServer);}
        System.out.println("PASS: listen-host catalogue without a connection, remote privacy and dedicated-server UI isolation");
        System.out.println("PASS: full named snapshots/overwrite, selected-name persistence, player/tier isolation, bounds/quota/delete, failed-write rollback and reconnect restoration");
    }
    private static Unit ship(Player player,mindustry.type.UnitType type,Building yard){Unit unit=type.create(Team.sharded);unit.spawnedByCore=true;unit.set(yard.x,yard.y-25);unit.add();player.unit(unit);return unit;}
    private static Map<?,?> entries(Object cat)throws Exception{return (Map<?,?>)cat.getClass().getMethod("presets").invoke(cat);}
    private static String selected(Object cat)throws Exception{return (String)cat.getClass().getMethod("selected").invoke(cat);}
    private static void snapshot(Object cat,String name,Object loadout,Object colours)throws Exception{
        Object saved=entries(cat).get(name);check(saved!=null,"missing preset "+name);
        check(saved.getClass().getMethod("loadout").invoke(saved).equals(loadout),"preset stats/weapon differ");
        check(saved.getClass().getMethod("colours").invoke(saved).equals(colours),"preset colours differ");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
