package shipyard.service;

import arc.Events;
import arc.struct.IntMap;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.*;
import mindustry.entities.bullet.BulletType;
import shipyard.model.ShipTier;
import shipyard.type.*;

/** Native shots remain native mechanics; tint snapshots belong to individual bullets. */
public final class ProjectileColours{
    private static final EngineBolt[] templates = new EngineBolt[3];
    // EffectState is pooled: retain the creation ID as well as the object identity.
    private static final IntMap<EffectState> observedEffects = new IntMap<>();
    private ProjectileColours(){}
    public static void load(){
        for(ShipTier tier : ShipTier.values()) templates[tier.ordinal()] = new EngineBolt(tier);
    }
    public static BulletType painted(ShipVariant hull, BulletType original){
        EngineBolt template = templates[hull.tier.ordinal()];
        return original == template.source() ? template.painted(hull.engineColor) : original;
    }
    public static void init(){
        Events.on(ResetEvent.class, event -> observedEffects.clear());
        Events.run(Trigger.afterGameUpdate, ProjectileColours::refresh);
        // Also covers paused/editor-created bullets before their first render.
        Events.run(Trigger.preDraw, ProjectileColours::refresh);
    }
    public static void refresh(){
        refreshMuzzleEffects();
        for(Bullet bullet : Groups.bullet){
            if(bullet.type instanceof EngineBolt) continue;
            if(bullet.owner instanceof Unit owner && owner.type instanceof ShipVariant hull && hull.cosmetics().engine() != -1){
                bullet.type = hull.projectile(bullet.type);
            }
        }
    }
    private static void refreshMuzzleEffects(){
        var entries = observedEffects.entries();
        while(entries.hasNext()){
            var entry = entries.next();
            if(!entry.value.isAdded() || entry.value.id != entry.key) entries.remove();
        }
        for(EffectState effect : Groups.effect){
            if(observedEffects.containsKey(effect.id) || !(effect.data instanceof Unit owner)) continue;
            ShipTier tier = ShipTier.of(owner.type);
            if(tier == null) continue;
            BulletType nativeShot = tier.base().weapons.first().bullet;
            BulletType gunShot = owner.type.weapons.first().bullet;
            boolean selected = owner.type instanceof ShipVariant hull && hull.loadout().weapon() != shipyard.model.ShipWeapon.standard;
            if(selected){
                if(effect.effect != gunShot.shootEffect && effect.effect != gunShot.smokeEffect && effect.effect != gunShot.chargeEffect) continue;
            }else if(effect.effect != nativeShot.shootEffect && effect.effect != nativeShot.smokeEffect) continue;
            // Observe native defaults too: later palette edits must not repaint old flashes.
            observedEffects.put(effect.id, effect);
            if(selected) effect.color.set(gunShot.hitColor);
            else if(owner.type instanceof ShipVariant hull && hull.cosmetics().engine() != -1){
                effect.color.set(hull.engineColor);
            }
            if((selected || owner.type instanceof ShipVariant hull && hull.cosmetics().engine()!=-1)
                && (effect.effect==nativeShot.shootEffect || effect.effect==nativeShot.smokeEffect || effect.effect==mindustry.content.Fx.shootSmallColor))
                effect.color.lerp(arc.graphics.Color.white,shipyard.graphics.EngineHighlights.mix(effect.color,0f));
        }
    }

}
