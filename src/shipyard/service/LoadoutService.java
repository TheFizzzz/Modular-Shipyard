package shipyard.service;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.IntMap;
import arc.util.Log;
import java.util.Properties;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import arc.util.ArcRuntimeException;
import mindustry.Vars;
import mindustry.game.EventType.UnitChangeEvent;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.PlayerLeave;
import mindustry.world.meta.Env;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import shipyard.model.ShipLoadout;
import shipyard.model.ShipCosmetics;
import shipyard.content.ShipVariants;
import shipyard.model.ShipTier;
import shipyard.model.ShipWeapon;
import shipyard.world.Shipyard.ShipyardBuild;

/** All mutation and persistence happen on the authority; packets never supply a UUID or stats. */
public final class LoadoutService{
    private static final String packet = "modular-shipyard-apply-v6";
    private static final String reply = "modular-shipyard-result-v6";
    static final Properties profiles = new Properties();
    private static final IntMap<Long> lastRequest = new IntMap<>();
    public static String feedback = "";
    public static boolean pending;
    private static long pendingSince;
    private static Fi file;

    private LoadoutService(){}

    public static void init(){
        file = Vars.dataDirectory.child("modular-shipyard/loadouts-v1.properties");
        profiles.clear();
        if(!Vars.net.client() && file.exists()){
            try(var input = new FileInputStream(file.file())){
                profiles.load(input);
            }catch(IOException | IllegalArgumentException failure){
                profiles.clear(); // Malformed Properties may have populated entries before failing.
                Log.err("Could not read Shipyard loadouts", failure);
            }
        }
        if(Vars.netServer != null){
            Vars.netServer.addPacketHandler(packet, LoadoutService::receive);
            Vars.netServer.addPacketHandler("modular-shipyard-apply-v5", LoadoutService::receive);
            Vars.netServer.addPacketHandler("modular-shipyard-apply-v4", LoadoutService::receive);
        }
        if(Vars.netClient != null) Vars.netClient.addPacketHandler(reply, message -> {
            pending = false;
            feedback = message;
        });
        Events.on(PlayerLeave.class, event -> lastRequest.remove(event.player.id));
        Events.on(ResetEvent.class, event -> {
            lastRequest.clear();
            pending = false;
            feedback = "";
        });
        Events.on(UnitChangeEvent.class, event -> {
            // Core respawn assigns a player after the unit exists. Recheck after assignment.
            if(!Vars.net.client()) Core.app.post(() -> {
                if(event.player.unit() == event.unit) restore(event.player);
            });
        });
    }

    public static void request(ShipyardBuild yard,Unit ship,ShipLoadout loadout,ShipCosmetics cosmetics,String preset){
        feedback = "";
        if(Vars.net.client()){
            pending = true;
            pendingSince = System.currentTimeMillis();
            Call.serverPacketReliable(packet, yard.pos() + "," + ship.id + "," + loadout.stats() + "," + cosmetics.encode() + "," + loadout.weapon().name()+","+(preset.isEmpty()?"-":PresetService.token(preset)));
        }else{
            feedback = apply(Vars.player, yard.pos(), ship.id, loadout, cosmetics,preset);
        }
    }

    public static boolean waiting(){
        if(pending && System.currentTimeMillis() - pendingSince > 8000){
            pending = false;
            feedback = "No server response. Reopen the editor to check your ship before retrying.";
        }
        return pending;
    }

    private static void receive(Player sender, String payload){
        if(Vars.net.client() || sender == null || sender.con == null) return;
        long now = System.currentTimeMillis();
        Long previous = lastRequest.get(sender.id);
        if(previous != null && now - previous < 250) return;
        lastRequest.put(sender.id, now);
        ApplyRequest request=decodeRequest(payload);
        String response=request==null?"Invalid Shipyard request.":apply(sender,request.position,request.unitId,request.loadout,request.cosmetics,request.preset);
        Call.clientPacketReliable(sender.con,reply,response);
    }
    private record ApplyRequest(int position,int unitId,ShipLoadout loadout,ShipCosmetics cosmetics,String preset){}
    /** Recover malformed wire fields only; applying a valid request has its own error boundary. */
    private static ApplyRequest decodeRequest(String payload){
        if(payload==null || payload.length()>320)return null;
        String[] fields=payload.split(",",-1);
        if(fields.length!=10 && fields.length!=11 && fields.length!=12)return null;
        try{
            return new ApplyRequest(Integer.parseInt(fields[0]),Integer.parseInt(fields[1]),
                new ShipLoadout(Integer.parseInt(fields[2]),Integer.parseInt(fields[3]),Integer.parseInt(fields[4]),Integer.parseInt(fields[5]),Integer.parseInt(fields[6]),fields.length>=11?ShipWeapon.parse(fields[10]):ShipWeapon.standard),
                ShipCosmetics.parse(fields[7]+","+fields[8]+","+fields[9]),fields.length==12 && !fields[11].equals("-")?PresetService.untoken(fields[11]):"");
        }catch(IllegalArgumentException malformedRequest){return null;}
    }

