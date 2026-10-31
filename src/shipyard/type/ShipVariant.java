package shipyard.type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import shipyard.model.ShipCosmetics;
import mindustry.type.UnitType;
import mindustry.entities.bullet.BulletType;
import shipyard.graphics.HullPaint;
import shipyard.service.ProjectileColours;
import mindustry.gen.Unit;
import shipyard.model.ShipLoadout;
import shipyard.model.ShipTier;

/** Stable registered hull and private stat views; shared rendering/weapons stay read-only. */
public final class ShipVariant extends UnitType implements Cloneable{
    public final ShipTier tier;
    private ShipLoadout loadout = ShipLoadout.defaults;
    private boolean legacy;
    private ShipCosmetics cosmetics = ShipCosmetics.defaults;
    private Color hullTint, accentTint, workLight;
    private BulletType projectile;
    private mindustry.entities.units.BuildPlan lastBuildPlan;

    public ShipVariant(ShipTier tier){
        super("ship-" + tier.name() + "-hull");
        this.tier = tier;
        hidden = true;
        hideDatabase = true;
        generateIcons = false;
    }

    public ShipLoadout loadout(){
        return loadout;
    }

    public ShipCosmetics cosmetics(){ return cosmetics; }
    public Color workLight(){return workLight;}

    public boolean legacy(){
        return legacy;
    }

    /** Object.clone does not run Content's constructor or register another content type. */
    public ShipVariant view(ShipLoadout loadout, boolean legacy){
        return view(loadout, ShipCosmetics.defaults, legacy);
    }

    public ShipVariant view(ShipLoadout loadout, ShipCosmetics colours, boolean legacy){
        try{
            ShipVariant variantView = (ShipVariant)super.clone();
            variantView.loadout = loadout;
            variantView.cosmetics = colours;
            variantView.projectile = null;
            variantView.lastBuildPlan = null;
            variantView.legacy = legacy;
            variantView.stats = new mindustry.world.meta.Stats();
            variantView.applyStats();
            return variantView;
        }catch(CloneNotSupportedException impossible){
            throw new AssertionError(impossible);
        }
    }

    /**
     * Vanilla types precede mod types in content initialization/loading. Copy their fully
     * initialized UnitType fields, never Content identity. Do not run UnitType.init/load
     * again: those mutate shared weapons, parts and engine collections. This adapter is
     * intentionally pinned to v160.2 and covered by real-engine tests.
     */
    private void borrowBase(){
        UnitType base = tier.base();
        NativeFields.copy(base,this,UnitType.class,UnitType.class.getSuperclass());
        applyStats();
        hidden = true;
        localizedName = base.localizedName;
        uiIcon = base.uiIcon;
        fullIcon = base.fullIcon;
    }

    private void applyStats(){
        UnitType base = tier.base();
        speed = loadout.speed(base);
        itemCapacity = loadout.capacity(base);
        buildSpeed = loadout.buildSpeed(base);
        buildRange = loadout.buildRange(base);
        mineSpeed = loadout.mineSpeed(base);
        health = loadout.health(base);
        weapons = shipyard.content.ShipWeapons.weapons(tier, loadout.weapon(), cosmetics);
        if(loadout.weapon() != shipyard.model.ShipWeapon.standard){
            range = maxRange = weapons.first().range();
            targetAir = weapons.first().bullet.collidesAir;
        }
        if(loadout.weapon().utility())clipSize=Math.max(base.clipSize,shipyard.content.ShipWeapons.range(tier,loadout.weapon())*3f);
        hullTint = cosmetics.hull() == -1 ? null : ShipCosmetics.colour(cosmetics.hull());
        accentTint = cosmetics.accents() == -1 ? null : ShipCosmetics.colour(cosmetics.accents());
        engineColor = cosmetics.engine() == -1 ? base.engineColor : ShipCosmetics.colour(cosmetics.engine());
        workLight=engineColor==null?null:shipyard.graphics.EngineHighlights.colour(engineColor,0f);
        engineColorInner = cosmetics.engine() == -1 ? base.engineColorInner : engineColor.cpy().lerp(Color.white, .65f);
        drawBuildBeam = cosmetics.engine() == -1 && base.drawBuildBeam;
        trailColor = cosmetics.engine() == -1 ? base.trailColor : engineColor;
        hidden = true;
        localizedName = base.localizedName;
        uiIcon = base.uiIcon;
        fullIcon = base.fullIcon;
    }

    public BulletType projectile(BulletType original){
        if(original != tier.base().weapons.first().bullet) return original;
        if(projectile == null) projectile = ProjectileColours.painted(this, original);
        return projectile;
    }

    @Override
    public void draw(Unit unit){
        if(cosmetics.engine()!=-1 && unit.isAdded() && !unit.inFogTo(mindustry.Vars.player.team())){
            // Track the current queue target even out of range: native buildAlpha can remain
            // high while an unfinished plan is queued, so it cannot expire a completed target.
            if(unit.updateBuilding && unit.buildPlan()!=null)lastBuildPlan=unit.buildPlan();
            if(lastBuildPlan!=null && tier.base().drawBuildBeam)
                shipyard.graphics.WorkBeams.build(unit,lastBuildPlan);
            if(unit.buildAlpha<=.001f)lastBuildPlan=null;
        }
        super.draw(unit);
        if(!unit.inFogTo(mindustry.Vars.player.team()))shipyard.service.UtilityService.draw(unit);
    }

    @Override
    public void drawBody(Unit unit){
        if(hullTint == null && accentTint == null){ super.drawBody(unit); return; }
        applyColor(unit);
        HullPaint.rect(region, unit.x, unit.y, unit.rotation - 90f, hullTint, accentTint == null ? null : cellColor(unit));
        Draw.reset();
    }

    @Override
    public void drawWeapons(Unit unit){
        // Divergence is emitted by the hull, with no physical gun sprites or heat overlays.
        if(loadout.weapon()==shipyard.model.ShipWeapon.divergence)return;
        if(hullTint == null && accentTint == null)super.drawWeapons(unit);
        else{
            applyColor(unit);
            for(var mount : unit.mounts) HullPaint.weapon(unit, mount, hullTint, accentTint == null ? null : cellColor(unit));
            Draw.reset();
        }
        shipyard.content.ShipWeapons.drawReload(unit);
    }

    @Override
    public void drawMiningBeam(Unit unit,float x,float y){
        if(cosmetics.engine()==-1)super.drawMiningBeam(unit,x,y);
        else shipyard.graphics.WorkBeams.mine(unit,x,y,engineColor);
    }

    @Override
    public Color cellColor(Unit unit){
        if(accentTint == null) return super.cellColor(unit);
        float fraction = Mathf.clamp(unit.healthf());
        return Tmp.c2.set(Color.black).lerp(accentTint,
            fraction + Mathf.absin(Time.time, Math.max(fraction * 5f, 1f), 1f - fraction));
    }

    @Override
    public void update(Unit unit){
        super.update(unit);
        // v160.2: native status multipliers reset before UnitType.update, then weapons
        // consume this multiplier. This composes with statuses without changing mounts,
        // bullets or shared Weapon definitions, and is recomputed identically on clients.
        unit.reloadMultiplier *= loadout.fireRateFactor();
        unit.healthMultiplier /= 1f-loadout.damageReduction();
    }

    @Override
    public void init(){
        borrowBase();
    }

    @Override
    public void loadIcon(){
        uiIcon = tier.base().uiIcon;
        fullIcon = tier.base().fullIcon;
    }

    @Override
    public void load(){
        borrowBase();
    }
}
