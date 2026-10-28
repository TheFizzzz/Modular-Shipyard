package shipyard.service;

import arc.Events;
import arc.struct.IntMap;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;

/** A fresh Vector can charge immediately; swapping presets cannot erase a charge cycle already started. */
public final class VectorReload{
    private record Cycle(Unit ship,float ready){}
    private static final IntMap<Cycle> cycles=new IntMap<>();
    private VectorReload(){}
    public static void init(){
        Events.on(ResetEvent.class,event->cycles.clear());Events.on(WorldLoadBeginEvent.class,event->cycles.clear());
        Events.run(Trigger.afterGameUpdate,()->{
            var entries=cycles.entries();while(entries.hasNext()){
                var entry=entries.next();
                Cycle cycle=entry.value;Unit ship=Groups.unit.getByID(entry.key);
                if(ship==null)ship=ShipyardSessions.docked(entry.key);
                if(ship!=cycle.ship || Time.time>=cycle.ready)entries.remove();
            }
        });
    }
    public static void charging(Unit ship,float reload){
        float duration=reload/Math.max(.05f,ship.reloadMultiplier);
        Cycle previous=cycles.get(ship.id);float ready=Time.time+duration;
        if(previous!=null && previous.ship==ship)ready=Math.max(ready,previous.ready);
        cycles.put(ship.id,new Cycle(ship,ready));
    }
    public static float remaining(Unit ship){
        Cycle cycle=cycles.get(ship.id);
        return cycle==null || cycle.ship!=ship?0f:Math.max(0f,cycle.ready-Time.time)*Math.max(.05f,ship.reloadMultiplier);
    }
}
