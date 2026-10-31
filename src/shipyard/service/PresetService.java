package shipyard.service;

import arc.Events;
import arc.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import shipyard.model.*;
import shipyard.world.Shipyard.ShipyardBuild;

/** Named snapshots share the authoritative profile transaction; catalogues are private to their owner. */
public final class PresetService{
    public static final int maxPresets=12;
    public record Preset(ShipLoadout loadout,ShipCosmetics colours){}
    public record Catalog(ShipTier tier,String selected,Map<String,Preset> presets){}
    private static final String fetch="modular-shipyard-presets-v1", remove="modular-shipyard-preset-delete-v1";
    private static final EnumMap<ShipTier,Catalog> local=new EnumMap<>(ShipTier.class);
    public static int revision;
    private PresetService(){}
    public static String name(String raw){
        String clean=raw.strip();
        if(clean.isEmpty() || clean.length()>32 || !clean.matches("[\\p{L}\\p{M}\\p{N} _.'-]+"))
            throw new IllegalArgumentException("Use 1–32 letters, numbers, spaces, dots, apostrophes, hyphens or underscores.");
        return clean;
    }
    public static String token(String name){return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));}
    public static String untoken(String token){return name(new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8));}
    private static String prefix(Player player,ShipTier tier){return player.uuid()+"."+tier.name();}
    public static Catalog catalog(Player player,ShipTier tier){
        String root=prefix(player,tier),start=root+".preset.";
        Map<String,Preset> sorted=new TreeMap<>(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        for(String key:LoadoutService.profiles.stringPropertyNames())if(key.startsWith(start)){
            try{
                String selected=untoken(key.substring(start.length()));
                String[] encodedFields=LoadoutService.profiles.getProperty(key).split("\\|",-1);
                if(encodedFields.length!=2)throw new IllegalArgumentException("Expected loadout and colours");
                Preset preset=new Preset(ShipLoadout.parse(encodedFields[0]),ShipCosmetics.parse(encodedFields[1]));
                if(!preset.loadout.inBounds() || !preset.colours.valid())throw new IllegalArgumentException("Preset fields out of bounds");
                sorted.put(selected,preset);
            }catch(IllegalArgumentException malformedPreset){Log.warn("Ignoring invalid Shipyard @ preset: @",tier.name(),malformedPreset.getMessage());}
        }
        String selected=LoadoutService.profiles.getProperty(root+".selected","");
        if(!sorted.containsKey(selected))selected="";
        return new Catalog(tier,selected,Collections.unmodifiableMap(sorted));
    }
    static void validate(Player player,ShipTier tier,String selected){
        if(selected.isEmpty())return;
        name(selected);Catalog catalog=catalog(player,tier);
        if(!catalog.presets.containsKey(selected) && catalog.presets.size()>=maxPresets)
            throw new IllegalArgumentException("This ship tier already has 12 loadouts. Delete one first.");
    }
    static void save(Player player,ShipTier tier,String selected,ShipLoadout loadout,ShipCosmetics colours){
        String root=prefix(player,tier);
        if(selected.isEmpty())LoadoutService.profiles.remove(root+".selected");
        else{
            LoadoutService.profiles.setProperty(root+".selected",selected);
            LoadoutService.profiles.setProperty(root+".preset."+token(selected),loadout.encode()+"|"+colours.encode());
        }
    }
    public static Catalog local(ShipTier tier){return local.get(tier);}
    public static void init(){
        Events.on(ResetEvent.class,e->{local.clear();revision++;});
        Events.on(WorldLoadBeginEvent.class,e->{local.clear();revision++;});
        if(Vars.netServer!=null){
            Vars.netServer.addPacketHandler(fetch,(player,payload)->{
                if(payload==null || payload.length()>16)return;
                int position;
                try{position=Integer.parseInt(payload);}catch(NumberFormatException malformedPosition){return;}
                if(Vars.world.build(position) instanceof ShipyardBuild yard && yard.editing(player))publish(player,ShipTier.of(yard.ship.type));
            });
            Vars.netServer.addPacketHandler(remove,(player,payload)->{
                if(payload==null || payload.length()>240)return;
                String[] fields=payload.split(",",-1);if(fields.length!=2)return;
                int position;String selected;
                try{position=Integer.parseInt(fields[0]);selected=untoken(fields[1]);}
                catch(IllegalArgumentException malformedDelete){return;}
                if(Vars.world.build(position) instanceof ShipyardBuild yard && yard.editing(player))
                    Call.clientPacketReliable(player.con,remove,delete(player,yard,selected));
            });
        }
        if(Vars.netClient!=null){
            Vars.netClient.addPacketHandler(remove,message->LoadoutService.feedback=message);
            Vars.netClient.addPacketHandler(fetch,payload->{
            if(!Vars.net.client() || payload==null || payload.length()>24000)return;
            Catalog snapshot;
            try{snapshot=decodeCatalog(payload);}
            catch(IOException | IllegalArgumentException malformedCatalog){return;}
            if(snapshot!=null){local.put(snapshot.tier,snapshot);revision++;}
            });
        }
    }
    private static Catalog decodeCatalog(String payload) throws IOException{
        var input=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(payload)));
        if(input.readInt()!=1)return null;
        int tierIndex=input.readUnsignedByte();if(tierIndex>=ShipTier.values().length)return null;
        ShipTier tier=ShipTier.values()[tierIndex];String selected=input.readUTF();int count=input.readInt();
        if(count<0 || count>maxPresets)return null;
        Map<String,Preset> presets=new LinkedHashMap<>();
        for(int i=0;i<count;i++){
            String presetName=name(input.readUTF());Preset preset=new Preset(ShipLoadout.parse(input.readUTF()),ShipCosmetics.parse(input.readUTF()));
            if(!preset.loadout.inBounds() || !preset.colours.valid())return null;
            presets.put(presetName,preset);
        }
        return new Catalog(tier,presets.containsKey(selected)?selected:"",Collections.unmodifiableMap(presets));
    }
    public static void request(ShipyardBuild yard){
        ShipTier tier=ShipTier.of(yard.ship.type);
        if(Vars.net.client())Call.serverPacketReliable(fetch,Integer.toString(yard.pos()));
        else publish(Vars.player,tier);
    }
    static void publish(Player player,ShipTier tier){
        if(tier==null)return;
        Catalog catalog=catalog(player,tier);
        if(!Vars.net.server() || !Vars.headless && player==Vars.player){local.put(tier,catalog);revision++;return;}
        if(player.con==null)return;
        var bytes=new ByteArrayOutputStream();
        try{
            var out=new DataOutputStream(bytes);
            out.writeInt(1);out.writeByte(tier.ordinal());out.writeUTF(catalog.selected);out.writeInt(catalog.presets.size());
            for(var entry:catalog.presets.entrySet()){
                out.writeUTF(entry.getKey());out.writeUTF(entry.getValue().loadout.encode());out.writeUTF(entry.getValue().colours.encode());
            }
        }catch(IOException encodingFailure){throw new UncheckedIOException("Cannot encode Shipyard presets",encodingFailure);}
        Call.clientPacketReliable(player.con,fetch,Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }
    public static void requestDelete(ShipyardBuild yard,String selected){
        if(Vars.net.client())Call.serverPacketReliable(remove,yard.pos()+","+token(name(selected)));
        else LoadoutService.feedback=delete(Vars.player,yard,name(selected));
    }
    public static String delete(Player player,ShipyardBuild yard,String selected){
        if(Vars.net.client() || !yard.editing(player))return "Possess the Shipyard to delete a loadout.";
        ShipTier tier=ShipTier.of(yard.ship.type);
        if(tier==null)return "This ship family uses its own loadouts.";
        String root=prefix(player,tier);
        Properties before=(Properties)LoadoutService.profiles.clone();
        LoadoutService.profiles.remove(root+".preset."+token(name(selected)));
        if(selected.equals(LoadoutService.profiles.getProperty(root+".selected")))LoadoutService.profiles.remove(root+".selected");
        try{LoadoutService.persist();}catch(IOException storageFailure){
            LoadoutService.profiles.clear();LoadoutService.profiles.putAll(before);
            Log.err("Could not delete saved Shipyard loadout",storageFailure);
            return "Could not delete the saved loadout.";
        }
        publish(player,tier);return "Saved loadout deleted.";
    }
}
