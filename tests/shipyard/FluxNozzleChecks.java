package shipyard;

import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.world.blocks.defense.turrets.ItemTurret;

/** Real flame flight, private range tuning and unchanged vanilla ammo on every ship tier. */
final class FluxNozzleChecks{
    static void run()throws Exception{
        Vars.logic.reset();Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;
        Vars.world.loadGenerator(80,80,t->{t.fill();t.eachTile(tile->tile.setFloor(Blocks.stone.asFloor()));});
        Time.setDeltaProvider(()->1f);Time.delta=1f;
        var vanilla=((ItemTurret)Blocks.scorch).ammoTypes.get(Items.coal);
        float nativeLife=vanilla.lifetime,nativeSpread=vanilla.inaccuracy,nativeRange=vanilla.range;
        for(int tier=0;tier<3;tier++){
            Unit ship=new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            ship.spawnedByCore=true;ship.set(200,200);ship.add();ExperimentalChecks.equip(ship,tier,"flamethrower");
            var gun=ship.mounts[0].weapon;var flame=gun.bullet;
            near(flame.lifetime,nativeLife*1.1f,"longer flame lifetime");near(flame.inaccuracy,nativeSpread,"native straight projectile accuracy changed");
            near(flame.range,nativeRange*1.1f,"native range calculation");near(gun.range(),flame.range,"mount range differs from flight range");
            float preview=(float)ExperimentalChecks.weapons.getMethod("range",ExperimentalChecks.tiers,ExperimentalChecks.kinds).invoke(null,ExperimentalChecks.tiers.getEnumConstants()[tier],ExperimentalChecks.kinds.getEnumConstants()[1]);
            near(preview,flame.range,"range preview is stale");
            near(flame.damage,vanilla.damage*new float[]{.25f,.85f,2.5f}[tier],"tier damage changed");
            check(flame.status==StatusEffects.burning && flame.collidesAir && flame.collidesGround,"flame targeting/status changed");
            Bullet shot=flame.create(ship,ship.team,200,200,0f);int ticks=0;
            while(shot.isAdded() && ticks++<100){Time.update();Groups.bullet.updatePhysics();Groups.bullet.update();}
            check(!shot.isAdded() && shot.dst(200,200)>nativeRange,"flame did not travel past the old range");
            check(gun.name.contains("flux") && gun.recoil==0,"nozzle mount lost its zero-recoil flame behaviour");
            System.out.println("FLUX "+tier+": range="+flame.range+" (was "+nativeRange+"), spread="+flame.inaccuracy+" (was "+nativeSpread+"), flight="+shot.dst(200,200));
            ship.remove();
        }
        near(vanilla.lifetime,nativeLife,"vanilla Scorch lifetime changed");near(vanilla.inaccuracy,nativeSpread,"vanilla Scorch spread changed");near(vanilla.range,nativeRange,"vanilla Scorch range changed");
        System.out.println("PASS: Flux longer physical flight and accurate preview, preserved straight-shot accuracy and tier damage/Burning/air-ground targeting and vanilla Scorch isolation");
    }
    static void near(float actual,float expected,String why){check(Math.abs(actual-expected)<.03f,why+": "+actual+" != "+expected);}
    static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
