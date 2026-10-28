package shipyard.service;

import arc.Core;
import arc.Events;
import arc.struct.IntMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.meta.Env;
import shipyard.content.ShipVariants;
import shipyard.model.*;

/** Local default palette; multiplayer sends colours only, once per world/connection. */
public final class CosmeticPreferences{
    private static final String setting="modular-shipyard-default-colours-v1";
    private static final String packet="modular-shipyard-default-colours-v1";
    private static final IntMap<ShipCosmetics> remote=new IntMap<>();
    private static ShipCosmetics saved;
    private static boolean sent;

    private CosmeticPreferences(){}

    public static void init(){
        saved=null;
        Events.on(ResetEvent.class,event->clear());
        Events.on(WorldLoadBeginEvent.class,event->clear());
        Events.on(PlayerLeave.class,event->remote.remove(event.player.id));
        Events.run(Trigger.update,CosmeticPreferences::update);
        if(Vars.netServer!=null)Vars.netServer.addPacketHandler(packet,CosmeticPreferences::receive);
    }

    public static ShipCosmetics saved(){
        if(saved==null){
            try{saved=ShipCosmetics.parse(Core.settings.getString(setting,"-,-,-"));}
            catch(IllegalArgumentException malformedPreference){
                Log.warn("Using native default ship colours: @",malformedPreference.getMessage());
                saved=ShipCosmetics.defaults;
            }
        }
        return saved;
    }

    public static void save(ShipCosmetics colours){
        if(colours==null || !colours.valid())throw new IllegalArgumentException("Invalid default ship colours");
        Core.settings.put(setting,colours.encode());
        Core.settings.manualSave();
        saved=colours;
    }

    static ShipCosmetics forPlayer(Player player){
        return !Vars.headless && player==Vars.player?saved():remote.get(player.id,ShipCosmetics.defaults);
    }

    private static void clear(){remote.clear();sent=false;}

    private static void update(){
        if(Vars.headless || !Vars.net.client() || !Vars.state.isPlaying() || Vars.netClient.isConnecting() || sent)return;
        // World loading can finish before native connectConfirm. A replicated unit means the host
        // has confirmed the connection and will accept ordinary client packets.
        if(Vars.player==null || Vars.player.unit()==null || !Vars.player.unit().isValid())return;
        sent=true;
        Call.serverPacketReliable(packet,saved().encode());
    }

    private static void receive(Player player,String payload){
        if(Vars.net.client() || player==null || player.con==null || remote.containsKey(player.id)
            || payload==null || payload.length()>24)return;
        ShipCosmetics colours;
        try{colours=ShipCosmetics.parse(payload);}
        catch(IllegalArgumentException malformedColours){return;}
        remote.put(player.id,colours);
        apply(player);
    }

    /** A colour handshake cannot change stats, weapons, named snapshots or server profiles. */
    private static void apply(Player player){
        if(player.dead() || (Vars.state.rules.env & Env.scorching)!=0)return;
        Unit ship=player.unit();ShipTier tier=ShipTier.of(ship.type);
        if(tier==null || ship.getPlayer()!=player)return;
        ShipCosmetics colours=LoadoutService.colours(player,tier);
        if(!colours.equals(ShipVariants.cosmetics(ship.type)))
            UnitLoadouts.install(ship,tier,ShipVariants.current(ship.type),colours);
    }
}
