package shipyard;

import arc.Events;
import arc.graphics.Color;
import arc.util.Time;
import java.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.type.UnitType;
import mindustry.world.Block;

/** Actual native firing, immediate ray hits, private palettes and weapon save/respawn state. */
final class WeaponChecks{
    static void run(Block block) throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var model=loader.loadClass("shipyard.model.ShipLoadout");
        var kindClass=loader.loadClass("shipyard.model.ShipWeapon");
        var coloursClass=loader.loadClass("shipyard.model.ShipCosmetics");
        var variants=loader.loadClass("shipyard.content.ShipVariants");
        var service=loader.loadClass("shipyard.service.LoadoutService");
        var current=variants.getMethod("current",UnitType.class);
        var choice=model.getMethod("weapon");
        var with=model.getMethod("withWeapon",kindClass);
        var parse=model.getMethod("parse",String.class);
        var apply=service.getMethod("apply",Player.class,int.class,int.class,model,coloursClass);
        Object neutral=model.getField("defaults").get(null);
        Object palette=coloursClass.getMethod("parse",String.class).invoke(null,"F7E2E1,FF80AB,30EE70");
        Object[] kinds=kindClass.getEnumConstants();
        check(choice.invoke(parse.invoke(null,"0,0,0,0,0")).equals(kinds[0]),"old profile did not default to Standard");
        try{parse.invoke(null,"0,0,0,0,0,unknown");throw new AssertionError("unknown weapon accepted");}catch(java.lang.reflect.InvocationTargetException expected){}
        Vars.logic.reset();
        Vars.world.loadGenerator(80,80,tiles -> {
            tiles.fill();tiles.eachTile(t -> t.setFloor(Blocks.stone.asFloor()));
            tiles.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);
            tiles.getn(65,65).setBlock(Blocks.coreShard,Team.crux,0);
            tiles.getn(40,40).setBlock(block,Team.sharded,0);
            tiles.getn(42,40).setBlock(Blocks.powerSource,Team.sharded,0);
        });
        Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;
        Vars.state.rules.unitCap=1000;Time.delta=1f;
        Building yard=Vars.world.build(40,40);yard.power.graph.reflow(yard);yard.power.graph.update();
        int bullets=Vars.content.bullets().size, units=Vars.content.units().size;
        Player player=LoadoutChecks.player("weapon-test-player");
        Unit last=null;
        for(UnitType base : new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}){
            Unit ship=base.create(Team.sharded);ship.spawnedByCore=true;ship.set(yard);ship.rotation=0;ship.add();player.unit(ship);
            float hp=ship.health=base.health*.5f;
            var vanilla=base.weapons.first().bullet;
            for(int index=1;index<7;index++){
                Object selected=with.invoke(neutral,kinds[index]);
                check((int)model.getMethod("points").invoke(selected)==(int)kindClass.getMethod("cost").invoke(kinds[index]),"weapon selection cost missing");
                check(parse.invoke(null,model.getMethod("encode").invoke(selected)).equals(selected),"weapon profile round trip failed");
                check(((String)DockTest.apply(apply,player,yard.pos(),ship.id,selected,palette)).contains("applied"),"weapon Apply failed");
                check(choice.invoke(current.invoke(null,ship.type)).equals(kinds[index]),"selected weapon missing");
                near(ship.health,hp,"weapon swap changed HP");
                check(base.weapons.first().bullet==vanilla,"shared native gun changed");
                var mount=ship.mounts[0];
                if(index==2)mount.weapon.alternate=false; // This fixture isolates one gun; full cadence has separate checks.
                check(mount.weapon==ship.type.weapons.first() && mount.weapon!=base.weapons.first(),"private gun not attached");
                check(mount.weapon.bullet!=vanilla,"native projectile retained");
                var tierClass=loader.loadClass("shipyard.model.ShipTier");
                Object tier=tierClass.getMethod("of",UnitType.class).invoke(null,base);
                float previewDamage=(float)loader.loadClass("shipyard.content.ShipWeapons").getMethod("damage",tierClass,kindClass).invoke(null,tier,kinds[index]);
                near(mount.weapon.bullet.damage,previewDamage,"tier damage preview differs from fired projectile");
                check(ship.type.targetAir && mount.weapon.bullet.collidesAir && mount.weapon.bullet.collidesGround,"weapon cannot target air and ground");
                near(mount.weapon.bullet.hitColor.g,Color.valueOf("30EE70").g,"engine palette missing before firing");
                if(index<=4)check(mount.weapon.bullet.pierceCap==(index==3 || index==4?1:2),"formation pierce cap missing");
                if(index==1){
                    check(mount.weapon.bullet.pierce && mount.weapon.bullet.collidesAir,"flame targeting/piercing wrong");
                    check(mount.weapon.bullet.status==StatusEffects.burning,"flame lost Burning");
                }else if(index==2){
                    check(mount.weapon.bullet instanceof BasicBulletType,"not a physical boomerang");
                    check(mount.weapon.shoot.firstShotDelay==0f,"boomerang retained laser charge");
                }else if(index==3){
                    check(mount.weapon.bullet instanceof ShrapnelBulletType && mount.weapon.shoot.shots==2,"not a two-ray Fuse shotgun");
                }else if(index==4){
                    check(mount.weapon.bullet instanceof RailBulletType && mount.weapon.shoot.firstShotDelay==60f,"railgun charge/type missing");
                    check(mount.weapon.bullet.range>250f,"railgun reach is not longer than Lancer");
                }else if(index==5){
                    check(mount.weapon.bullet.status==StatusEffects.shocked,"chain lacks Shocked");
                }else{
                    check(mount.weapon.bullet instanceof MissileBulletType && mount.weapon.bullet.homingPower>0,"missile lacks native homing");
                }
                Unit target=UnitTypes.dagger.create(Team.crux);target.set(ship.x+40,ship.y-2.75f);target.add();target.health=target.maxHealth=10000;
                Vars.state.teams.updateTeamStats();
                float before=target.health;
                // Native delayed charge is exercised on desktop. Remove delay on this private gun only to inspect immediate collision.
                float delay=mount.weapon.shoot.firstShotDelay;mount.weapon.shoot.firstShotDelay=0;
                mount.reload=0;mount.shoot=true;mount.aimX=target.x;mount.aimY=target.y;ship.isShooting=true;
                long prior=Groups.bullet.count(b -> b.owner==ship);
                mount.weapon.update(ship,mount);
                long count=Groups.bullet.count(b -> b.owner==ship)-prior;
                check(count==(index==3?2:1),"wrong native shot count for "+kinds[index]+": "+count);
                Bullet shot=Groups.bullet.find(b -> b.owner==ship && b.type.id==mount.weapon.bullet.id);
                check(shot!=null,"missing native shot");
                if(index==6 || index==2){
                    check(shot.collides(target),"missile collision rejected ground target");
                    shot.collision(target,target.x,target.y);
                    check(target.health<before,"physical shot hit did no ground damage");
                    if(index==2)shot.remove();
                }else if(index==1){
                    shot.type.hitEntity(shot,target,target.health);
                    check(target.hasEffect(StatusEffects.burning),"actual flame hit did not burn");
                }else check(target.health<before,"instant ray did no damage");
                mount.weapon.shoot.firstShotDelay=delay;mount.shoot=false;ship.isShooting=false;
                target.remove();
                Unit flying=UnitTypes.flare.create(Team.crux);flying.elevation=1f;
                flying.set(ship.x+35,ship.y-2.75f);flying.add();flying.health=flying.maxHealth=10000;
                Vars.state.teams.updateTeamStats();
                check(flying.isFlying(),"test target is not flying");
                mount.weapon.shoot.firstShotDelay=0;
                mount.reload=0;mount.shoot=true;mount.aimX=flying.x;mount.aimY=flying.y;ship.isShooting=true;
                mount.weapon.update(ship,mount);
                if(index==2){
                    Bullet blade=Groups.bullet.find(b -> b.owner==ship && b.type.id==mount.weapon.bullet.id);
                    check(blade!=null && blade.collides(flying),"boomerang rejected air");
                    blade.collision(flying,flying.x,flying.y);blade.remove();
                }else if(index==6){
                    Bullet missile=Groups.bullet.find(b -> b.owner==ship && b.type==mount.weapon.bullet && b.collides(flying));
                    check(missile!=null,"missing air-capable missile");
                    flying.set(ship.x+35,ship.y+25);
                    Vars.state.teams.updateTeamStats();
                    float oldAngle=missile.rotation();
                    missile.time=missile.type.homingDelay;
                    for(int tick=0;tick<8;tick++)missile.type.updateHoming(missile);
                    check(arc.math.Angles.angleDist(oldAngle,missile.rotation())>2f,"native missile homing did not turn toward flying target");
                    missile.collision(flying,flying.x,flying.y);
                }else if(index==1){
                    Bullet flame=Groups.bullet.find(b -> b.owner==ship && b.type==mount.weapon.bullet && b.collides(flying));
                    check(flame!=null,"native flame collision rejects flying target");
                    flame.collision(flying,flying.x,flying.y);
                    check(flying.hasEffect(StatusEffects.burning),"flying flame hit did not burn");
                }
                check(flying.health<10000,"weapon did no damage to flying target: "+kinds[index]);
                mount.weapon.shoot.firstShotDelay=delay;mount.shoot=false;ship.isShooting=false;
                flying.remove();
                if(index==2 || index==3 || index==4 || index==5){
                    // Native first hit, plus a mixed ground/air cluster beyond it.
                    Unit[] cluster=new Unit[5];
                    for(int j=0;j<cluster.length;j++){
                        cluster[j]=(j%2==0?UnitTypes.dagger:UnitTypes.flare).create(Team.crux);
                        if(j%2==1)cluster[j].elevation=1f;
                        cluster[j].set(ship.x+35f+j*28f,ship.y-2.75f+(index==5 && j>0?20f:0f));
                        cluster[j].add();cluster[j].health=cluster[j].maxHealth=1000f;
                    }
                    Unit friend=UnitTypes.dagger.create(Team.sharded);friend.set(ship.x+55,ship.y);friend.add();float friendlyHP=friend.health;
                    Vars.state.teams.updateTeamStats();
                    mount.weapon.shoot.firstShotDelay=0;
                    mount.reload=0;mount.shoot=true;ship.isShooting=true;mount.aimX=ship.x+150;mount.aimY=ship.y-2.75f;
                    if(index==4)for(Unit enemy:cluster)enemy.health=enemy.maxHealth=100f;
                    if(index==2)Groups.bullet.clear();
                    // Isolate the pierce cap with a scripted straight blade; aimed curved throws have their own suite.
                    if(index==2)mount.weapon.bullet.create(ship,ship.team,ship.x,ship.y-2.75f,0f);
                    else mount.weapon.update(ship,mount);
                    if(index==2)for(int frame=0;frame<16;frame++){
                        Time.update();Groups.unit.updatePhysics();Groups.bullet.updatePhysics();Groups.bullet.update();Groups.bullet.collide();
                    }
                    if(index==2 || index==3){
                        check(cluster[0].health<1000,"capped ray missed first target");
                        if(index==2)check(cluster[1].health<1000,"Lancer lost its second target");
                        near(cluster[index==3?1:2].health,1000f,"ray exceeded its pierce cap");
                    }else if(index==4){
                        near(cluster[2].health,100f,"railgun hit a third target despite its one-target cap");
                        check(cluster[0].health<100 && cluster[1].health==100,"single-target railgun pierced a second target");
                    }else{
                        float factor=base==UnitTypes.alpha?.5f:base==UnitTypes.beta?1.1f:3f;
                        near(1000-cluster[0].health,36f*factor,"chain initial damage");
                        near(1000-cluster[1].health,27f*factor,"chain first jump damage");
                        near(1000-cluster[2].health,20.25f*factor,"chain second jump damage");
                        near(1000-cluster[3].health,15.1875f*factor,"chain third jump damage");
                        near(cluster[4].health,1000f,"chain exceeded jump limit");
                    }
                    near(friend.health,friendlyHP,"advanced weapon damaged an ally");
                    for(Unit enemy:cluster)enemy.remove();friend.remove();
                    mount.weapon.shoot.firstShotDelay=delay;mount.shoot=false;ship.isShooting=false;
                }
                // Reattaching a neutral native snapshot must restore private guns without changing cooldown.
                mount.reload=7f;
                ship.setType(Vars.content.unit("modular-shipyard-ship-"+base.name+"-hull"));
                Events.fire(Trigger.update);
                check(choice.invoke(current.invoke(null,ship.type)).equals(kinds[index]),"snapshot lost weapon");
                near(ship.mounts[0].reload,7f,"snapshot changed cooldown");
                near(ship.health,hp,"snapshot changed HP");
            }
            last=ship;
        }
        check(Vars.content.bullets().size==bullets && Vars.content.units().size==units,"weapon/palette edits registered extra content");
        int id=last.id;float hp=last.health;
        var file=Vars.dataDirectory.child("weapons-roundtrip.msav");SaveIO.write(file);SaveIO.load(file);
        last=Groups.unit.getByID(id);
        check(choice.invoke(current.invoke(null,last.type)).equals(kinds[6]),"disk save lost missiles");
        near(last.health,hp,"disk weapon restore changed HP");
        check(last.mounts[0].weapon.bullet instanceof MissileBulletType,"disk restore did not bind native missiles");
        var bytes=new ByteArrayOutputStream();SaveIO.getVersion().writeCustomChunks(new DataOutputStream(bytes),true);
        Events.fire(new ResetEvent());last.setType(Vars.content.unit("modular-shipyard-ship-gamma-hull"));
        SaveIO.getVersion().readCustomChunks(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        check(choice.invoke(current.invoke(null,last.type)).equals(kinds[6]),"join chunk lost weapon");
        service.getMethod("init").invoke(null);
        Player reconnect=LoadoutChecks.player("weapon-test-player");
        for(int index=0;index<3;index++){
            UnitType base=new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[index];
            Unit fresh=base.create(Team.sharded);fresh.spawnedByCore=true;fresh.set(Vars.world.build(40,40));fresh.add();reconnect.unit(fresh);
            service.getMethod("restore",Player.class).invoke(null,reconnect);
            check(choice.invoke(current.invoke(null,fresh.type)).equals(kinds[6]),"per-tier reconnect lost weapon");
        }
        System.out.println("PASS: six weapon choices on all tiers, native flame Burning/returning blade/two-ray spread/rail pierce/three chain jumps/native missile homing, pre-fire palettes, air/ground damage, weapon selection costs, private guns, snapshot cooldowns, disk/join/respawn/reconnect and fixed content counts");
    }
    private static void near(float a,float b,String message){check(Math.abs(a-b)<.01f,message+": "+a+" vs "+b);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