    /** Public for actual-engine integration tests; also the only request mutation path. */
    public static String apply(Player player, int position, int unitId, int speed, int cargo){
        return apply(player, position, unitId, speed, cargo, 0);
    }

    public static String apply(Player player, int position, int unitId, int speed, int cargo, int construction){
        return apply(player, position, unitId, speed, cargo, construction, 0, 0);
    }

    public static String apply(Player player, int position, int unitId, int speed, int cargo, int construction, int durability, int fireRate){
        return apply(player, position, unitId, new ShipLoadout(speed, cargo, construction, durability, fireRate),
            player == null ? ShipCosmetics.defaults : ShipVariants.cosmetics(player.unit().type));
    }

    public static String apply(Player player, int position, int unitId, ShipLoadout loadout, ShipCosmetics cosmetics){
        return apply(player,position,unitId,loadout,cosmetics,"");
    }

    public static String apply(Player player,int position,int unitId,ShipLoadout loadout,ShipCosmetics cosmetics,String preset){
        if(Vars.net.client()) return "Only the server can apply loadouts.";
        if(!(Vars.world.build(position) instanceof ShipyardBuild yard) || !yard.editing(player)
            || yard.ship.id != unitId) return "Possess a powered, friendly Shipyard to edit your docked ship.";
        Unit ship=yard.ship;
        ShipTier tier = ShipTier.of(ship.type);
        if(!loadout.valid(tier)) return "Loadout exceeds this ship's budget or slider limits.";
        if(!cosmetics.valid()) return "Invalid cosmetic colours. Use opaque six-digit HEX colours.";
        if(ship.stack.amount > loadout.capacity(tier.base())) return "Unload cargo before reducing capacity below the amount carried.";
        String key = key(player, tier);
        try{if(!preset.isEmpty())preset=PresetService.name(preset);PresetService.validate(player,tier,preset);}catch(IllegalArgumentException error){return error.getMessage();}
        Properties before=(Properties)profiles.clone();
        String coloursKey=key+".colours";
        profiles.setProperty(key, loadout.encode());
        profiles.setProperty(coloursKey, cosmetics.encode());
        PresetService.save(player,tier,preset,loadout,cosmetics);
        try{
            persist();
        }catch(IOException failure){
            profiles.clear();profiles.putAll(before);
            Log.err("Could not save Shipyard loadout", failure);
            return "Could not save the loadout; no changes applied.";
        }
        UnitLoadouts.install(ship, tier, loadout, cosmetics);
        ShipyardSessions.sync(yard,ship,false);
        PresetService.publish(player,tier);
        return "Loadout applied and saved.";
    }

    private static String key(Player player, ShipTier tier){
        return player.uuid() + "." + tier.name();
    }

    static void persist() throws IOException{
        var directory=file.parent().file();
        if(!directory.isDirectory() && !directory.mkdirs())throw new IOException("Cannot create Shipyard profile directory");
        Fi temporary=file.sibling(file.name()+".tmp");
        try(var output=new FileOutputStream(temporary.file())){
            profiles.store(output,"Modular Shipyard profile fields: speed,cargo,construction,durability,fireRate[,weapon]; .colours = hull,accents,engine HEX or - for native defaults");
        }
        try{temporary.moveTo(file);}
        catch(ArcRuntimeException nativeFileFailure){
            // Arc wraps filesystem I/O; preserve its portable replacement behavior.
            Throwable cause=nativeFileFailure.getCause();
            while(cause instanceof ArcRuntimeException)cause=cause.getCause();
            if(cause instanceof IOException)throw new IOException("Cannot replace Shipyard profiles",nativeFileFailure);
            throw nativeFileFailure;
        }
    }

    public static void restore(Player player){
        if(Vars.net.client() || player == null || player.dead()
            || (Vars.state.rules.env & Env.scorching) != 0) return;
        Unit ship = player.unit();
        if(ship.getPlayer() != player) return;
        ShipTier tier = ShipTier.of(ship.type);
        if(tier == null) return;
        String raw = profiles.getProperty(key(player, tier));
        ShipLoadout loadout;
        try{loadout=raw==null?ShipVariants.current(ship.type):ShipLoadout.parse(raw).forRestore(tier);}
        catch(IllegalArgumentException malformedLoadout){
            Log.warn("Ignoring invalid Shipyard @ profile: @",tier.name(),malformedLoadout.getMessage());return;
        }
        ShipCosmetics cosmetics=colours(player,tier);
        if(loadout.valid(tier) && ship.stack.amount<=loadout.capacity(tier.base())
            && (!loadout.equals(ShipVariants.current(ship.type)) || !cosmetics.equals(ShipVariants.cosmetics(ship.type))))
            UnitLoadouts.install(ship,tier,loadout,cosmetics);
    }

    static ShipCosmetics colours(Player player,ShipTier tier){
        String raw=profiles.getProperty(key(player,tier)+".colours");
        if(raw==null)return CosmeticPreferences.forPlayer(player);
        try{return ShipCosmetics.parse(raw);}
        catch(IllegalArgumentException malformedColours){
            Log.warn("Using native Shipyard @ colours: @",tier.name(),malformedColours.getMessage());
            return ShipCosmetics.defaults;
        }
    }
}
