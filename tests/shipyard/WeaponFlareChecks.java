package shipyard;

import arc.audio.Sound;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;

/** Native audio dispatch, expiry versus impact, and per-weapon volume isolation. */
final class WeaponFlareChecks{
    static class Probe extends Sound{
        int count;float volume;
        @Override public int at(float x,float y,float pitch,float value){count++;volume=value;return 0;}
    }
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(80,80,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Time.setDeltaProvider(()->1f);Time.delta=1;
        Unit owner=UnitTypes.alpha.create(Team.sharded);owner.set(200,200);owner.add();ExperimentalChecks.equip(owner,0,"caustic");
        ExperimentalChecks.near(owner.mounts[0].weapon.shootSoundVolume,.8f,"Caustic event volume");
        ExperimentalChecks.equip(owner,0,"missiles");var main=owner.mounts[0].weapon.bullet;var small=main.fragBullet;
        ExperimentalChecks.near(owner.mounts[0].weapon.shootSoundVolume,1f,"other weapon volume changed");
        if(small.hitSound!=Sounds.explosionPlasmaSmall)throw new AssertionError("missing bomblet detonation cue");
        var original=small.hitSound;Probe probe=new Probe();small.hitSound=probe;
        try{
            Unit enemy=ExperimentalChecks.enemy(UnitTypes.dagger,240,200);Vars.state.teams.updateTeamStats();
            Bullet impact=small.create(owner,Team.sharded,200,200,0);impact.collision(enemy,enemy.x,enemy.y);
            if(probe.count!=1)throw new AssertionError("impact sound duplicated/missing: "+probe.count);
            ExperimentalChecks.near(probe.volume,1f,"detonation volume");enemy.remove();
            Bullet expired=small.create(owner,Team.sharded,200,200,0);expired.time=expired.lifetime;expired.remove();
            if(probe.count!=2)throw new AssertionError("expiry sound duplicated/missing: "+probe.count);
            Bullet absorbed=small.create(owner,Team.sharded,200,200,0);absorbed.absorb();
            if(probe.count!=2)throw new AssertionError("absorption played a detonation");
        }finally{small.hitSound=original;}
        ExperimentalChecks.equip(owner,0,"asymptote");var orb=owner.mounts[0].weapon.bullet;
        if(orb.hitSound!=Sounds.explosionPlasmaSmall)throw new AssertionError("missing Asymptote detonation cue");
        var originalOrbSound=orb.hitSound;Probe orbProbe=new Probe();orb.hitSound=orbProbe;
        try{
            Unit enemy=ExperimentalChecks.enemy(UnitTypes.dagger,240,200);Vars.state.teams.updateTeamStats();
            Bullet impact=orb.create(owner,Team.sharded,200,200,0);impact.collision(enemy,enemy.x,enemy.y);
            if(orbProbe.count!=1)throw new AssertionError("Asymptote impact cue duplicated/missing");enemy.remove();
            Bullet expired=orb.create(owner,Team.sharded,200,200,0);expired.time=expired.lifetime;expired.remove();
            if(orbProbe.count!=2)throw new AssertionError("Asymptote timeout cue duplicated/missing");
            Bullet absorbed=orb.create(owner,Team.sharded,200,200,0);absorbed.absorb();
            if(orbProbe.count!=2)throw new AssertionError("absorbed Asymptote played detonation");
            ExperimentalChecks.near(orbProbe.volume,1f,"Asymptote detonation volume");
        }finally{orb.hitSound=originalOrbSound;}
        System.out.println("PASS: Caustic 80% event volume, other-weapon isolation, one bomblet/Asymptote cue per impact/timeout and quiet absorption");
    }
}
