package shipyard;

import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.world.blocks.defense.TargetDummy.TargetDummyBuild;

/** Real training dummy, including changing its size, armour, elevation and team during a beam. */
final class DummyChecks{
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(96,96,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));t.getn(60,48).setBlock(Blocks.targetDummy,Team.sharded,0);});
        Time.setDeltaProvider(()->1f);Time.delta=1;
        TargetDummyBuild dummy=(TargetDummyBuild)Vars.world.build(60,48);dummy.unitTeam=Team.crux;dummy.updateTile();
        Unit source=UnitTypes.alpha.create(Team.sharded);source.spawnedByCore=true;source.dead=false;source.set(dummy.x-80,dummy.y);source.rotation=0;source.add();
        ExperimentalChecks.equip(source,0,"caustic");Vars.state.teams.updateTeamStats();
        int mostHits=0;
        for(int frame=0;frame<1200;frame++){
            if(frame%120==0){dummy.dummySize=new float[]{0,1,12,40,160}[frame/120%5];dummy.unitArmor=(frame/120%3)*20;dummy.boosting=frame/120%2==0;}
            if(frame==600)dummy.team=Team.crux;
            dummy.updateTile();Vars.state.teams.updateTeamStats();ExperimentalChecks.fire(source,dummy.x,dummy.y,1);
            if(dummy.unit!=null)dummy.unit.update();
            mostHits=Math.max(mostHits,dummy.hits);
            if(frame%120==119 && dummy.dummySize>0f && (dummy.unit==null || dummy.unit.getDuration(StatusEffects.corroded)<=0))
                throw new AssertionError("dummy lost sustained contact at size "+dummy.dummySize);
        }
        if(mostHits<=0)throw new AssertionError("Caustic did not register training-dummy hits");
        if(dummy.unit==null || dummy.unit.getDuration(StatusEffects.corroded)<=0)throw new AssertionError("enemy dummy base intercepted corrosion meant for its unit");
        dummy.tile.remove();ExperimentalChecks.fire(source,source.x+100,source.y,10);
        System.out.println("PASS: live training dummy, 1200 held Caustic frames, changing size/armour/elevation and dummy removal");
    }
}
