package shipyard.service;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.io.*;
import java.io.*;
import java.util.Base64;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.blocks.payloads.*;
import shipyard.model.*;
import shipyard.world.Shipyard.ShipyardBuild;
import shipyard.world.Shipyard;
import mindustry.type.UnitType;

/** Authority-owned docking state. Only a native control-select can start a session. */
public final class ShipyardSessions{
    private static final String statePacket="modular-shipyard-dock-v1", exitPacket="modular-shipyard-exit-v1";
    private static final arc.struct.IntMap<Unit> dockedShips=new arc.struct.IntMap<>();
    private static final arc.struct.IntMap<ShipyardBuild> yardsByPlayer=new arc.struct.IntMap<>();
    private ShipyardSessions(){}
    public static void remember(ShipyardBuild yard){dockedShips.put(yard.ship.id,yard.ship);if(yard.ownerId>=0)yardsByPlayer.put(yard.ownerId,yard);}
    public static void forget(ShipyardBuild yard){if(yard.ship!=null)dockedShips.remove(yard.ship.id);yardsByPlayer.remove(yard.ownerId);}

    public static void init(){
        Events.on(ResetEvent.class,e->{dockedShips.clear();yardsByPlayer.clear();});
        Events.on(WorldLoadBeginEvent.class,e->{dockedShips.clear();yardsByPlayer.clear();});
        if(Vars.netServer!=null) Vars.netServer.addPacketHandler(exitPacket,(player,payload)->{
            if(payload==null || payload.length()>80)return;
            String[] fields=payload.split(",",-1);if(fields.length!=2)return;
            int position;long session;
            try{position=Integer.parseInt(fields[0]);session=Long.parseLong(fields[1]);}
            catch(NumberFormatException malformedExit){return;}
            if(Vars.world.build(position) instanceof ShipyardBuild yard && yard.owns(player) && yard.session==session)yard.release(player);
        });
        if(Vars.netClient!=null) Vars.netClient.addPacketHandler(statePacket,ShipyardSessions::receive);
        Events.on(UnitChangeEvent.class,event->{
            if(Vars.net.client()) return;
            ShipyardBuild yard=yardsByPlayer.get(event.player.id);
            if(yard!=null && event.unit!=yard.unit()) Core.app.post(()->{if(yard.owns(event.player))yard.release(event.player);});
        });
        Events.on(PlayerLeave.class,event->{
            if(Vars.net.client()) return;
            ShipyardBuild yard=yardsByPlayer.remove(event.player.id);
            if(yard!=null){yard.ownerId=-1;yard.unit().resetController();sync(yard,yard.ship,false);}
        });
    }
    /** Native PayloadUnit.remove destroys carried payloads; detach them during safe suspension. */
    public static void suspend(Unit ship){
        if(ship instanceof Payloadc carrier){
            var carried=carrier.payloads();
            carrier.payloads(new arc.struct.Seq<>());
            try{ship.remove();}finally{carrier.payloads(carried);}
        }else ship.remove();
    }
    public static Unit docked(int id){return dockedShips.get(id);}
    public static ShipyardBuild yard(Player player){return player==null?null:yardsByPlayer.get(player.id);}
    private static Shipyard family(UnitType type){
        for(var block:Vars.content.blocks())if(block instanceof Shipyard yard && yard.supportsShip(type))return yard;
        throw new IllegalArgumentException("Unsupported docked ship type: "+type.name);
    }
    public static void exit(ShipyardBuild yard){
        if(Vars.player==null || !yard.owns(Vars.player)) return;
        if(Vars.net.client()) Call.serverPacketReliable(exitPacket,yard.pos()+","+yard.session);
        else yard.release(Vars.player);
    }
    public static void writeShip(Writes out,Unit ship){
        Shipyard family=family(ship.type);
        out.i(ship.id);
        out.str(family.loadout(ship));out.str(family.cosmetics(ship).encode());
        Payload.write(new UnitPayload(ship),out);
    }
    public static Unit readShip(Reads in){
        int id=in.i();String loadout=in.str();ShipCosmetics colours=ShipCosmetics.parse(in.str());
        Payload payload=Payload.read(in);
        if(!(payload instanceof UnitPayload unitPayload))throw new IllegalArgumentException("Expected docked unit payload");
        Unit ship=unitPayload.unit;ship.id=id;
        family(ship.type).restoreShip(ship,loadout,colours);
        return ship;
    }
    public static void sync(ShipyardBuild yard,Unit ship,boolean released){
        if(!Vars.net.server()) return;
        var bytes=new ByteArrayOutputStream();var out=new Writes(new DataOutputStream(bytes));
        out.i(yard.pos());out.i(yard.ownerId);out.l(yard.session);out.bool(released);
        out.bool(yard.wasCore);out.f(yard.entryX);out.f(yard.entryY);writeShip(out,ship);
        // Unexpected in-memory/native serialization failures must remain visible.
        Call.clientPacketReliable(statePacket,Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }
    private static void controlReturned(Player player,Unit ship,int position){
        if(player==null)return;
        player.justSwitchFrom=null;player.justSwitchTo=null;
        if(player.team()==ship.team)player.unit(ship);
        else if(player.unit() instanceof BlockUnitc proxy && proxy.tile().pos()==position)player.clearUnit();
    }
    private record DockSnapshot(int position,int ownerId,long session,boolean released,boolean wasCore,float x,float y,Unit ship){}
    private static DockSnapshot decode(String payload) throws IOException{
        var input=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(payload)));
        int position=input.readInt(),ownerId=input.readInt();long session=input.readLong();
        boolean released=input.readBoolean(),wasCore=input.readBoolean();float x=input.readFloat(),y=input.readFloat();
        if(!Float.isFinite(x) || !Float.isFinite(y))throw new IOException("Invalid docked ship position");
        Unit ship;
        try{ship=readShip(new Reads(input));}
        catch(RuntimeException nativeFailure){
            // Arc Reads wraps checked I/O. Unwrap only that exact cause; rethrow bugs.
            if(nativeFailure.getCause() instanceof IOException truncatedPayload)throw truncatedPayload;
            throw nativeFailure;
        }
        return new DockSnapshot(position,ownerId,session,released,wasCore,x,y,ship);
    }
    private static void receive(String payload){
        if(!Vars.net.client() || payload==null || payload.length()>200000)return;
        DockSnapshot snapshot;
        try{snapshot=decode(payload);}
        catch(IOException | IllegalArgumentException malformedSession){Log.warn("Rejected malformed Shipyard session: @",malformedSession.getMessage());return;}
        // World mutation is deliberately outside the parsing recovery boundary.
        int position=snapshot.position,ownerId=snapshot.ownerId;Unit received=snapshot.ship;
        Player player=Groups.player.getByID(ownerId);
        if(!(Vars.world.build(position) instanceof ShipyardBuild yard)){
            if(snapshot.released){
                dockedShips.remove(received.id);yardsByPlayer.remove(ownerId);
                Unit previous=Groups.unit.getByID(received.id);if(previous!=null)suspend(previous);
                received.spawnedByCore=snapshot.wasCore;received.set(snapshot.x,snapshot.y);received.add();controlReturned(player,received,position);
            }
            return;
        }
        Unit previous=Groups.unit.getByID(received.id);if(previous!=null)suspend(previous);
        if(yard.ship!=null && yard.ship.id==received.id){
            Unit retained=yard.ship;retained.setType(received.type);((Shipyard)yard.block).bindShip(retained);retained.health=received.health;received=retained;
        }
        if(snapshot.released){
            forget(yard);yard.ship=null;yard.ownerId=-1;yard.opened=false;yard.unit().resetController();
            received.spawnedByCore=snapshot.wasCore;received.set(snapshot.x,snapshot.y);received.add();controlReturned(player,received,position);
        }else{
            yard.ship=received;yard.ownerId=ownerId;yard.session=snapshot.session;yard.wasCore=snapshot.wasCore;yard.entryX=snapshot.x;yard.entryY=snapshot.y;
            remember(yard);if(ownerId<0)yard.unit().resetController();
            if(player!=null){player.justSwitchFrom=null;player.justSwitchTo=null;player.unit(yard.unit());}
        }
    }
}
