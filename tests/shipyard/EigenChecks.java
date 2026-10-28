package shipyard;

import arc.Events;
import arc.struct.IntSet;
import arc.util.Time;
import java.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;

/** Native untargetable energy drones, bounded ownership, contact damage, recall, replacement and save codecs. */
final class EigenChecks{
    static Class<?> service,content;static java.lang.reflect.Method update,drones,isDrone;
    static void run()throws Exception{
        var deploy=Sounds.shootPulsar;var contact=Sounds.shootSap;var broken=Sounds.explosionPlasmaSmall;
        var d=new WeaponFlareChecks.Probe();var c=new WeaponFlareChecks.Probe();var b=new WeaponFlareChecks.Probe();
        Sounds.shootPulsar=d;Sounds.shootSap=c;Sounds.explosionPlasmaSmall=b;
        try{runNative();check(d.count>0 && c.count>0 && b.count>0,"missing deployment/contact/break audio");near(d.volume,1f,"deployment volume");near(c.volume,1f,"contact volume");near(b.volume,1f,"break volume");}
        finally{Sounds.shootPulsar=deploy;Sounds.shootSap=contact;Sounds.explosionPlasmaSmall=broken;}
    }
    static void runNative()throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;service=loader.loadClass("shipyard.service.EigenService");content=loader.loadClass("shipyard.content.EigenContent");
        update=service.getMethod("update");drones=service.getMethod("drones",Unit.class);isDrone=content.getMethod("isDrone",Unit.class);
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=0;Vars.state.rules.waves=false;Vars.state.rules.possessionAllowed=true;
        Vars.world.loadGenerator(128,128,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));t.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);t.getn(115,115).setBlock(Blocks.coreShard,Team.crux,0);t.getn(64,64).setBlock(Vars.content.block("modular-shipyard-shipyard"),Team.sharded,0);t.getn(66,64).setBlock(Blocks.powerSource,Team.sharded,0);});Vars.state.set(State.playing);
        Time.setDeltaProvider(()->1f);Time.delta=1;
        int units=Vars.content.units().size,bullets=Vars.content.bullets().size;
        check(Vars.content.units().count(t->t.name.startsWith("modular-shipyard-ship-"))==3,"player hull bloat");
        check(Vars.content.units().count(t->t.name.equals("modular-shipyard-eigen-drone"))==1,"missing fixed drone ID");
        for(int tier=0;tier<3;tier++){
            Unit owner=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            owner.set(320,400);owner.add();ExperimentalChecks.equip(owner,tier,"eigen");owner.aim(400,400);owner.isShooting=false;
            near(owner.mounts[0].weapon.bullet.damage,8f*new float[]{.35f,.8f,2f}[tier],"private per-tier contact damage");
            Unit enemy=ExperimentalChecks.enemy(tier==1?UnitTypes.flare:UnitTypes.dagger,400,400);enemy.armor=0;
            Unit ally=UnitTypes.dagger.create(Team.sharded);ally.set(enemy);ally.add();float friendly=ally.health;
            step(185);check(live(owner)==0,"idle Eigen auto-deployed drones");
            owner.isShooting=true;owner.disarmed=true;step(40);check(live(owner)==0,"disarmed Eigen deployed drones");owner.disarmed=false;
            owner.aim(owner.x,owner.y);update.invoke(null);check(live(owner)==1,"first trigger did not launch exactly one drone");
            Unit launched=Groups.unit.getByID(ids(owner)[0]);var gun=owner.mounts[0].weapon;float rotation=owner.rotation-90f;
            near(launched.x,owner.x+arc.math.Angles.trnsx(rotation,gun.x,gun.y)+arc.math.Angles.trnsx(rotation+gun.baseRotation,gun.shootX,gun.shootY),"drone did not launch from gun X");
            near(launched.y,owner.y+arc.math.Angles.trnsy(rotation,gun.x,gun.y)+arc.math.Angles.trnsy(rotation+gun.baseRotation,gun.shootX,gun.shootY),"drone did not launch from gun Y");
            owner.mounts[0].weapon.update(owner,owner.mounts[0]);
            check(owner.mounts[0].recoil<1f && owner.mounts[0].heat<1f,"launcher visuals did not cool down");
            owner.isShooting=false;step(185);check(live(owner)==1,"released trigger kept deploying");
            deploy(owner);check(owner.mounts[0].totalShots==5 && owner.mounts[1].totalShots==4,"launches did not alternate guns");
            owner.aim(400,400);owner.isShooting=true;step(60);check(live(owner)==9,"swarm did not fill to nine with unit cap zero");check(enemy.health<10000,"swarm missed air/ground contact");near(ally.health,friendly,"swarm hit ally");
            float hp=new float[]{20,55,130}[tier];
            for(int id:ids(owner)){
                Unit drone=Groups.unit.getByID(id);check(drone!=null && drone.isFlying() && !drone.hittable() && !drone.targetable(Team.crux) && !drone.type.killable,"drone not excluded from combat targeting");
                near(drone.maxHealth,hp,"drone tier durability");check(!drone.type.useUnitCap && !drone.type.playerControllable,"drone consumes cap or allows possession");
            }
            Unit pushed=Groups.unit.getByID(ids(owner)[0]);pushed.vel.set(-10f,0f);update.invoke(null);check(pushed.vel.x<-8f,"drone guidance erased external physics impulse");pushed.vel.setZero();
            // Contact cadence still scales, independently of the finite lifetime damage budget.
            Unit cadence=Groups.unit.getByID(ids(owner)[0]);cadence.set(enemy);
            owner.reloadMultiplier=1.5f;forceContact(owner,cadence,0);
            near(values(owner,"cooldown")[0],20f,"Fire Rate did not improve drone contact cadence");owner.reloadMultiplier=1f;
            owner.isShooting=false;float health=enemy.health;step(185);near(enemy.health,health,"recalled swarm kept dealing damage");
            for(int id:ids(owner))check(Groups.unit.getByID(id).within(owner,40f),"released drone did not recall");
            // Native repair turrets select damaged units. Contact wear must never make a drone eligible.
            Unit orbit=Groups.unit.getByID(ids(owner)[0]);orbit.damagePierce(10000);near(orbit.health,orbit.maxHealth,"enemy damage depleted energy drone HP");
            check(!orbit.damaged(),"contact wear made drone a repair target");
            check(mindustry.entities.Units.closestEnemy(Team.crux,orbit.x,orbit.y,64f,u->u.type.name.equals("modular-shipyard-eigen-drone"))==null,"native enemy targeting acquired an energy drone");
            var repair=(mindustry.world.blocks.units.RepairTurret.RepairPointBuild)Blocks.repairPoint.newBuilding().create(Blocks.repairPoint,Team.sharded);
            repair.set(owner.x,owner.y);Time.time+=25f;repair.updateTile();check(repair.target==null,"allied repair turret acquired a drone");
            for(int tick=0;tick<180;tick++){float speed=tick%120<60?8f:-8f;owner.vel.set(speed,0);owner.x+=speed;step(1);}
            for(int id:ids(owner))check(Groups.unit.getByID(id).within(owner,45f),"orbit could not keep pace with moving owner");
            owner.vel.setZero();owner.set(320,400);step(90);
            Unit beyond=ExperimentalChecks.enemy(UnitTypes.dagger,owner.x+250,owner.y);beyond.armor=0;
            owner.isShooting=true;owner.aim(owner.x+300,owner.y);step(90);near(beyond.health,10000,"swarm exceeded control reach");
            for(int id:ids(owner))check(Groups.unit.getByID(id).within(owner,207f),"drone ignored clamped cursor anchor");beyond.remove();
            enemy.remove();Vars.world.tile(49,49).setBlock(Blocks.copperWallLarge,Team.crux,0);var wall=Vars.world.build(49,49);float wallHealth=wall.health;
            owner.aim(wall.x,wall.y);step(75);check(wall.health<wallHealth,"swarm cannot damage a native building");wall.tile.remove();owner.isShooting=false;step(185);deploy(owner);
            int lost=ids(owner)[0];Unit victim=Groups.unit.getByID(lost);
            Unit burnTarget=ExperimentalChecks.enemy(UnitTypes.dagger,victim.x,victim.y);burnTarget.armor=0;Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();
            owner.isShooting=true;
            for(int n=0;n<40 && Groups.unit.getByID(lost)!=null;n++)forceContact(owner,victim,0);
            check(live(owner)==8,"contact damage did not exhaust drone");owner.isShooting=false;burnTarget.remove();
            step(170);check(live(owner)==8,"replacement bypassed three-second cooldown");step(10);check(live(owner)==8,"idle drone replacement auto-launched");
            owner.isShooting=true;step(1);check(live(owner)==9 && ids(owner)[0]!=lost,"firing did not launch ready replacement");owner.isShooting=false;
            int[] old=ids(owner);
            ExperimentalChecks.equip(owner,tier,"standard");step(1);for(int id:old)check(Groups.unit.getByID(id)==null,"weapon swap left orphan");
            ExperimentalChecks.equip(owner,tier,"eigen");step(1);check(live(owner)==0,"weapon reselect instantly refreshed drones");step(185);check(live(owner)==0,"idle reselect auto-launched swarm");deploy(owner);check(live(owner)==9,"reselected swarm did not recover while firing");
            owner.remove();step(1);for(int id:ids(owner))check(Groups.unit.getByID(id)==null,"owner removal left a drone");enemy.remove();ally.remove();Groups.bullet.clear();
        }
        // Two independently owned swarms and native disk persistence with a missing slot and damaged drone.
        Unit a=UnitTypes.gamma.create(Team.sharded),b=UnitTypes.alpha.create(Team.sharded);a.set(320,400);b.set(500,400);a.add();b.add();
        install(a,2);install(b,0);a.isShooting=b.isShooting=false;step(185);check(live(a)==0 && live(b)==0,"owners auto-deployed");deploy(a);deploy(b);check(live(a)==9 && live(b)==9,"owners share capacity");
        IntSet owned=new IntSet();for(int id:ids(a))owned.add(id);for(int id:ids(b))check(!owned.contains(id),"drone shared by owners");
        var paletteB=ExperimentalChecks.colours.getMethod("parse",String.class).invoke(null,"F7E2E1,FF80AB,3366FF");
        loader.loadClass("shipyard.service.UnitLoadouts").getMethod("install",Unit.class,ExperimentalChecks.tiers,ExperimentalChecks.model,ExperimentalChecks.colours).invoke(null,b,ExperimentalChecks.tiers.getEnumConstants()[0],ExperimentalChecks.loadout("eigen"),paletteB);
        check(!service.getMethod("colour",Unit.class).invoke(null,Groups.unit.getByID(ids(a)[0])).equals(service.getMethod("colour",Unit.class).invoke(null,Groups.unit.getByID(ids(b)[0]))),"owners share drone palette");
        int[] before=ids(a);values(a,"charge")[1]=50;
        Call.unitDespawn(Groups.unit.getByID(before[0]));step(10);int ownerId=a.id,otherId=b.id;
        var launchTimer=state(a).getClass().getDeclaredField("launchCooldown");launchTimer.setAccessible(true);launchTimer.setFloat(state(a),17f);
        var launchMount=state(a).getClass().getDeclaredField("launchMount");launchMount.setAccessible(true);launchMount.setInt(state(a),1);
        var file=Vars.dataDirectory.child("eigen-native.msav");SaveIO.write(file);SaveIO.load(file);Vars.state.set(State.playing);a=Groups.unit.getByID(ownerId);b=Groups.unit.getByID(otherId);check(a!=null && b!=null,"save lost owner");
        update.invoke(null);near(launchTimer.getFloat(state(a)),16f,"save lost launch cooldown");check(launchMount.getInt(state(a))==1,"save lost next gun");check(ExperimentalChecks.current(a).equals("eigen"),"save lost Eigen loadout");check(live(a)==8 && live(b)==9,"save duplicated or replenished swarm");
        near(values(a,"charge")[1],50,"save replenished contact charge");near(Groups.unit.getByID(before[1]).health,130,"save damaged native drone HP");near(Groups.unit.getByID(before[1]).maxHealth,130,"save lost private drone tier");
        step(150);check(live(a)==8,"save skipped replacement time");step(30);check(live(a)==8,"saved replacement auto-launched while idle");a.isShooting=true;step(1);a.isShooting=false;check(live(a)==9,"saved replacement did not launch when firing");
        int[] savedIds=ids(a);var bytes=new ByteArrayOutputStream();mindustry.io.SaveIO.getVersion().writeCustomChunks(new DataOutputStream(bytes),true);
        Events.fire(new EventType.ResetEvent());mindustry.io.SaveIO.getVersion().readCustomChunks(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));update.invoke(null);
        for(int i=0;i<9;i++)check(ids(a)[i]==savedIds[i],"join codec replaced live drones");
        clientChecks(a,b);legacyChecks(a,b);
        var yard=Vars.world.build(64,64);yard.power.graph.reflow(yard);yard.power.graph.update();
        Player pilot=LoadoutChecks.player("eigen-docking");pilot.add();pilot.unit(a);a.set(yard.x,yard.y-20);a.spawnedByCore=true;
        int[] dockedIds=ids(a);mindustry.input.InputHandler.buildingControlSelect(pilot,yard);
        check(!a.isAdded(),"Eigen owner failed to dock");step(1);
        for(int id:dockedIds)check(Groups.unit.getByID(id)==null,"docking left active drones");
        yard.getClass().getMethod("release",Player.class).invoke(yard,pilot);check(pilot.unit()==a && a.isAdded(),"dock did not return original owner");a.isShooting=false;step(185);check(live(a)==0,"undocking auto-launched swarm");deploy(a);check(live(a)==9,"undocked swarm did not recover when firing");
        a.remove();b.remove();pilot.remove();step(1);check(Groups.unit.count(u->{try{return (boolean)isDrone.invoke(null,u);}catch(Exception e){throw new RuntimeException(e);}})==0,"removed owners left swarm units");
        check(Vars.content.units().size==units && Vars.content.bullets().size==bullets,"Eigen views registered per-loadout content");
        System.out.println("PASS: Eigen nine native energy drones, cap-free bounded ownership, untargetability, native repair exclusion, moving orbit, contact wear, air/ground contact, ally safety, recall, replacement/swap cleanup, two-owner isolation, disk/join charge/cooldown/ID persistence and fixed content counts");
    }
    static void clientChecks(Unit a,Unit b)throws Exception{
        var active=Vars.net.getClass().getDeclaredField("active");var server=Vars.net.getClass().getDeclaredField("server");active.setAccessible(true);server.setAccessible(true);
        boolean wasActive=active.getBoolean(Vars.net),wasServer=server.getBoolean(Vars.net);
        var field=service.getDeclaredField("epoch");field.setAccessible(true);String epoch=(String)field.get(null);
        int[] baseline=ids(a);int count=Groups.unit.size();StringBuilder packet=new StringBuilder(epoch).append(',').append(a.id).append(",2");
        for(int id:baseline)packet.append(',').append(id).append(",0,0,50");
        try{
            active.setBoolean(Vars.net,true);server.setBoolean(Vars.net,false);
            for(int i=0;i<100;i++)update.invoke(null);
            check(Groups.unit.size()==count,"client spawned or retired authoritative drones");
            mindustry.core.NetClient.clientPacketReliable("modular-shipyard-eigen-v3",packet.toString());
            check(ids(a)[0]==baseline[0],"valid native packet rejected");
            String duplicate=packet.toString().replace(","+baseline[0]+",0,0,50",","+baseline[1]+",0,0,50");
            mindustry.core.NetClient.clientPacketReliable("modular-shipyard-eigen-v3",duplicate);
            check(ids(a)[0]==baseline[0],"packet accepted duplicate drone IDs");
            String stolen=packet.toString().replace(","+baseline[0]+",0,0,50",","+ids(b)[0]+",0,0,50");
            mindustry.core.NetClient.clientPacketReliable("modular-shipyard-eigen-v3",stolen);
            check(ids(a)[0]==baseline[0],"packet stole another owner's drone");
            mindustry.core.NetClient.clientPacketReliable("modular-shipyard-eigen-v3",packet.toString().replace(",0,0,50",",NaN,0,130"));
            mindustry.core.NetClient.clientPacketReliable("modular-shipyard-eigen-v3","old-"+epoch+","+a.id+",remove");
            check(ids(a).length==9,"invalid/stale packet changed state");
            mindustry.core.NetClient.clientPacketReliable("modular-shipyard-eigen-v3",epoch+","+a.id+",remove");
            check(ids(a).length==0 && Groups.unit.size()==count,"client removal failed or removed native units directly");
            mindustry.core.NetClient.clientPacketReliable("modular-shipyard-eigen-v3",packet.toString());
            check(ids(a).length==9,"native snapshot did not restore links");
        }finally{active.setBoolean(Vars.net,wasActive);server.setBoolean(Vars.net,wasServer);}
    }
    static void legacyChecks(Unit a,Unit b)throws Exception{
        int[] aIds=ids(a),bIds=ids(b);
        for(int version=1;version<=3;version++){
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(version);out.writeUTF("legacy-eigen");out.writeInt(2);
            for(Unit u:new Unit[]{a,b}){out.writeInt(u.id);out.writeByte(u==a?2:0);if(version>=3){out.writeFloat(36f);out.writeInt(1);}for(int slot=0;slot<6;slot++){int id=(u==a?aIds:bIds)[slot];out.writeInt(id);out.writeFloat(0);out.writeFloat(0);if(version>=2)out.writeFloat(u==a?77f:11f);}}
            Unit drone=Groups.unit.getByID(ids(a)[0]);drone.health=77;
            var field=mindustry.io.SaveVersion.class.getDeclaredField("customChunks");field.setAccessible(true);
            var chunks=(arc.struct.OrderedMap<?,?>)field.get(null);mindustry.io.SaveFileReader.CustomChunk eigenChunk=null;
            for(var entry:chunks)if("modular-shipyard-eigen-v1".equals(entry.key))eigenChunk=(mindustry.io.SaveFileReader.CustomChunk)entry.value;
            check(eigenChunk!=null,"missing Eigen custom chunk");eigenChunk.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            near(values(a,"charge")[0],(version==1?130:77)*2f/3f,"legacy Gamma budget migration");near(values(b,"charge")[0],(version==1?20:11)*2f/3f,"legacy Alpha budget migration");
            near(drone.health,drone.maxHealth,"legacy native HP made drone repairable");
        }
    }
    static Object state(Unit u)throws Exception{
        var f=service.getDeclaredField("states");f.setAccessible(true);return ((arc.struct.IntMap<?>)f.get(null)).get(u.id);
    }
    static float[] values(Unit u,String name)throws Exception{Object state=state(u);var f=state.getClass().getDeclaredField(name);f.setAccessible(true);return (float[])f.get(state);}
    static void forceContact(Unit owner,Unit drone,int slot)throws Exception{
        Object state=state(owner);var m=service.getDeclaredMethod("contact",Unit.class,Unit.class,state.getClass(),int.class);m.setAccessible(true);m.invoke(null,owner,drone,state,slot);
    }
    static void install(Unit u,int tier)throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        loader.loadClass("shipyard.service.UnitLoadouts").getMethod("install",Unit.class,ExperimentalChecks.tiers,ExperimentalChecks.model,ExperimentalChecks.colours)
            .invoke(null,u,ExperimentalChecks.tiers.getEnumConstants()[tier],ExperimentalChecks.loadout("eigen"),ExperimentalChecks.palette);
    }
    static void deploy(Unit owner)throws Exception{
        boolean held=owner.isShooting;float aimX=owner.aimX,aimY=owner.aimY;
        owner.aim(owner.x,owner.y);owner.isShooting=true;step(185);owner.isShooting=held;owner.aim(aimX,aimY);
    }
    static int[] ids(Unit u)throws Exception{return (int[])drones.invoke(null,u);}
    static int live(Unit u)throws Exception{int count=0;for(int id:ids(u)){Unit drone=Groups.unit.getByID(id);if(drone!=null && !drone.dead)count++;}return count;}
    static void step(int ticks)throws Exception{
        for(int i=0;i<ticks;i++){
            Time.update();update.invoke(null);Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();
            for(Unit u:Groups.unit.copy())if((boolean)isDrone.invoke(null,u))u.update();
            Groups.unit.updatePhysics();Groups.bullet.updatePhysics();Groups.bullet.update();Groups.bullet.collide();
        }
    }
    static void check(boolean value,String why){ExperimentalChecks.check(value,why);}
    static void near(float a,float b,String why){ExperimentalChecks.near(a,b,why);}
}
