package shipyard;

import arc.Events;
import arc.graphics.Color;
import arc.util.Time;
import java.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.type.UnitType;
import mindustry.world.Block;

/** Native mounted beams, collision damage, distance scaling and loadout lifecycle. */
final class ExperimentalChecks{
    static Class<?> model,kinds,tiers,colours,variants,weapons,exposure;
    static Object neutral,palette;static java.lang.reflect.Method get,bind;
    static void run(Block block)throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        model=loader.loadClass("shipyard.model.ShipLoadout");kinds=loader.loadClass("shipyard.model.ShipWeapon");
        tiers=loader.loadClass("shipyard.model.ShipTier");colours=loader.loadClass("shipyard.model.ShipCosmetics");
        variants=loader.loadClass("shipyard.content.ShipVariants");weapons=loader.loadClass("shipyard.content.ShipWeapons");
        exposure=loader.loadClass("shipyard.service.CausticExposure");
        neutral=model.getField("defaults").get(null);palette=colours.getMethod("parse",String.class).invoke(null,"F7E2E1,FF80AB,30EE70");
        get=variants.getMethod("get",tiers,model,colours);bind=weapons.getMethod("bind",Unit.class);
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(128,128,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));
            t.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);t.getn(115,115).setBlock(Blocks.coreShard,Team.crux,0);
            t.getn(64,64).setBlock(block,Team.sharded,0);t.getn(66,64).setBlock(Blocks.powerSource,Team.sharded,0);});
        Time.setDeltaProvider(()->1f);Time.delta=1f;Vars.state.set(mindustry.core.GameState.State.playing);
        int count=Vars.content.bullets().size;Unit last=null;
        for(int tier=0;tier<3;tier++){
            Unit ship=new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            ship.set(512,480);ship.rotation=0;ship.spawnedByCore=true;ship.add();equip(ship,tier,"caustic");
            near(ship.mounts[0].weapon.range(),124.8f,"Caustic range did not increase by 20 percent");
            var nativeGun=new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].weapons.first();
            check(ship.mounts[0].weapon!=nativeGun,"shared gun changed");
            check(ship.mounts[0].weapon.continuous && ship.mounts[0].weapon.alwaysContinuous,"Caustic not held fire");
            near(ship.mounts[0].weapon.bullet.hitColor.g,Color.valueOf("30EE70").g,"Caustic colour");
            Unit focal=enemy(UnitTypes.dagger,ship.x+64,ship.y);Vars.state.teams.updateTeamStats();
            fire(ship,focal.x,focal.y,24);
            float focused=10000-focal.health;check(focused>0,"Caustic focal point missed");
            for(var m:ship.mounts){check(m.bullet!=null && m.bullet.isAdded(),"held beam expired");near(m.bullet.aimX,focal.x,"beams do not share cursor X");near(m.bullet.aimY,focal.y,"beams do not share cursor Y");}
            near((float)exposure.getMethod("progress",Unit.class,Unit.class).invoke(null,ship,focal),24f,"overlapping strands accelerated exposure");
            check(!focal.hasEffect(StatusEffects.corroded),"short contact immediately corroded target");
            // Contact away from the convergence point has the same low direct damage.
            focal.health=10000;fire(ship,ship.x+100,ship.y,24);float unfocused=10000-focal.health;
            near(focused,unfocused,"obsolete focal damage bonus remains");
            fire(ship,focal.x,focal.y,41);check(!focal.hasEffect(StatusEffects.corroded),"Corroded applied before 1.5 seconds");
            fire(ship,focal.x,focal.y,1);check(focal.hasEffect(StatusEffects.corroded),"sustained native strands failed to corrode");
            near(focal.getDuration(StatusEffects.corroded),180f,"wrong Corroded duration");
            focal.remove();
            Unit flying=enemy(UnitTypes.flare,ship.x+64,ship.y);Vars.state.teams.updateTeamStats();
            fire(ship,flying.x,flying.y,24);check(flying.health<10000,"Caustic cannot hit air");flying.remove();
            Unit friend=UnitTypes.dagger.create(Team.sharded);friend.set(ship.x+64,ship.y);friend.add();float hp=friend.health;
            fire(ship,friend.x,friend.y,24);near(friend.health,hp,"Caustic friendly damage");friend.remove();
            // Native laser-absorbing walls must block both the beam and the focal bonus.
            Unit screened=enemy(UnitTypes.dagger,ship.x+64,ship.y);
            var wallTile=Vars.world.tileWorld(ship.x+32,ship.y);
            for(int offset=-1;offset<=1;offset++)Vars.world.tile(wallTile.x,wallTile.y+offset).setBlock(Blocks.plastaniumWall,Team.crux,0);
            Vars.state.teams.updateTeamStats();fire(ship,screened.x,screened.y,24);
            near(screened.health,10000,"strand damage bypassed absorbing wall");
            near((float)exposure.getMethod("progress",Unit.class,Unit.class).invoke(null,ship,screened),0,"corrosion bypassed wall");
            for(int offset=-1;offset<=1;offset++)Vars.world.tile(wallTile.x,wallTile.y+offset).remove();screened.remove();
            // Rate changes affect continuous tick throughput; both normal and boosted trials reach steady state.
            Unit rateTarget=enemy(UnitTypes.dagger,ship.x+64,ship.y);Vars.state.teams.updateTeamStats();
            ship.reloadMultiplier=1f;fire(ship,rateTarget.x,rateTarget.y,60);float normal=10000-rateTarget.health;
            rateTarget.health=10000;ship.reloadMultiplier=1.5f;fire(ship,rateTarget.x,rateTarget.y,60);float faster=10000-rateTarget.health;
            check(faster>normal*1.25f,"Fire Rate ignored by continuous beams");ship.reloadMultiplier=1f;rateTarget.remove();
            Bullet held=ship.mounts[0].bullet;ship.isShooting=false;for(var m:ship.mounts){m.shoot=false;m.weapon.update(ship,m);}
            check(!held.isAdded(),"release left a damaging beam");
            fire(ship,ship.x+64,ship.y,2);held=ship.mounts[0].bullet;check(held!=null,"beam did not restart");
            equip(ship,tier,"asymptote");if(held.isAdded())held.type.update(held);check(!held.isAdded(),"weapon swap left ghost beam");
            var shotType=ship.mounts[0].weapon.bullet;float full=shotType.damage;
            Bullet orb=shotType.create(ship,Team.sharded,ship.x,ship.y,0f);
            near(orb.damage,full*.2f,"orb starts too strong");float initial=orb.vel.len();
            near(initial,.25f,"orb launch is not slow");
            orb.time=17;orb.type.update(orb);near(orb.vel.len(),initial,"orb accelerated before launch phase ended");
            orb.time=24;orb.type.update(orb);check(orb.vel.len()>initial && orb.vel.len()<12f,"orb acceleration not bounded");
            // Age alone gives speed, not damage. Distance is measured from launch, regardless of owner position.
            near(orb.damage,full*.2f,"stationary old orb gained damage");
            orb.time=30;orb.type.update(orb);near(orb.vel.len(),12f,"orb did not complete its sharp ramp");
            orb.set(orb.originX+200f,orb.originY);orb.type.update(orb);near(orb.damage,full,"long-range orb did not mature");
            ship.x-=20;orb.type.update(orb);near(orb.damage,full,"owner movement changed in-flight damage");ship.x+=20;
            orb.time=10000;orb.type.update(orb);check(orb.vel.len()<=12.001f,"orb exceeded speed cap");
            near(shotType.damage,full,"per-shot scaling mutated template");
            near(shotType.hitColor.g,Color.valueOf("30EE70").g,"orb engine colour");
            for(UnitType targetType:new UnitType[]{UnitTypes.dagger,UnitTypes.flare}){
                Unit target=enemy(targetType,orb.x,orb.y);float before=target.health;
                Bullet hit=shotType.create(ship,Team.sharded,ship.x,ship.y,0f);hit.set(hit.originX+200,hit.originY);hit.type.update(hit);
                check(hit.collides(target),"orb target rejected");hit.collision(target,target.x,target.y);
                check(target.health<before,"orb did no ground/air damage");target.remove();
            }
            orb.remove();
            Unit ground=enemy(UnitTypes.dagger,ship.x+200,ship.y),air=enemy(UnitTypes.flare,ship.x+212,ship.y);
            Unit ally=UnitTypes.dagger.create(Team.sharded);ally.set(ship.x+205,ship.y);ally.dead=false;ally.add();float alliedHP=ally.health;
            Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();
            Bullet blast=shotType.create(ship,Team.sharded,ship.x,ship.y,0);blast.set(blast.originX+200,blast.originY);blast.type.update(blast);
            blast.collision(ground,ground.x,ground.y);
            check(ground.health<10000 && air.health<10000,"Asymptote blast missed ground/air cluster");near(ally.health,alliedHP,"blast damaged ally");
            near(shotType.splashDamageRadius,32f,"wrong Asymptote AOE radius");
            ground.remove();air.remove();ally.remove();
            for(boolean variable:new boolean[]{false,true}){
                Time.delta=1f;
                Bullet end=shotType.create(ship,Team.sharded,ship.x,ship.y,0);
                for(int tick=0;tick<85 && end.isAdded();tick++){
                    Time.delta=variable?(tick%2==0?.5f:3f):1f;
                    Time.update();Groups.bullet.updatePhysics();Groups.bullet.update();
                    check(end.dst(end.originX,end.originY)<=240.01f,"orb exceeded physical range");
                }
                near(end.dst(end.originX,end.originY),240f,"orb stopped before 30 tiles variable="+variable+" time="+end.time+" life="+end.lifetime+" speed="+end.vel.len());
                check(!end.isAdded(),"orb lingered at range limit");
                Time.setDeltaProvider(()->1f);Time.delta=1f;
            }
            float closeDamage=travelHit(ship,20f),farDamage=travelHit(ship,220f);
            check(farDamage>closeDamage*2f,"actual travel did not strengthen orb");
            check(farDamage>=full,"natural long-range collision lost charged damage");
            System.out.println("EXPERIMENTAL "+new String[]{"Alpha","Beta","Gamma"}[tier]+": Caustic focused/off-focus (24 ticks)="+focused+"/"+unfocused+", Asymptote close/far hit="+closeDamage+"/"+farDamage);
            near(shotType.range,240f,"orb reach is not 30 tiles");
            last=ship;
        }
        check(Vars.content.bullets().size==count,"palette/loadout changes bloated content");
        // Exercise the existing native custom-chunk codec with both appended stable weapon tokens.
        for(String token:new String[]{"caustic","asymptote","superfactorial","lancer"}){
            equip(last,2,token);
            // Use the authority's loadout registry rather than just a direct view for persistence.
            var owner=LoadoutChecks.player("experimental-"+token);owner.unit(last);last.set(Vars.world.build(64,64));
            var yard=Vars.world.build(64,64);yard.power.graph.reflow(yard);yard.power.graph.update();
            var apply=loader.loadClass("shipyard.service.LoadoutService").getMethod("apply",Player.class,int.class,int.class,model,colours);
            Object value=loadout(token);
            check(((String)DockTest.apply(apply,owner,yard.pos(),last.id,value,palette)).contains("applied"),"new weapon Apply rejected");
            int id=last.id;var file=Vars.dataDirectory.child("experimental-"+token+".msav");SaveIO.write(file);SaveIO.load(file);last=Groups.unit.getByID(id);
            check(last!=null && current(last).equals(token),"disk save lost "+token);
            var bytes=new ByteArrayOutputStream();SaveIO.getVersion().writeCustomChunks(new DataOutputStream(bytes),true);
            Events.fire(new ResetEvent());last.setType(Vars.content.unit("modular-shipyard-ship-gamma-hull"));
            SaveIO.getVersion().readCustomChunks(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            check(current(last).equals(token),"join chunk lost "+token);
        }
        System.out.println("PASS: Caustic wavy contact/deduped exposure/1.5s corrosion/air-ground/ally/rate/release/swap, Asymptote bounded acceleration/distance-only damage/palette/air-ground, all tiers, fixed content counts and disk/join loadouts");
    }
    static float travelHit(Unit ship,float distance){
        Unit target=enemy(UnitTypes.dagger,ship.x+distance,ship.y);Vars.state.teams.updateTeamStats();
        Bullet orb=ship.mounts[0].weapon.bullet.create(ship,Team.sharded,ship.x,ship.y,0);
        for(int tick=0;tick<90 && orb.isAdded();tick++){
            Time.update();Groups.unit.updatePhysics();Groups.bullet.updatePhysics();Groups.bullet.update();Groups.bullet.collide();
        }
        float dealt=10000-target.health;check(dealt>0,"natural orb missed "+distance+"-unit target");target.remove();return dealt;
    }
    static Object loadout(String token)throws Exception{return model.getMethod("parse",String.class).invoke(null,"0,0,0,0,0,"+token);}
    static void equip(Unit ship,int tier,String token)throws Exception{ship.setType((UnitType)get.invoke(null,tiers.getEnumConstants()[tier],loadout(token),palette));bind.invoke(null,ship);}
    static String current(Unit ship)throws Exception{return model.getMethod("weapon").invoke(variants.getMethod("current",UnitType.class).invoke(null,ship.type)).toString();}
    static Unit enemy(UnitType type,float x,float y){Unit u=type.create(Team.crux);u.set(x,y);if(type.flying)u.elevation=1f;u.add();u.health=u.maxHealth=10000;return u;}
    static void fire(Unit ship,float x,float y,int ticks){
        for(int tick=0;tick<ticks;tick++){
            Time.update();ship.aim(x,y);ship.isShooting=true;
            for(WeaponMount m:ship.mounts){m.aimX=x;m.aimY=y;m.shoot=true;m.weapon.update(ship,m);}
            Groups.unit.updatePhysics();Groups.bullet.updatePhysics();Groups.bullet.update();Groups.bullet.collide();
            try{exposure.getMethod("update").invoke(null);}catch(Exception e){throw new RuntimeException(e);}
        }
    }
    static void near(float a,float b,String why){check(Math.abs(a-b)<.02f,why+": "+a+" vs "+b);}
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
}
