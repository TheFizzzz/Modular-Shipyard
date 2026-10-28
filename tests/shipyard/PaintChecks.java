package shipyard;

import arc.Events;
import arc.graphics.Color;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.world.Block;

/** Native firing, immutable shared definitions, palette lifetime and actual damage. */
final class PaintChecks{
    static void run(Block block) throws Exception{
        var loader = Vars.mods.getMod("modular-shipyard").loader;
        var tiers = loader.loadClass("shipyard.model.ShipTier");
        var model = loader.loadClass("shipyard.model.ShipLoadout");
        var cosmetics = loader.loadClass("shipyard.model.ShipCosmetics");
        var variants = loader.loadClass("shipyard.content.ShipVariants");
        var get = variants.getMethod("get", tiers, model, cosmetics);
        Object neutral = model.getField("defaults").get(null);
        Object green = cosmetics.getMethod("parse",String.class).invoke(null,"F7E2E1,FF4488,30EE70");
        Object pink = cosmetics.getMethod("parse",String.class).invoke(null,"F7E2E1,FF4488,F7E2E1");
        Object nativeEngine = cosmetics.getMethod("parse",String.class).invoke(null,"F7E2E1,-,-");
        Vars.world.loadGenerator(80,80,tiles -> {
            tiles.fill();tiles.eachTile(t -> t.setFloor(Blocks.stone.asFloor()));
            tiles.getn(8,8).setBlock(Blocks.coreShard,Team.sharded,0);
        });
        Vars.state.rules.env = mindustry.world.meta.Env.terrestrial;
        Time.delta=1f;
        int bulletTypes=Vars.content.bullets().size;
        var bases = new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma};
        for(int index=0;index<bases.length;index++){
            var base=bases[index];
            var original=(LaserBoltBulletType)base.weapons.first().bullet;
            Color originalBack=original.backColor.cpy(), originalTrail=original.trailColor.cpy(), originalHit=original.hitColor.cpy();
            Unit a=base.create(Team.sharded);a.set(200,200);a.add();a.rotation=0f;
            a.setType((mindustry.type.UnitType)get.invoke(null,tiers.getEnumConstants()[index],neutral,green));
            var mounts=a.mounts;
            var mount=mounts[0];mount.reload=0f;mount.shoot=true;mount.aimX=300;mount.aimY=200;
            a.isShooting=true;
            base.weapons.first().update(a,mount);
            Bullet shot=Groups.bullet.find(b -> b.owner==a);
            check(shot!=null,"native weapon did not fire: "+base.name);
            float damage=shot.damage,lifetime=shot.lifetime,vx=shot.vel.x,vy=shot.vel.y;
            EffectState flash = effect(original.shootEffect, a, original.hitColor);
            EffectState smoke = effect(original.smokeEffect, a, original.hitColor);
            Unit vanilla = base.create(Team.sharded);
            EffectState nativeFlash = effect(original.shootEffect, vanilla, original.hitColor);
            EffectState unrelatedEffect = effect(Fx.unitSpawn, a, original.hitColor);
            Events.fire(Trigger.afterGameUpdate);
            nearColour(flash.color, Color.valueOf("30EE70"), "muzzle flash colour");
            nearColour(smoke.color, Color.valueOf("30EE70"), "muzzle smoke colour");
            nearColour(nativeFlash.color, originalHit, "vanilla muzzle changed");
            nearColour(unrelatedEffect.color, originalHit, "unrelated effect changed");
            check(shot.type!=original && shot.type.getClass().getName().equals("shipyard.type.EngineBolt"),"native shot did not receive private palette");
            var painted=(LaserBoltBulletType)shot.type;
            nearColour(painted.backColor,Color.valueOf("30EE70"),"bolt colour");
            nearColour(painted.trailColor,Color.valueOf("30EE70"),"trail colour");
            nearColour(painted.hitColor,Color.valueOf("30EE70"),"impact colour");
            nearColour(painted.lightColor,Color.valueOf("30EE70"),"light colour");
            check(painted.frontColor.r>painted.backColor.r,"bright bolt centre missing");
            near(shot.damage,damage,"damage changed");near(shot.lifetime,lifetime,"lifetime changed");
            near(shot.vel.x,vx,"velocity changed");near(shot.vel.y,vy,"velocity changed");
            near(painted.homingPower,original.homingPower,"homing changed");
            near(painted.buildingDamageMultiplier,original.buildingDamageMultiplier,"building damage changed");
            near(painted.width,original.width,"bolt dimensions changed");near(painted.trailWidth,original.trailWidth,"trail dimensions changed");
            check(painted.pierce==original.pierce && painted.collides==original.collides && painted.trailLength==original.trailLength,
                "collision/trail mechanics changed");
            check(a.mounts==mounts && a.mounts[0].weapon==base.weapons.first(),"colour hook replaced weapon mounts");
            Unit target=UnitTypes.alpha.create(Team.crux);
            float hp=target.health;
            painted.hitEntity(shot,target,hp);
            near(hp-target.health,damage,"actual hit damage changed");

            // Another owner gets another palette on the same registered adapter ID.
            Unit b=base.create(Team.sharded);b.set(200,240);b.add();
            b.setType((mindustry.type.UnitType)get.invoke(null,tiers.getEnumConstants()[index],neutral,pink));
            Bullet other=original.create(b,Team.sharded,220,240,0);
            Events.fire(Trigger.afterGameUpdate);
            nearColour(((LaserBoltBulletType)other.type).backColor,Color.valueOf("F7E2E1"),"other palette missing");
            check(other.type!=shot.type && other.type.id==shot.type.id,"private bullet types registered extra IDs");
            a.setType((mindustry.type.UnitType)get.invoke(null,tiers.getEnumConstants()[index],neutral,pink));
            Events.fire(Trigger.afterGameUpdate);
            nearColour(((LaserBoltBulletType)shot.type).backColor,Color.valueOf("30EE70"),"in-flight shot changed with later engine colour");
            nearColour(flash.color,Color.valueOf("30EE70"),"existing flash repainted");
            nearColour(smoke.color,Color.valueOf("30EE70"),"existing smoke repainted");
            EffectState pinkFlash = effect(original.shootEffect, a, original.hitColor);
            Events.fire(Trigger.preDraw);
            nearColour(pinkFlash.color,Color.valueOf("F7E2E1"),"new flash palette missing");
            a.setType((mindustry.type.UnitType)get.invoke(null,tiers.getEnumConstants()[index],neutral,nativeEngine));
            Bullet reset=original.create(a,Team.sharded,220,200,0);
            Events.fire(Trigger.afterGameUpdate);
            check(reset.type==original,"default engine did not restore native projectile");
            EffectState defaultFlash = effect(original.shootEffect, a, original.hitColor);
            Events.fire(Trigger.preDraw);
            nearColour(defaultFlash.color,originalHit,"default flash colour changed");
            a.setType((mindustry.type.UnitType)get.invoke(null,tiers.getEnumConstants()[index],neutral,green));
            Events.fire(Trigger.preDraw);
            nearColour(defaultFlash.color,originalHit,"existing default flash repainted");
            flash.remove();smoke.remove();nativeFlash.remove();unrelatedEffect.remove();pinkFlash.remove();defaultFlash.remove();
            Bullet unrelated=((mindustry.world.blocks.defense.turrets.ItemTurret)Blocks.duo).ammoTypes.get(Items.copper).create(b,Team.sharded,220,240,0);
            Events.fire(Trigger.afterGameUpdate);
            check(unrelated.type==((mindustry.world.blocks.defense.turrets.ItemTurret)Blocks.duo).ammoTypes.get(Items.copper),"unrelated projectile recoloured");
            nearColour(original.backColor,originalBack,"shared projectile body changed");
            nearColour(original.trailColor,originalTrail,"shared projectile trail changed");
            nearColour(original.hitColor,originalHit,"shared projectile impact changed");
        }
        check(Vars.content.bullets().size==bulletTypes,"palette changes registered bullet types");
        check(Vars.content.units().count(t -> t.name.startsWith("modular-shipyard-ship-"))==3,"palette changes registered hulls");
        System.out.println("PASS: native Alpha/Beta/Gamma firing, independent engine-coloured muzzle flashes/smoke/bolts/trails/impacts/lights, preserved damage/velocity/homing/mounts, in-flight palette retention and vanilla defaults");
    }
    private static EffectState effect(mindustry.entities.Effect fx, Unit owner, Color colour){
        // Headless Effect.at intentionally skips visuals; exercise the native effect state.
        EffectState effect = EffectState.create();
        effect.effect=fx;effect.data=owner;effect.color.set(colour);effect.lifetime=fx.lifetime;effect.add();
        return effect;
    }
    private static void nearColour(Color a,Color b,String message){ near(a.r,b.r,message);near(a.g,b.g,message);near(a.b,b.b,message); }
    private static void near(float a,float b,String message){check(Math.abs(a-b)<.001f,message+" "+a+" vs "+b);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
