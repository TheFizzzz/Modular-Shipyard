package shipyard;

import arc.Events;
import arc.util.Time;
import java.io.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.type.UnitType;
import mindustry.world.Block;

/** Real native groups, impulses, projectile absorption, lifecycle and save/join state. */
final class UtilityChecks{
    static Class<?> utilities,model,weapons,cosmetics,tiers,balance;
    static java.lang.reflect.Method apply,update;
    static Object neutral,palette;static Player owner;static Building yard;
    static void run(Block block)throws Exception{
        var l=Vars.mods.getMod("modular-shipyard").loader;
        utilities=l.loadClass("shipyard.service.UtilityService");model=l.loadClass("shipyard.model.ShipLoadout");
        weapons=l.loadClass("shipyard.model.ShipWeapon");cosmetics=l.loadClass("shipyard.model.ShipCosmetics");
        tiers=l.loadClass("shipyard.model.ShipTier");balance=l.loadClass("shipyard.model.UtilityBalance");
        update=utilities.getMethod("update");neutral=model.getField("defaults").get(null);
        palette=cosmetics.getMethod("parse",String.class).invoke(null,"F7E2E1,FF4488,30EE70");
        apply=l.loadClass("shipyard.service.LoadoutService").getMethod("apply",Player.class,int.class,int.class,model,cosmetics,String.class);
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.possessionAllowed=true;
        Vars.state.rules.waves=false;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(128,128,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));
            t.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);t.getn(115,115).setBlock(Blocks.coreShard,Team.crux,0);
            t.getn(64,64).setBlock(block,Team.sharded,0);t.getn(66,64).setBlock(Blocks.powerSource,Team.sharded,0);});
        yard=Vars.world.build(64,64);yard.power.graph.reflow(yard);yard.power.graph.update();
        Vars.state.set(State.playing);Time.delta=1f;owner=LoadoutChecks.player("utility-owner-"+System.nanoTime());owner.add();
        Unit last=null;
        int bullets=Vars.content.bullets().size,units=Vars.content.units().size;
        Object[] choices=weapons.getEnumConstants();
        check(!(boolean)weapons.getMethod("selectable").invoke(choices[3]),"legacy shotgun still selectable");
        check((boolean)weapons.getMethod("utility").invoke(choices[7]) && (boolean)weapons.getMethod("utility").invoke(choices[8]),"utility classification missing");
        for(int t=0;t<3;t++){
            near((float)balance.getMethod("pulseReload",tiers).invoke(null,tiers.getEnumConstants()[t]),150f,"Divergence base cooldown is not 2.5 seconds");
            UnitType base=new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[t];
            Unit ship=base.create(Team.sharded);ship.spawnedByCore=true;ship.set(yard.x,yard.y-20);ship.add();owner.unit(ship);
            equip(ship,7,"Divergence");
            Unit small=enemy(UnitTypes.dagger,ship.x+30,ship.y),heavy=enemy(UnitTypes.reign,ship.x+30,ship.y+2),air=enemy(UnitTypes.flare,ship.x,ship.y+30);
            Unit outside=enemy(UnitTypes.dagger,ship.x+160,ship.y),friend=UnitTypes.dagger.create(Team.sharded);friend.set(ship.x+20,ship.y);friend.add();
            Vars.state.teams.updateTeamStats();ship.isShooting=true;update.invoke(null);
            check(small.vel.x>0 && air.vel.y>0,"radial pulse missed ground/air");
            check(small.vel.len()>heavy.vel.len(),"heavy enemy did not resist pulse");
            near(friend.vel.len(),0,"pulse moved an ally");near(outside.vel.len(),0,"pulse exceeded radius");
            near(small.health,small.maxHealth,"pulse caused damage");float velocity=small.vel.x;update.invoke(null);near(small.vel.x,velocity,"held fire bypassed pulse reload");
            for(Unit u:new Unit[]{small,heavy,air,outside,friend})u.remove();ship.isShooting=false;
            equip(ship,8,"Invariant");ship.aimX=ship.x+100;ship.aimY=ship.y;ship.rotation=0;ship.isShooting=true;
            update.invoke(null);check(active(ship),"held shield not active");
            float cap=value("capacity",t),recovery=value("recovery",t);
            float radius=(float)balance.getMethod("range",tiers,weapons).invoke(null,tiers.getEnumConstants()[t],choices[8]);
            float depth=radius*balance.getField("shieldDepth").getFloat(null);
            float[] band=(float[])utilities.getMethod("vertices",Unit.class,float.class).invoke(null,ship,0f);
            float[] beams=(float[])utilities.getMethod("projectors",Unit.class,float.class).invoke(null,ship,0f);
            check(beams.length==ship.mounts.length*4,"missing gun projectors");
            for(int i=0;i<beams.length;i+=4){
                check(arc.math.Mathf.dst(ship.x,ship.y,beams[i],beams[i+1])>.1f,"projector starts at ship center instead of muzzle");
                float dx=beams[i+2]-beams[i],dy=beams[i+3]-beams[i+1],len=arc.math.Mathf.len(dx,dy);
                check(!(boolean)utilities.getMethod("intersects",float[].class,float.class,float.class,float.class,float.class)
                    .invoke(null,band,beams[i],beams[i+1],beams[i+2],beams[i+3]),"projection clips into shield band");
                check((boolean)utilities.getMethod("intersects",float[].class,float.class,float.class,float.class,float.class)
                    .invoke(null,band,beams[i+2],beams[i+3],beams[i+2]+dx/len,beams[i+3]+dy/len),"projection stops too far from shield");
            }
            Bullet front=shot(ship,depth,0,Team.crux,10),rear=shot(ship,-radius,0,Team.crux,10),ally=shot(ship,depth,2,Team.sharded,10);
            Groups.bullet.updatePhysics();update.invoke(null);
            check(front.absorbed && !rear.absorbed && !ally.absorbed,"shield team/front boundary wrong");near(hp(ship),cap-10,"shield damage differs from native projectile damage");
            rear.remove();ally.remove();
            Bullet interior=shot(ship,depth*.5f,0,Team.crux,1),side=shot(ship,0,radius,Team.crux,1);
            Bullet openRear=shot(ship,-radius-10,0,Team.crux,1);openRear.vel.set(radius+10,0);
            Groups.bullet.updatePhysics();update.invoke(null);
            check(!interior.absorbed && !openRear.absorbed && side.absorbed,"C-shield band/side coverage/rear opening differs from shape");
            interior.remove();openRear.remove();
            // Sweep test: both endpoints outside, but the incoming segment crosses the shield band.
            Bullet fast=shot(ship,150,0,Team.crux,5);fast.vel.set(-220,0);Groups.bullet.updatePhysics();update.invoke(null);check(fast.absorbed,"fast shot tunneled through shield");
            ship.aimX=ship.x;ship.aimY=ship.y+100;
            Bullet rotated=shot(ship,0,depth,Team.crux,5);Groups.bullet.updatePhysics();update.invoke(null);check(rotated.absorbed,"shield did not follow aim");
            float before=hp(ship);ship.isShooting=false;update.invoke(null);check(hp(ship)>before,"released shield did not recharge");
            ship.isShooting=true;Bullet breaking=shot(ship,0,depth,Team.crux,cap+1);Groups.bullet.updatePhysics();update.invoke(null);
            check(breaking.absorbed && !active(ship),"shield break failed");near(cd(ship),recovery,"break lockout missing");
            Bullet during=shot(ship,0,depth,Team.crux,1);Groups.bullet.updatePhysics();update.invoke(null);
            check(!during.absorbed && !active(ship),"holding fire bypassed broken cooldown");during.remove();
            float locked=cd(ship);equip(ship,0,"");near(cd(ship),locked,"weapon switching reset cooldown");equip(ship,8,"Invariant");near(cd(ship),locked,"Shipyard Apply reset cooldown");
            ship.isShooting=true;
            for(int i=0;i<(int)locked;i++)update.invoke(null);
            near(cd(ship),0,"cooldown did not expire");near(hp(ship),cap,"recovery did not restore capacity");
            update.invoke(null);check(active(ship),"shield failed to reactivate after full recovery");
            Bullet breakAgain=shot(ship,0,depth,Team.crux,cap+1);Groups.bullet.updatePhysics();update.invoke(null);check(breakAgain.absorbed,"repeat shield failed");
            ship.isShooting=false;last=ship;
        }
        check(Vars.content.bullets().size==bullets && Vars.content.units().size==units,"utilities registered content per loadout");
        int id=last.id;float remaining=cd(last);
        var save=Vars.dataDirectory.child("utilities-roundtrip.msav");SaveIO.write(save);SaveIO.load(save);last=Groups.unit.getByID(id);
        near(cd(last),remaining,"disk save reset broken cooldown");near(hp(last),0,"disk save reset shield health");
        var bytes=new ByteArrayOutputStream();SaveIO.getVersion().writeCustomChunks(new DataOutputStream(bytes),true);
        Events.fire(new ResetEvent());SaveIO.getVersion().readCustomChunks(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        near(cd(last),remaining,"join state reset broken cooldown");
        owner=LoadoutChecks.player("utility-held-"+System.nanoTime());owner.add();owner.unit(last);last.set(yard=Vars.world.build(64,64));
        yard.power.graph.reflow(yard);yard.power.graph.update();
        mindustry.input.InputHandler.buildingControlSelect(owner,yard);check(!last.isAdded(),"utility fixture failed to dock");
        SaveIO.write(save);SaveIO.load(save);
        yard=Vars.world.build(64,64);last=(Unit)yard.getClass().getField("ship").get(yard);
        check(last!=null,"occupied save lost utility ship");near(cd(last),remaining,"occupied save reset cooldown");
        System.out.println("PASS: tiered radial mass-aware air/ground impulses, ally/range/reload safety, flattened C-shaped band/gun projection endpoints/side/rear/interior and swept projectile absorption, break/held-fire lockout, release recharge, weapon/dock bypass prevention, private named utility loadouts and world/join/occupied persistence");
    }
    static void equip(Unit ship,int index,String name)throws Exception{
        ship.isShooting=false;Object value=model.getMethod("withWeapon",weapons).invoke(neutral,weapons.getEnumConstants()[index]);
        check(model.getMethod("parse",String.class).invoke(null,model.getMethod("encode").invoke(value)).equals(value),"utility profile roundtrip");
        ship.set(yard.x,yard.y-20);owner.unit(ship);
        String result=(String)DockTest.apply(apply,owner,yard.pos(),ship.id,value,palette,name);check(result.contains("applied"),result);
    }
    static Unit enemy(UnitType type,float x,float y){Unit u=type.create(Team.crux);if(type==UnitTypes.flare)u.elevation=1f;u.set(x,y);u.add();return u;}
    static Bullet shot(Unit ship,float x,float y,Team team,float damage){Bullet b=UnitTypes.dagger.weapons.first().bullet.create(ship,team,ship.x+x,ship.y+y,180f);b.damage=damage;b.vel.setZero();return b;}
    static float value(String method,int tier)throws Exception{return (float)balance.getMethod(method,tiers).invoke(null,tiers.getEnumConstants()[tier]);}
    static float cd(Unit u)throws Exception{return (float)utilities.getMethod("cooldown",Unit.class).invoke(null,u);}
    static float hp(Unit u)throws Exception{return (float)utilities.getMethod("health",Unit.class).invoke(null,u);}
    static boolean active(Unit u)throws Exception{return (boolean)utilities.getMethod("active",Unit.class).invoke(null,u);}
    static void near(float a,float b,String message){check(Math.abs(a-b)<.02f,message+": "+a+" vs "+b);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
