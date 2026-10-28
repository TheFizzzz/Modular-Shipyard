package shipyard;

import arc.Events;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;

/** Native damage path and owner/expiry isolation for the corrosion penetration reward. */
final class CausticPenChecks{
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(80,80,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Time.setDeltaProvider(()->1f);Time.delta=1;
        var exposure=ExperimentalChecks.exposure;
        var touch=exposure.getMethod("touch",Unit.class,Unit.class);var update=exposure.getMethod("update");
        var multiplier=exposure.getMethod("armorMultiplier",Unit.class,Unit.class);
        Unit a=UnitTypes.gamma.create(Team.sharded),b=UnitTypes.gamma.create(Team.sharded);
        a.set(250,300);b.set(250,280);a.add();b.add();ExperimentalChecks.equip(a,2,"caustic");ExperimentalChecks.equip(b,2,"caustic");
        Unit target=ExperimentalChecks.enemy(UnitTypes.dagger,290,300);target.armor=12;
        target.apply(StatusEffects.corroded,180);
        near((float)multiplier.invoke(null,a,target),1,"foreign Corroded granted penetration");
        for(int tick=0;tick<89;tick++){touch.invoke(null,a,target);update.invoke(null);}
        near((float)multiplier.invoke(null,a,target),1,"penetration before buildup");
        touch.invoke(null,a,target);update.invoke(null);
        near((float)multiplier.invoke(null,a,target),.5f,"completed buildup missing penetration");
        near((float)multiplier.invoke(null,b,target),1,"other owner inherited penetration");
        // Actual mounted beams use native armour damage, while retaining their original type fields.
        Vars.state.teams.updateTeamStats();float before=target.health;
        ExperimentalChecks.fire(a,target.x,target.y,24);float buffed=before-target.health;
        var gun=a.mounts[0].weapon.bullet;near(gun.armorMultiplier,1,"private adapter retained per-hit penetration");
        Events.fire(new mindustry.game.EventType.ResetEvent());target.health=10000;
        ExperimentalChecks.fire(a,target.x,target.y,24);float cold=10000-target.health;
        if(!(buffed>cold*1.5f))throw new AssertionError("native beam damage did not benefit: "+buffed+"/"+cold);
        for(int tick=0;tick<90;tick++){touch.invoke(null,a,target);update.invoke(null);}
        for(int tick=0;tick<179;tick++)update.invoke(null);
        near((float)multiplier.invoke(null,a,target),.5f,"reward ended before its three-second tail");
        update.invoke(null);near((float)multiplier.invoke(null,a,target),1,"expired reward persisted");
        near(StatusEffects.corroded.intervalDamage,20,"shared Corroded damage changed");
        near(StatusEffects.corroded.intervalDamageTime,15,"shared Corroded interval changed");
        near(UnitTypes.dagger.armor,0,"shared armour changed");
        Events.fire(new mindustry.game.EventType.ResetEvent());
        System.out.println("PASS: Caustic own-buildup-only 50% armour reduction, native per-hit payoff, owner isolation, three-second expiry and unchanged shared content");
    }
    static void near(float a,float b,String m){ExperimentalChecks.near(a,b,m);}
}
