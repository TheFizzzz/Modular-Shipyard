package shipyard;

import arc.graphics.Color;
import arc.math.Angles;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;

/** Actual timeout, absorption and collision paths, with private tier/palette fragment adapters. */
final class ConvergeChecks{
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(80,80,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Time.setDeltaProvider(()->1f);Time.delta=1;
        int registered=Vars.content.bullets().size;
        for(int tier=0;tier<3;tier++){
            Groups.bullet.clear();
            Unit owner=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            owner.set(200,200);owner.add();ExperimentalChecks.equip(owner,tier,"missiles");
            var main=owner.mounts[0].weapon.bullet;float factor=new float[]{.5f,1.1f,3f}[tier];
            near(main.damage,48*factor,"main damage");near(main.splashDamage,27*factor,"main splash");near(main.lifetime,40,"main life");
            near(main.fragBullet.damage,8*factor,"child tier damage");near(main.fragBullet.splashDamage,4*factor,"child splash");
            near(main.fragBullet.hitColor.g,Color.valueOf("30EE70").g,"child palette");
            Bullet missed=main.create(owner,Team.sharded,200,200,0);
            for(int tick=0;tick<41;tick++){Time.update();Groups.bullet.update();}
            check(!missed.isAdded(),"main did not expire");check(Groups.bullet.size()==3,"timeout did not produce exactly three bomblets");
            Bullet child=Groups.bullet.first();check(child.type==main.fragBullet && child.owner==owner && child.team==owner.team,"child ownership/type");
            Unit target=ExperimentalChecks.enemy(UnitTypes.flare,child.x+40,child.y+32);Vars.state.teams.updateTeamStats();
            float angle=child.rotation();child.time=child.type.homingDelay;
            for(int i=0;i<10;i++)child.type.updateHoming(child);
            check(Angles.angleDist(angle,child.rotation())>2,"child homing did not turn");
            check(child.collides(target),"child rejected air");float before=target.health;child.collision(target,target.x,target.y);check(target.health<before,"child missed air damage");target.remove();
            Groups.bullet.clear();
            for(var type:new mindustry.type.UnitType[]{UnitTypes.dagger,UnitTypes.flare}){
                Unit enemy=ExperimentalChecks.enemy(type,240,200);Vars.state.teams.updateTeamStats();
                Bullet impact=main.create(owner,Team.sharded,200,200,0);impact.collision(enemy,enemy.x,enemy.y);
                check(Groups.bullet.count(b->b.type==main.fragBullet)==0,"direct impact also split");enemy.remove();Groups.bullet.clear();
            }
            Bullet absorbed=main.create(owner,Team.sharded,200,200,0);absorbed.time=absorbed.lifetime;absorbed.absorb();check(Groups.bullet.isEmpty(),"absorbed missile split");
            Bullet intercepted=main.create(owner,Team.sharded,200,200,0);intercepted.remove();check(Groups.bullet.isEmpty(),"early interception split");
            Bullet small=main.fragBullet.create(owner,Team.sharded,200,200,0);small.time=small.lifetime;small.remove();check(Groups.bullet.isEmpty(),"bomblet recursively split");
            owner.remove();
        }
        check(Vars.content.bullets().size==registered,"fragment palettes registered new content");
        System.out.println("PASS: Converge native expiry splits three owned/tinted/tiered homing bomblets; hits/absorption/interception do not split; air damage and nonrecursive expiry");
    }
    static void near(float a,float b,String message){ExperimentalChecks.near(a,b,message);}
    static void check(boolean ok,String message){ExperimentalChecks.check(ok,message);}
}
