package shipyard;

import arc.graphics.Color;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.gen.Unit;
import mindustry.game.Team;
import mindustry.graphics.Pal;

/** Native firing cycles rather than effect-field existence: early, middle and final reload ticks. */
final class ReloadWorkChecks{
    static void run()throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var guns=loader.loadClass("shipyard.content.ShipWeapons");
        var reloading=guns.getMethod("reloading",mindustry.entities.units.WeaponMount.class);
        var intensity=guns.getMethod("reloadIntensity",Unit.class,mindustry.entities.units.WeaponMount.class);
        Color accentBefore=Pal.accent.cpy(),removeBefore=Pal.remove.cpy();
        var highlight=loader.loadClass("shipyard.graphics.EngineHighlights").getMethod("colour",Color.class,float.class);
        Color dark=Color.valueOf("101820"),original=dark.cpy();
        Color pale=(Color)highlight.invoke(null,dark,.2f);
        check(pale.r>dark.r+.2f && pale.g>dark.g+.2f && pale.b>dark.b+.2f,"dark activity highlight is unreadable");
        check(dark.equals(original) && pale!=dark,"highlight changed the selected engine colour");
        Color bright=Color.valueOf("AAFFFF");
        check(((Color)highlight.invoke(null,bright,.2f)).equals(bright.cpy().lerp(Color.white,.2f)),"bright palettes changed their highlight strength");
        for(int tier=0;tier<3;tier++)for(String weapon:new String[]{"railgun","asymptote"}){
            Unit ship=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            ship.set(300,300);ship.rotation=0;ship.add();ExperimentalChecks.equip(ship,tier,weapon);
            for(var mount:ship.mounts)mount.reload=0f;
            var mount=ship.mounts[0];
            check(!(boolean)reloading.invoke(null,mount),"fresh gun has a fake reload animation");
            ExperimentalChecks.fire(ship,400,300,weapon.equals("railgun")?65:5);
            check((boolean)reloading.invoke(null,mount),"actual shot did not start "+weapon+" reload");
            if(weapon.equals("railgun")){
                float remaining=mount.reload,rate=ship.reloadMultiplier;
                for(float multiplier:new float[]{.5f,1f,1.5f}){
                    ship.reloadMultiplier=multiplier;
                    mount.reload=12f*multiplier;
                    check(Math.abs((float)intensity.invoke(null,ship,mount)-.5f)<.001f,"steam fade ignored reload modifier");
                    mount.reload=.24f*multiplier;
                    check((float)intensity.invoke(null,ship,mount)<.011f,"steam still opaque at readiness");
                }
                ship.reloadMultiplier=rate;mount.reload=remaining;
            }
            float before=mount.reload;
            ExperimentalChecks.equip(ship,tier,weapon);mount=ship.mounts[0];
            check((boolean)reloading.invoke(null,mount) && Math.abs(mount.reload-before)<.01f,"same-weapon rebind lost reload decoration");
            ship.isShooting=false;for(var gun:ship.mounts)gun.shoot=false;
            int frames=0;
            while(mount.reload>Time.delta*ship.reloadMultiplier){
                check((boolean)reloading.invoke(null,mount),"reload decoration ended early at "+mount.reload+" for "+weapon);
                Time.update();for(var gun:ship.mounts)gun.weapon.update(ship,gun);frames++;
                check(frames<220,"reload never finished");
            }
            check(frames>35,"fixture did not cover a full cooldown");
            Time.update();for(var gun:ship.mounts)gun.weapon.update(ship,gun);
            check(!(boolean)reloading.invoke(null,mount),"reload decoration remained after readiness");
            check(!ship.type.drawBuildBeam,"custom work beam also draws the vanilla build beam");
            check(ship.type.engineColor.equals(Color.valueOf("30EE70")),"private Engine palette changed");
            ship.remove();
        }
        check(Pal.accent.equals(accentBefore) && Pal.remove.equals(removeBefore),"work beams mutated a global palette");
        System.out.println("PASS: continuous Vector/Asymptote reload from shot through readiness on all tiers; private work-beam selection and unchanged global palettes");
    }
    static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
