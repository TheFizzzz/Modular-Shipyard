package shipyard;

import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;

/** Full native flight/collision, per-leg limits, return steering and removed-owner cleanup. */
final class InvolutionChecks{
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(128,128,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Time.setDeltaProvider(()->1f);Time.delta=1f;
        int registered=Vars.content.bullets().size;
        checkWeaponSwaps();
        for(int tier=0;tier<3;tier++){
            Unit ship=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            ship.dead=false;ship.spawnedByCore=true;ship.set(320,400);ship.rotation=0;ship.add();
            ExperimentalChecks.equip(ship,tier,"lancer");
            var readiness=ExperimentalChecks.weapons.getMethod("bladeReadiness",Unit.class,mindustry.entities.units.WeaponMount.class);
            for(var mount:ship.mounts)near((float)readiness.invoke(null,ship,mount),1f,"ready cradle missing blade");
            int launchedIndex=-1;
            for(int frame=0;frame<90 && launchedIndex<0;frame++){
                ExperimentalChecks.fire(ship,ship.x+120,ship.y,1);
                for(int i=0;i<ship.mounts.length;i++)if(ship.mounts[i].totalShots>0){launchedIndex=i;break;}
            }
            check(launchedIndex>=0,"cradle gun never launched");var launched=ship.mounts[launchedIndex];
            near((float)readiness.invoke(null,ship,launched),0f,"launched blade still seated");
            ship.isShooting=false;ship.controlWeapons(false,false);
            for(int frame=0;frame<40;frame++)for(var mount:ship.mounts)mount.weapon.update(ship,mount);
            float reforming=(float)readiness.invoke(null,ship,launched);
            check(reforming>0f && reforming<1f,"blade did not reform during reload");
            for(int frame=0;frame<50;frame++)for(var mount:ship.mounts)mount.weapon.update(ship,mount);
            near((float)readiness.invoke(null,ship,launched),1f,"blade not ready after reload");
            Groups.bullet.clear();
            var type=ship.mounts[0].weapon.bullet;
            near(type.range,198.72f,"blade outward reach");
            check(ship.mounts[0].weapon.shoot.firstShotDelay==0f,"blade retained charge delay");
            Unit target=ExperimentalChecks.enemy(UnitTypes.dagger,ship.x+80,ship.y);target.armor=0;
            Unit ally=UnitTypes.dagger.create(Team.sharded);ally.set(target);ally.add();float allyHP=ally.health;
            Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();
            Bullet blade=type.create(ship,ship.team,ship.x,ship.y,0f);float full=blade.damage;
            check(blade.type!=type && blade.type.id==type.id,"blade has no private fixed-ID collision view");
            boolean turned=false;
            for(int frame=0;frame<120 && blade.isAdded();frame++){
                tick();if(blade.x<blade.lastX)turned=true;
            }
            check(turned,"blade did not turn back");check(!blade.isAdded() && blade.time<100f,"blade expired instead of being caught");
            near(10000-target.health,full*2.25f,"blade outbound and 25% stronger return hit");
            near(ally.health,allyHP,"blade damaged an ally");target.remove();ally.remove();Groups.bullet.clear();

            Unit[] line=new Unit[3];
            for(int i=0;i<3;i++){line[i]=ExperimentalChecks.enemy(UnitTypes.dagger,ship.x+32+32*i,ship.y);line[i].armor=0;}
            Vars.state.teams.updateTeamStats();blade=type.create(ship,ship.team,ship.x,ship.y,0);
            for(int frame=0;frame<21;frame++)tick();
            near(10000-line[0].health,full,"blade first outgoing target");near(10000-line[1].health,full,"blade second outgoing target");
            near(line[2].health,10000,"blade exceeded two targets outbound");
            check(type.collides && type.collidesTiles,"blade collision cap mutated the shared template");
            for(var unit:line)unit.remove();Groups.bullet.clear();

            // Buildings consume the same two-hit budget as units, through native tile collision.
            for(int i=0;i<3;i++)Vars.world.tile(44+i*4,49).setBlock(Blocks.copperWallLarge,Team.crux,0);
            var first=Vars.world.build(44,49);var second=Vars.world.build(48,49);var third=Vars.world.build(52,49);
            ship.y=first.y;float firstHP=first.health,secondHP=second.health,thirdHP=third.health;
            blade=type.create(ship,ship.team,ship.x,ship.y,0);
            for(int frame=0;frame<21;frame++)tick();
            check(first.health<firstHP && second.health<secondHP,"blade failed native building impacts: hp="+first.health+","+second.health+","+third.health+" xy="+blade.x+","+blade.y+" added="+blade.isAdded()+" hits="+blade.collided+" origin="+blade.originX+","+blade.originY+" flags="+blade.type.collides+","+blade.type.collidesTiles);
            near(third.health,thirdHP,"blade exceeded two building targets outbound");
            first.tile.remove();second.tile.remove();third.tile.remove();Groups.bullet.clear();

            // Steering follows a moving owner and catching is tested against the entire movement segment.
            blade=type.create(ship,ship.team,ship.x,ship.y,0);
            for(int frame=0;frame<30;frame++)tick();ship.y+=80;
            boolean aimedAtOwner=false;
            for(int frame=0;frame<90 && blade.isAdded();frame++){
                tick();
                if(blade.x>ship.x+8f && blade.x<blade.lastX)near(blade.y,ship.y-80f,"blade deviated before finishing its recorded path");
                if(blade.vel.y>0 && blade.x<=ship.x+8f)aimedAtOwner=true;
            }
            check(aimedAtOwner && !blade.isAdded() && blade.time<100f,"return did not track/catch the moving ship");
            // Native mirrored mounts must produce a staggered stream, not simultaneous pairs.
            for(float rate:new float[]{1f,1.5f}){
                ship.reloadMultiplier=rate;
                for(var mount:ship.mounts){mount.reload=0;mount.side=false;mount.totalShots=0;}
                int previous=-1,lastFrame=-1,shots=0;
                for(int frame=0;frame<240;frame++){
                    int[] before=new int[ship.mounts.length];
                    for(int i=0;i<before.length;i++)before[i]=ship.mounts[i].totalShots;
                    ExperimentalChecks.fire(ship,ship.x+120,ship.y,1);
                    int fired=0;
                    for(int i=0;i<before.length;i++)if(ship.mounts[i].totalShots>before[i]){
                        fired++;shots++;
                        check(previous!=i,"same gun fired twice without alternating");
                        if(lastFrame>=0)check(frame-lastFrame>=25 && frame-lastFrame<=42,"unexpected alternating interval "+(frame-lastFrame));
                        previous=i;lastFrame=frame;
                    }
                    check(fired<=1,"both Involution guns fired together");
                }
                check(shots>=(rate==1f?5:8),"alternating cadence lost throughput");
            }
            ship.reloadMultiplier=1f;
            blade=type.create(ship,ship.team,ship.x,ship.y,0);ship.remove();tick();
            check(!blade.isAdded(),"removed/docked owner left a damaging orphan blade");
        }
        InvolutionSteeringChecks.run();
        InvolutionRecallChecks.run();
        check(Vars.content.bullets().size==registered,"blade flight/palettes registered content IDs");
        System.out.println("PASS: Involution exact retrace/small-target double hit, alternating native cadence, two-target outgoing cap, ally safety, private collision flags, moving-owner steering/catch and removed-owner cleanup, all tiers");
    }
    static void checkWeaponSwaps()throws Exception{
        var install=Vars.mods.getMod("modular-shipyard").loader.loadClass("shipyard.service.UnitLoadouts")
            .getMethod("install",Unit.class,ExperimentalChecks.tiers,ExperimentalChecks.model,ExperimentalChecks.colours);
        for(int tier=0;tier<3;tier++)for(String source:new String[]{"standard","flamethrower","railgun","superfactorial","invariant"})for(int phase=0;phase<4;phase++){
            Unit ship=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            ship.set(320,400);ship.rotation=0;ship.spawnedByCore=true;ship.add();
            ExperimentalChecks.equip(ship,tier,source);
            for(int i=0;i<ship.mounts.length;i++){ship.mounts[i].side=(phase&(1<<(i%2)))!=0;ship.mounts[i].totalShots=0;}
            install.invoke(null,ship,ExperimentalChecks.tiers.getEnumConstants()[tier],ExperimentalChecks.loadout("lancer"),ExperimentalChecks.palette);
            ExperimentalChecks.fire(ship,ship.x+120,ship.y,180);
            int shots=0;for(var mount:ship.mounts)shots+=mount.totalShots;
            check(shots>=2,"Involution stalled after swapping from "+source+" tier="+tier+" phase="+phase);
            boolean[] side=new boolean[ship.mounts.length];float[] reload=new float[ship.mounts.length];
            for(int i=0;i<ship.mounts.length;i++){side[i]=ship.mounts[i].side;reload[i]=ship.mounts[i].reload;}
            install.invoke(null,ship,ExperimentalChecks.tiers.getEnumConstants()[tier],ExperimentalChecks.loadout("lancer"),ExperimentalChecks.colours.getField("defaults").get(null));
            for(int i=0;i<ship.mounts.length;i++){
                check(ship.mounts[i].side==side[i],"cosmetic edit reset Involution alternating phase");
                near(ship.mounts[i].reload,reload[i],"cosmetic edit reset Involution reload");
            }
            ship.remove();Groups.bullet.clear();
        }
        System.out.println("PASS: Involution fires after 60 native weapon swaps across source mount counts and alternating phases");
    }
    static void tick(){Time.update();Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();Groups.bullet.updatePhysics();Groups.bullet.update();Groups.bullet.collide();}
    static void check(boolean ok,String why){ExperimentalChecks.check(ok,why);}
    static void near(float a,float b,String why){ExperimentalChecks.near(a,b,why);}
}
