package shipyard.service;

import arc.Events;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.SaveFileReader.CustomChunk;
import mindustry.io.SaveVersion;
import mindustry.type.UnitType;
import shipyard.content.ShipVariants;
import shipyard.model.*;
import shipyard.type.ShipVariant;

/** Explicit per-unit state: native codecs serialize only the three stable hull IDs. */
public final class UnitLoadouts{
    private static final String packet = "modular-shipyard-unit-v4";
    private static final ConcurrentHashMap<Integer, Entry> entries = new ConcurrentHashMap<>();
    private static volatile String epoch = UUID.randomUUID().toString();
    private static boolean migrate;

    private static final class Entry{
        final ShipTier tier;
        final ShipLoadout loadout;
        final UnitType view;
        final ShipCosmetics cosmetics;
        Unit bound;
        Entry(ShipTier tier, ShipLoadout loadout, ShipCosmetics cosmetics){
            this(tier,loadout,cosmetics,ShipVariants.get(tier,loadout,cosmetics));
        }
        Entry(ShipTier tier, ShipLoadout loadout, ShipCosmetics cosmetics,UnitType view){
            this.tier = tier;
            this.loadout = loadout;
            this.cosmetics = cosmetics;
            this.view = view;
        }
    }

    private UnitLoadouts(){}

    public static void init(){
        Events.on(ResetEvent.class, event -> clear());
        Events.on(WorldLoadBeginEvent.class, event -> clear());
        Events.on(WorldLoadEvent.class, event -> migrate = true);
        Events.on(SaveLoadEvent.class, event -> {
            migrate = true;
            refresh();
        });
        Events.run(Trigger.update, UnitLoadouts::refresh);
        SaveVersion.addCustomChunk("modular-shipyard-units-v1", new CustomChunk(){
            public void write(DataOutput output) throws IOException{
                output.writeInt(4);
                output.writeUTF(epoch);
                var active = entries.entrySet().stream().filter(item -> {
                    Unit unit = Groups.unit.getByID(item.getKey());
                    return unit != null && unit.isAdded() && ShipTier.of(unit.type) == item.getValue().tier;
                }).toList();
                output.writeInt(active.size());
                for(var item : active){
                    output.writeInt(item.getKey());
                    output.writeByte(item.getValue().tier.ordinal());
                    ShipLoadout loadout = item.getValue().loadout;
                    output.writeInt(loadout.speed()); output.writeInt(loadout.cargo()); output.writeInt(loadout.construction());
                    output.writeInt(loadout.durability()); output.writeInt(loadout.fireRate());
                    ShipCosmetics cosmetics = item.getValue().cosmetics;
                    output.writeInt(cosmetics.hull()); output.writeInt(cosmetics.accents()); output.writeInt(cosmetics.engine());
                    output.writeUTF(loadout.weapon().name());
                }
            }
            public void read(DataInput input) throws IOException{
                int version = input.readInt();
                if(version < 1 || version > 4) throw new IOException("Unsupported Shipyard unit state version");
                String savedEpoch = input.readUTF();
                if(Vars.net.client()) epoch = savedEpoch;
                int count = input.readInt();
                if(count < 0 || count > 1000000) throw new IOException("Invalid Shipyard unit count");
                for(int i = 0; i < count; i++){
                    int id = input.readInt(), tier = input.readUnsignedByte();
                    ShipLoadout loadout = new ShipLoadout(input.readInt(), input.readInt(), input.readInt(),
                        version >= 2 ? input.readInt() : 0, version >= 2 ? input.readInt() : 0);
                    ShipCosmetics cosmetics = version >= 3 ? new ShipCosmetics(input.readInt(), input.readInt(), input.readInt())
                        : ShipCosmetics.defaults;
                    if(version >= 4) loadout = loadout.withWeapon(ShipWeapon.parse(input.readUTF()));
                    accept(id, tier, loadout, cosmetics);
                }
                refresh();
            }
        });
        if(Vars.netClient != null) Vars.netClient.addPacketHandler(packet, payload -> {
            if(!Vars.net.client() || payload == null || payload.length() > 200) return;
            String[] fields = payload.split(",", -1);
            if(fields.length != 12 || !epoch.equals(fields[0])) return;
            int unitId,tierIndex;ShipLoadout loadout;ShipCosmetics cosmetics;
            try{
                unitId=Integer.parseInt(fields[1]);tierIndex=Integer.parseInt(fields[2]);
                loadout=new ShipLoadout(Integer.parseInt(fields[3]),Integer.parseInt(fields[4]),Integer.parseInt(fields[5]),Integer.parseInt(fields[6]),Integer.parseInt(fields[7]),ShipWeapon.parse(fields[11]));
                cosmetics=ShipCosmetics.parse(fields[8]+","+fields[9]+","+fields[10]);
            }catch(IllegalArgumentException malformedSnapshot){return;}
            accept(unitId,tierIndex,loadout,cosmetics);refresh();
        });
    }

