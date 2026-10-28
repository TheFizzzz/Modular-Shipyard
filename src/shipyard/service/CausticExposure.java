package shipyard.service;

import arc.Events;
import arc.util.Time;
import java.util.HashMap;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.game.EventType.*;
import mindustry.gen.Unit;

/** Authority-only, transient contact buildup; native Corroded handles damage, saves and snapshots. */
public final class CausticExposure{
    public static final float threshold=90f,duration=180f,decay=.75f;
    private static final HashMap<Long,Exposure> contacts=new HashMap<>();
    private static final class Exposure{
        final Unit owner,target;final int ownerId,targetId;
        float progress,penetrationLeft;boolean touched;
        Exposure(Unit owner,Unit target){this.owner=owner;this.target=target;ownerId=owner.id;targetId=target.id;}
    }
    private CausticExposure(){}
    public static void init(){
        Events.on(ResetEvent.class,e->contacts.clear());
        Events.on(WorldLoadBeginEvent.class,e->contacts.clear());
        Events.run(Trigger.afterGameUpdate,CausticExposure::update);
    }
    private static long key(Unit owner,Unit target){return ((long)owner.id<<32)|(target.id&0xffffffffL);}
    public static void touch(Unit owner,Unit target){
        if(Vars.net.client() || owner.team==target.team || !owner.isAdded() || !target.isAdded()
            || target.dead || target.isImmune(StatusEffects.corroded))return;
        Exposure exposure=contacts.computeIfAbsent(key(owner,target),k->new Exposure(owner,target));
        if(exposure.owner!=owner || exposure.target!=target){exposure=new Exposure(owner,target);contacts.put(key(owner,target),exposure);}
        exposure.touched=true;
    }
    public static float progress(Unit owner,Unit target){
        Exposure exposure=contacts.get(key(owner,target));
        return exposure==null || exposure.owner!=owner || exposure.target!=target?0f:exposure.progress;
    }
    /** Only this owner's completed buildup grants penetration; native Corroded alone is insufficient. */
    public static float armorMultiplier(Unit owner,Unit target){
        Exposure exposure=contacts.get(key(owner,target));
        return exposure!=null && exposure.owner==owner && exposure.target==target
            && owner.isAdded() && !owner.dead && target.isAdded() && !target.dead && owner.team!=target.team
            && exposure.penetrationLeft>0f && target.hasEffect(StatusEffects.corroded) ? .5f : 1f;
    }
    public static void update(){
        if(Vars.net.client()){contacts.clear();return;}
        var iterator=contacts.values().iterator();
        while(iterator.hasNext()){
            Exposure exposure=iterator.next();
            if(!exposure.owner.isAdded() || exposure.owner.dead || exposure.owner.id!=exposure.ownerId
                || !exposure.target.isAdded() || exposure.target.dead || exposure.target.id!=exposure.targetId
                || exposure.owner.team==exposure.target.team){iterator.remove();continue;}
            exposure.penetrationLeft=Math.max(0f,exposure.penetrationLeft-Time.delta);
            if(exposure.touched){
                exposure.progress=Math.min(threshold,exposure.progress+Time.delta);
                if(exposure.progress>=threshold){
                    exposure.target.apply(StatusEffects.corroded,duration);
                    exposure.penetrationLeft=duration;
                }
            }else exposure.progress=Math.max(0f,exposure.progress-Time.delta*decay);
            exposure.touched=false;
            if(exposure.progress==0f && exposure.penetrationLeft==0f)iterator.remove();
        }
    }
}
