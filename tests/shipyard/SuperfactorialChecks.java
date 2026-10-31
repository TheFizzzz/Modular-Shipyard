package shipyard;

import arc.graphics.Color;
import arc.math.Angles;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;

/** Real native mounts: cadence, spread, isolation, tier damage and interruption behaviour. */
final class SuperfactorialChecks{
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;Vars.state.rules.unitCap=1000;
        Vars.world.loadGenerator(96,96,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Time.setDeltaProvider(()->1f);Time.delta=1f;
        int registered=Vars.content.bullets().size;
        for(int tier=0;tier<3;tier++){
            Unit ship=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            ship.spawnedByCore=true;ship.dead=false;ship.set(320,320);ship.rotation=0;ship.add();
            ExperimentalChecks.equip(ship,tier,"superfactorial");
            var mount=ship.mounts[0];var gun=mount.weapon;
            near(gun.bullet.damage,new float[]{1,3,8}[tier],"tier damage");
            near(gun.range(),160f,"minigun range");near(gun.inaccuracy,24f,"spread");
            near(gun.bullet.hitColor.g,Color.valueOf("30EE70").g,"engine colour");
            int initial=mount.totalShots;
            ExperimentalChecks.fire(ship,ship.x+140,ship.y,1);
            var coldType=mount.bullet.type;
            check(coldType.armorMultiplier>.99f,"cold round gained full penetration");
            ExperimentalChecks.fire(ship,ship.x+140,ship.y,59);int slow=mount.totalShots-initial;
            ExperimentalChecks.fire(ship,ship.x+140,ship.y,180);int start=mount.totalShots;
            boolean wide=false;
            for(int i=0;i<60;i++){
                ExperimentalChecks.fire(ship,ship.x+140,ship.y,1);
                if(mount.bullet!=null && Angles.angleDist(mount.bullet.rotation(),0f)>18f)wide=true;
            }
            int fast=mount.totalShots-start;
            var hotType=mount.bullet.type;
            near(hotType.armorMultiplier,.1f,"full-spin armour multiplier");
            near(gun.bullet.armorMultiplier,1f,"spin mutated the loadout projectile template");
            check(coldType.armorMultiplier>.99f,"old rounds inherited new penetration");
            Unit armoured=ExperimentalChecks.enemy(UnitTypes.dagger,ship.x+20,ship.y);armoured.armor=4f;
            float before=armoured.health;var cold=coldType.create(ship,ship.team,ship.x,ship.y,0);cold.collision(armoured,armoured.x,armoured.y);
            float coldDamage=before-armoured.health;before=armoured.health;
            var hot=hotType.create(ship,ship.team,ship.x,ship.y,0);hot.collision(armoured,armoured.x,armoured.y);
            near(before-armoured.health,mindustry.entities.Damage.applyArmor(gun.bullet.damage,.4f),"hot-round armour damage");
            check(before-armoured.health>coldDamage,"spin-up penetration did not help against armour");armoured.remove();
            check(slow>=1 && slow<=3 && fast>=14 && fast<=16,"bad spin-up cadence: "+slow+" -> "+fast);
            // Four alternating barrels give this mount only 15 independent shots per second.
            // Sample more actual rounds if the first short random window misses the outer spread.
            for(int tick=0;tick<180 && !wide;tick++){
                ExperimentalChecks.fire(ship,ship.x+140,ship.y,1);
                if(mount.bullet!=null && Angles.angleDist(mount.bullet.rotation(),0f)>18f)wide=true;
            }
            check(wide,"native firing did not produce wide spread");
            // A second player's identical definition must start cold despite the first's full spin.
            Unit other=UnitTypes.alpha.create(Team.sharded);other.spawnedByCore=true;other.dead=false;other.set(320,500);other.rotation=0;other.add();
            ExperimentalChecks.equip(other,0,"superfactorial");
            ExperimentalChecks.fire(other,other.x+140,other.y,60);
            check(other.mounts[0].totalShots<=4,"spin leaked to another player");other.remove();
            ship.isShooting=false;
            for(var m:ship.mounts){m.shoot=false;m.weapon.update(ship,m);}
            start=mount.totalShots;ExperimentalChecks.fire(ship,ship.x+140,ship.y,60);
            check(mount.totalShots-start<=4,"release did not reset spin");
            ExperimentalChecks.fire(ship,ship.x+140,ship.y,240);
            ship.reloadMultiplier=1.5f;start=mount.totalShots;
            ExperimentalChecks.fire(ship,ship.x+140,ship.y,60);
            check(mount.totalShots-start>=21 && mount.totalShots-start<=24,"fractional Fire Rate upgrade was lost");
            ship.reloadMultiplier=2f;start=mount.totalShots;
            ExperimentalChecks.fire(ship,ship.x+140,ship.y,60);
            check(mount.totalShots-start>=29,"Fire Rate did not affect full-speed minigun");ship.reloadMultiplier=1f;
            ship.disarmed=true;start=mount.totalShots;ExperimentalChecks.fire(ship,ship.x+140,ship.y,1);
            check(start==mount.totalShots,"disarmed minigun fired");ship.disarmed=false;
            start=mount.totalShots;ExperimentalChecks.fire(ship,ship.x+140,ship.y,60);
            check(mount.totalShots-start<=4,"disarm did not reset spin");
            ExperimentalChecks.fire(ship,ship.x+140,ship.y,240);
            ExperimentalChecks.weapons.getMethod("resetSpin",Unit.class).invoke(null,ship);
            start=mount.totalShots;ExperimentalChecks.fire(ship,ship.x+140,ship.y,60);
            check(mount.totalShots-start<=4,"docking reset did not clear spin");
            for(var type:new mindustry.type.UnitType[]{UnitTypes.dagger,UnitTypes.flare}){
                Unit enemy=ExperimentalChecks.enemy(type,ship.x+20,ship.y);float hp=enemy.health;
                Bullet hit=gun.bullet.create(ship,ship.team,ship.x,ship.y,0f);
                check(hit.collides(enemy),"round rejected ground/air target");hit.collision(enemy,enemy.x,enemy.y);
                check(enemy.health<hp,"round did no ground/air damage");enemy.remove();
            }
            // Every round is a simple physical hit, with no piercing or splash.
            check(!gun.bullet.pierce && gun.bullet.splashDamage==0f,"minigun round gained area/pierce damage");
            ship.remove();
        }
        check(Vars.content.bullets().size==registered,"minigun loadouts registered projectile IDs");
        System.out.println("PASS: Superfactorial four-gun alternating-pair 4-to-60 total shots/s native ramp, spread, release/disarm/dock resets, Fire Rate, per-player isolation, snapshotted armour penetration, tier damage and air/ground collision");
    }
    static void near(float a,float b,String why){ExperimentalChecks.near(a,b,why);}
    static void check(boolean ok,String why){ExperimentalChecks.check(ok,why);}
}
