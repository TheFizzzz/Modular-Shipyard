package shipyard;

import java.io.*;
import java.lang.reflect.*;
import java.util.Base64;
import java.util.Properties;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.world.Block;

/** Malformed input is recoverable; internal bugs must not masquerade as invalid input or I/O. */
final class ErrorBoundaryChecks{
    static void run(Block block)throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var loadouts=loader.loadClass("shipyard.service.LoadoutService");
        var decodeApply=method(loadouts,"decodeRequest",String.class);
        for(String malformed:new String[]{"","a,b","0,1,no,0,0,0,0,-,-,-,standard,-","0,1,0,0,0,0,0,BAD,-,-,standard,-"})
            check(decodeApply.invoke(null,malformed)==null,"malformed Apply fields accepted");
        check(decodeApply.invoke(null,"0,1,0,0,0,0,0,-,-,-,standard,-")!=null,"valid Apply fields rejected");
        var cosmetics=loader.loadClass("shipyard.model.ShipCosmetics");var validHex=cosmetics.getMethod("validHex",String.class);
        for(String hex:new String[]{"F7E2E1","#ffffff","000000"})check((boolean)validHex.invoke(null,hex),"valid HEX rejected");
        for(String hex:new String[]{null,"","F7E","GGGGGG","#12345678"})check(!(boolean)validHex.invoke(null,hex),"incomplete HEX accepted");
        var decodeDock=method(loader.loadClass("shipyard.service.ShipyardSessions"),"decode",String.class);
        expectCause(decodeDock,Base64.getEncoder().encodeToString(new byte[3]),IOException.class);
        expectCause(decodeDock,dockPayload(false),IOException.class);
        expectCause(decodeDock,dockPayload(true),RuntimeException.class);
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.possessionAllowed=true;
        Vars.world.loadGenerator(48,48,tiles->{tiles.fill();tiles.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));
            tiles.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);tiles.getn(24,24).setBlock(block,Team.sharded,0);tiles.getn(26,24).setBlock(Blocks.powerSource,Team.sharded,0);});
        Building yard=Vars.world.build(24,24);yard.power.graph.reflow(yard);yard.power.graph.update();
        Player player=LoadoutChecks.player("error-boundary-player");player.add();
        Unit ship=UnitTypes.alpha.create(Team.sharded);ship.spawnedByCore=true;ship.set(yard.x,yard.y-20);ship.add();player.unit(ship);
        var model=loader.loadClass("shipyard.model.ShipLoadout");Object neutral=model.getField("defaults").get(null),colours=cosmetics.getField("defaults").get(null);
        var apply=loadouts.getMethod("apply",Player.class,int.class,int.class,model,cosmetics,String.class);
        check(((String)DockTest.apply(apply,player,yard.pos(),ship.id,neutral,colours,"Safe")).contains("applied"),"fixture failed to save");
        mindustry.input.InputHandler.buildingControlSelect(player,yard);
        var profilesField=loadouts.getDeclaredField("profiles");profilesField.setAccessible(true);Properties profiles=(Properties)profilesField.get(null);
        Properties savedProfiles=(Properties)profiles.clone();var fileField=loadouts.getDeclaredField("file");fileField.setAccessible(true);Object savedFile=fileField.get(null);
        var obstruction=Vars.dataDirectory.child("cleanup-io-obstruction");obstruction.writeString("not a directory");
        try{
            fileField.set(null,obstruction.child("profiles.properties"));
            var presets=loader.loadClass("shipyard.service.PresetService");
            String response=(String)presets.getMethod("delete",Player.class,yard.getClass(),String.class).invoke(null,player,yard,"Safe");
            check(response.contains("Could not delete") && profiles.equals(savedProfiles),"failed deletion did not roll back");
            fileField.set(null,null); // A broken internal invariant, not an ordinary filesystem failure.
            try{apply.invoke(null,player,yard.pos(),ship.id,neutral,colours,"Safe");throw new AssertionError("internal bug was converted into a storage response");}
            catch(InvocationTargetException failure){check(failure.getCause() instanceof NullPointerException,"unexpected internal error type");}
        }finally{
            fileField.set(null,savedFile);profiles.clear();profiles.putAll(savedProfiles);obstruction.delete();
            yard.getClass().getMethod("release",Player.class).invoke(yard,player);ship.remove();player.remove();
        }
        System.out.println("PASS: typed invalid Apply/HEX/dock decoding, native truncated-IO unwrapping, visible native entity-map errors, failed-delete rollback and unexpected Apply error propagation");
    }
    static String dockPayload(boolean unknownEntity)throws IOException{
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(0);out.writeInt(0);out.writeLong(1);out.writeBoolean(false);out.writeBoolean(false);out.writeFloat(0);out.writeFloat(0);
        if(unknownEntity){out.writeInt(1);out.writeUTF("0,0,0,0,0");out.writeUTF("-,-,-");out.writeBoolean(true);out.writeByte(0);out.writeByte(127);}
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    static Method method(Class<?> type,String name,Class<?>... parameters)throws Exception{var method=type.getDeclaredMethod(name,parameters);method.setAccessible(true);return method;}
    static void expectCause(Method decoder,String payload,Class<? extends Throwable> expected)throws Exception{
        try{decoder.invoke(null,payload);throw new AssertionError("bad dock payload accepted");}
        catch(InvocationTargetException failure){check(expected.isInstance(failure.getCause()),"wrong decoding error: "+failure.getCause());}
    }
    static void check(boolean passed,String reason){if(!passed)throw new AssertionError(reason);}
}
