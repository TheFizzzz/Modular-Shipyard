package shipyard.service;

import arc.Events;
import arc.struct.IntMap;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import shipyard.model.ShipTier;

/** Reconcile a newly visible core ship with the authority's persisted profile after joining. */
public final class ProfileSync{
    private static final String packet="modular-shipyard-profile-sync-v1";
    private static final IntMap<Long> lastRequest=new IntMap<>();
    private static Unit requested;
    private static int acknowledged=-1;
    private static long lastSent;

    private ProfileSync(){}

    public static void init(){
        Events.on(ResetEvent.class,event->clear());
        Events.on(WorldLoadBeginEvent.class,event->clear());
        Events.on(PlayerLeave.class,event->lastRequest.remove(event.player.id));
        Events.run(Trigger.update,ProfileSync::update);
        if(Vars.netServer!=null)Vars.netServer.addPacketHandler(packet,ProfileSync::receive);
        if(Vars.netClient!=null)Vars.netClient.addPacketHandler(packet,ProfileSync::acknowledge);
    }

    private static void clear(){lastRequest.clear();requested=null;acknowledged=-1;lastSent=0;}

    private static void update(){
        if(Vars.headless || !Vars.net.client() || !Vars.state.isPlaying() || Vars.netClient.isConnecting() || Vars.player==null)return;
        Unit ship=Vars.player.unit();
        if(ship==null || !ship.isValid() || !ship.spawnedByCore || ShipTier.of(ship.type)==null || ship.id==acknowledged)return;
        if(requested!=ship){requested=ship;lastSent=0;}
        long now=Time.millis();
        if(lastSent!=0 && now-lastSent<2000)return;
        lastSent=now;
        Call.serverPacketReliable(packet,UnitLoadouts.worldEpoch()+","+ship.id);
    }

    private static Integer decode(String payload){
        if(payload==null || payload.length()>60)return null;
        String[] fields=payload.split(",",-1);
        if(fields.length!=2 || !fields[0].equals(UnitLoadouts.worldEpoch()))return null;
        try{return Integer.valueOf(fields[1]);}
        catch(NumberFormatException malformedUnit){return null;}
    }

    private static void receive(Player player,String payload){
        if(Vars.net.client() || player==null || player.con==null || player.dead())return;
        Integer id=decode(payload);Unit ship=player.unit();ShipTier tier=ShipTier.of(ship.type);
        if(id==null || id!=ship.id || !ship.spawnedByCore || tier==null || ship.getPlayer()!=player)return;
        long now=Time.millis();Long previous=lastRequest.get(player.id);
        if(previous!=null && now-previous<1000)return;
        lastRequest.put(player.id,now);
        LoadoutService.restore(player);
        // Resend even if the authoritative view already matched: an early client snapshot may not have.
        UnitLoadouts.track(ship);
        PresetService.publish(player,tier);
        Call.clientPacketReliable(player.con,packet,UnitLoadouts.worldEpoch()+","+ship.id);
    }

    private static void acknowledge(String payload){
        if(!Vars.net.client() || requested==null || Vars.player==null || Vars.player.unit()!=requested)return;
        Integer id=decode(payload);
        if(id!=null && id==requested.id)acknowledged=id;
    }
}
