package shipyard;

import java.lang.reflect.Array;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.game.Team;

/** Real collision paths: convergence is occlusion-aware and release snapshots retain no damage targets. */
final class CausticFeedbackChecks{
    static void run()throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var geometry=loader.loadClass("shipyard.graphics.CausticBeam");
        var traceType=loader.loadClass("shipyard.graphics.CausticBeam$Trace");
        var visuals=loader.loadClass("shipyard.graphics.CausticVisuals");
        var tracesType=Array.newInstance(traceType,0).getClass();
        var cast=geometry.getMethod("trace",Bullet.class,int.class);
        var converges=visuals.getMethod("converges",tracesType,float.class,float.class);
        var snapshot=visuals.getMethod("snapshot",tracesType,tracesType,float.class,float.class);
        Vars.logic.reset();Vars.state.rules.unitCap=1000;Vars.world.loadGenerator(80,80,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Unit owner=UnitTypes.alpha.create(Team.sharded);owner.set(200,200);owner.add();ExperimentalChecks.equip(owner,0,"caustic");
        Bullet beam=owner.mounts[0].weapon.bullet.create(owner,Team.sharded,200,200,0);beam.aimX=280;beam.aimY=200;
        Object free=Array.newInstance(traceType,3);for(int i=0;i<3;i++)Array.set(free,i,cast.invoke(null,beam,i));
        check((boolean)converges.invoke(null,free,280f,200f),"open strands do not converge");
        for(int y=23;y<=27;y++)Vars.world.tile(30,y).setBlock(Blocks.copperWall,Team.crux,0);
        Object blocked=Array.newInstance(traceType,3);for(int i=0;i<3;i++)Array.set(blocked,i,cast.invoke(null,beam,i));
        check(!(boolean)converges.invoke(null,blocked,280f,200f),"sweet spot glows behind wall");
        Object finish=snapshot.invoke(null,free,blocked,280f,200f);
        check(!(boolean)finish.getClass().getMethod("focused").invoke(finish),"release glow ignores obstruction");
        Object copied=finish.getClass().getMethod("to").invoke(finish);
        Object originalTrace=Array.get(blocked,0),copiedTrace=Array.get(copied,0);
        check(traceType.getMethod("target").invoke(originalTrace)!=null,"fixture has no collision target");
        check(traceType.getMethod("target").invoke(copiedTrace)==null,"release effect retained a live damage target");
        float[] original=(float[])traceType.getMethod("points").invoke(originalTrace);
        float[] clone=(float[])traceType.getMethod("points").invoke(copiedTrace);float x=clone[0];original[0]+=100;
        check(clone[0]==x,"live beam edits rewrote released trail");
        beam.remove();owner.remove();
        System.out.println("PASS: Caustic clear/occluded convergence and detached cosmetic release snapshots with no live hit targets");
    }
    static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