    static String worldEpoch(){return epoch;}

    private static void clear(){
        entries.clear();
        epoch = UUID.randomUUID().toString();
        migrate = false;
    }

    private static void accept(int id, int ordinal, ShipLoadout loadout, ShipCosmetics cosmetics){
        if(ordinal < 0 || ordinal >= ShipTier.values().length) return;
        ShipTier tier = ShipTier.values()[ordinal];
        ShipLoadout restored=loadout.forRestore(tier);
        if(restored.valid(tier) && cosmetics.valid()) entries.put(id, new Entry(tier, restored, cosmetics));
    }

    public static void install(Unit unit, ShipTier tier, ShipLoadout loadout){
        install(unit, tier, loadout, ShipVariants.cosmetics(unit.type));
    }

    public static void install(Unit unit, ShipTier tier, ShipLoadout loadout, ShipCosmetics cosmetics){
        if(!loadout.valid(tier) || !cosmetics.valid()) throw new IllegalArgumentException("Invalid loadout");
        // Only a deliberate authoritative install rescales HP. Reattaching a view after
        // snapshots/save reads preserves the authoritative HP already carried by the codec.
        ShipWeapon previousWeapon = ShipVariants.current(unit.type).weapon();
        float previousReduction=ShipVariants.current(unit.type).damageReduction();
        float previousHealth = unit.health, previousMaxHealth = unit.maxHealth;
        float fraction = unit.maxHealth > 0f ? Math.max(0f, Math.min(1f, unit.health / unit.maxHealth)) : 1f;
        Entry entry = new Entry(tier, loadout, cosmetics);
        entries.put(unit.id, entry);
        attach(unit, entry);
        if(previousWeapon != loadout.weapon()) for(var mount : unit.mounts){
            mount.reload = loadout.weapon()==ShipWeapon.railgun?VectorReload.remaining(unit):mount.weapon.reload;mount.charging = false;mount.shoot = false;
        }
        unit.healthMultiplier *= (1f-previousReduction)/(1f-loadout.damageReduction());
        unit.health = previousMaxHealth == unit.maxHealth ? previousHealth : fraction * unit.maxHealth;
        unit.vel.limit(unit.speed());
        if(Vars.net.server()) Call.clientPacketReliable(packet, epoch + "," + unit.id + "," + tier.ordinal()
            + "," + loadout.stats() + "," + cosmetics.encode() + "," + loadout.weapon().name());
    }

    /** Register an existing view for native snapshot repair without replacing its guns or HP. */
    public static void track(Unit unit){
        ShipTier tier=ShipTier.of(unit.type);
        ShipLoadout loadout=ShipVariants.current(unit.type);
        ShipCosmetics cosmetics=ShipVariants.cosmetics(unit.type);
        Entry entry=new Entry(tier,loadout,cosmetics,unit.type);entry.bound=unit;entries.put(unit.id,entry);
        if(Vars.net.server()) Call.clientPacketReliable(packet, epoch + "," + unit.id + "," + tier.ordinal()
            + "," + loadout.stats() + "," + cosmetics.encode() + "," + loadout.weapon().name());
    }

    private static void attach(Unit unit, Entry entry){
        if(unit.type != entry.view){
            boolean counted = unit.isAdded() && unit.type.id != entry.view.id;
            if(counted) unit.team.data().updateCount(unit.type, -1);
            unit.setType(entry.view);
            if(counted) unit.team.data().updateCount(entry.view, 1);
        }
        shipyard.content.ShipWeapons.bind(unit);
        entry.bound = unit;
    }

    /** Reattach after a native snapshot restores the registered, neutral hull type. */
    public static void refreshUnit(Unit unit){
        Entry entry = entries.get(unit.id);
        if(entry != null && ShipTier.of(unit.type) == entry.tier) attach(unit, entry);
    }

    public static void refresh(){
        if(migrate){
            migrate = false;
            for(Unit unit : Groups.unit){
                if(unit.type instanceof ShipVariant variant && variant.legacy()){
                    ShipLoadout loadout = variant.loadout();
                    install(unit, variant.tier, loadout.valid(variant.tier) ? loadout : ShipLoadout.defaults);
                }
            }
        }
        for(var item : entries.entrySet()){
            Entry entry = item.getValue();
            Unit unit = Groups.unit.getByID(item.getKey());
            if(unit==null)unit=ShipyardSessions.docked(item.getKey());
            if(unit != null && ShipTier.of(unit.type) == entry.tier){
                attach(unit, entry);
            }else if(entry.bound != null || unit != null){
                entries.remove(item.getKey(), entry);
            }
        }
    }
}
