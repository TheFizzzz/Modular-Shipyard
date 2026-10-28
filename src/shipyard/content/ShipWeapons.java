package shipyard.content;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.Seq;
import shipyard.type.NativeFields;
import mindustry.content.*;
import mindustry.ctype.Content;
import mindustry.entities.Effect;
import mindustry.entities.Mover;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.type.Weapon;
import mindustry.world.blocks.defense.turrets.*;
import shipyard.model.*;
import shipyard.graphics.WeaponVisuals;

/** Fixed projectile/utility marker IDs. Palette clones and private guns never register content. */
public final class ShipWeapons{
    private static Flame flame;
    private static Shrapnel shrapnel;
    private static UtilityBullet divergence, invariant, operator, eigen;
    private static final Effect flameShoot = new Effect(32f, 80f, e -> {
        Draw.color(e.color, Color.gray, e.fin());
        Angles.randLenVectors(e.id, 12, e.finpow() * 60f, e.rotation, 10f,
            (x,y) -> Fill.circle(e.x+x,e.y+y,.65f+e.fout()*1.5f));
    }).followParent(false);
    static Effect charge(float duration){ return new Effect(duration, 100f, e -> {
        Draw.color(e.color);
        Lines.stroke(.5f + e.fin());
        Lines.circle(e.x,e.y, (1f-e.fin())*8f);
        Fill.circle(e.x,e.y,e.fin()*2f);
    }){
        @Override public void create(float x,float y,float rotation,Color color,Object effectData){
            if(effectData instanceof Unit owner) color=owner.type.weapons.first().bullet.hitColor;
            super.create(x,y,rotation,color,effectData);
        }
    }; }
    private static final Effect charge = charge(40f);
    private ShipWeapons(){}
    public static void init(){
        if(!mindustry.Vars.headless)arc.Events.on(mindustry.game.EventType.ClientLoadEvent.class,event->{
            // Decode these short cues ahead of first use, without playing them.
            for(var sound:new arc.audio.Sound[]{Sounds.chargeLancer,Sounds.shootForeshadow,Sounds.shootBeamPlasmaSmall,Sounds.shootSap,Sounds.loopSteam,Sounds.loopCircuit})
                sound.checkLazyLoad(()->{});
        });
    }
    public static void load(){
        // Preserve the retired laser adapter ID for old in-flight projectiles.
        flame=new Flame();new Laser();shrapnel=new Shrapnel();AdvancedWeapons.load();divergence=new UtilityBullet();invariant=new UtilityBullet();ExperimentalWeapons.load();SuperfactorialWeapons.load();InvolutionWeapons.load();operator=new UtilityBullet();AdvancedWeapons.loadBomblets();eigen=new UtilityBullet();eigen.damage=12f*shipyard.service.EigenService.damageShare;eigen.armorMultiplier=shipyard.service.EigenService.damageShare;eigen.rangeOverride=shipyard.service.EigenService.range;EigenContent.load();shipyard.graphics.CausticVisuals.load(); }
    public static BulletType source(ShipWeapon weapon){
        return switch(weapon){
            case flamethrower -> ((ItemTurret)Blocks.scorch).ammoTypes.get(Items.coal);
            case lancer -> InvolutionWeapons.template();
            case fuse -> ((ItemTurret)Blocks.fuse).ammoTypes.get(Items.titanium);
            case railgun, chain, missiles -> AdvancedWeapons.template(weapon);
            case caustic, asymptote -> ExperimentalWeapons.template(weapon);
            case superfactorial -> SuperfactorialWeapons.template();
            case divergence -> divergence;
            case invariant -> invariant;
            case operator -> operator;
            case eigen -> eigen;
            default -> throw new IllegalArgumentException("Standard weapon is tier-specific");
        };
    }
    private static Color colour(ShipWeapon weapon, ShipCosmetics cosmetics){
        if(cosmetics.engine()!=-1) return ShipCosmetics.colour(cosmetics.engine());
        return switch(weapon){
            case flamethrower -> Pal.lightFlame.cpy();
            case railgun -> Pal.orangeSpark.cpy();
            case missiles -> Pal.missileYellowBack.cpy();
            default -> Pal.lancerLaser.cpy();
        };
    }
    public static Seq<Weapon> weapons(ShipTier tier, ShipWeapon kind, ShipCosmetics cosmetics){
        if(kind==ShipWeapon.standard) return tier.base().weapons;
        if(kind.utility() || kind==ShipWeapon.eigen){
            BulletType marker=(kind==ShipWeapon.eigen?eigen:kind==ShipWeapon.divergence?divergence:kind==ShipWeapon.invariant?invariant:operator).painted(colour(kind,cosmetics),kind==ShipWeapon.eigen?shipyard.service.EigenService.range:UtilityBalance.range(tier,kind));
            if(kind==ShipWeapon.eigen)marker.damage*=WeaponBalance.damageFactor(tier,kind);
            Seq<Weapon> guns=new Seq<>();
            for(Weapon base:tier.base().weapons){
                Gun gun=kind==ShipWeapon.eigen?new DroneGun(base):new Gun(base){@Override public void update(Unit unit,WeaponMount mount){}};
                gun.bullet=marker;gun.reload=kind==ShipWeapon.divergence?UtilityBalance.pulseReload(tier):0f;
                gun.alternate=false;guns.add(gun);
            }
            return guns;
        }
        Color color=colour(kind,cosmetics);
        BulletType bullet=switch(kind){
            case flamethrower -> flame.painted(color);
            case lancer -> InvolutionWeapons.painted(color);
            case fuse -> shrapnel.painted(color);
            case railgun, chain, missiles -> AdvancedWeapons.painted(kind,color);
            case caustic, asymptote -> ExperimentalWeapons.painted(kind,color);
            case superfactorial -> SuperfactorialWeapons.painted(color);
            default -> throw new IllegalArgumentException();
        };
        // Ship weapons may hit air as well as ground; vanilla turret ammo stays untouched.
        bullet.collidesAir=true;
        // Keep individual matchups useful without unlimited formation-wide damage.
        bullet.pierceCap=switch(kind){case flamethrower,lancer -> 2;case railgun -> 1;case fuse -> 1;default -> bullet.pierceCap;};
        float factor=WeaponBalance.damageFactor(tier,kind);
        bullet.damage*=factor;bullet.splashDamage*=factor;
        if(kind==ShipWeapon.missiles)AdvancedWeapons.scaleFragments(bullet,factor);
        Seq<Weapon> guns=new Seq<>();
        for(Weapon base : tier.base().weapons){
            Gun gun=kind==ShipWeapon.caustic?new CausticGun(base):kind==ShipWeapon.superfactorial?new SpoolGun(base):kind==ShipWeapon.railgun || kind==ShipWeapon.asymptote?new ReloadGun(base,kind):new Gun(base);
            gun.bullet=bullet;
            gun.reload=reload(kind);
            gun.shoot=kind==ShipWeapon.fuse ? new ShootSpread(2,20f) : new ShootPattern();
            if(kind==ShipWeapon.railgun) gun.shoot.firstShotDelay=60f;
            gun.shootSound=switch(kind){case flamethrower -> Sounds.shootFlame;case railgun -> Sounds.shootForeshadow;case lancer -> Sounds.shootLaser;case chain -> Sounds.shootArc;case missiles, asymptote -> Sounds.shootMissile;case caustic -> Sounds.beamPlasmaSmall;case superfactorial -> Sounds.shootDuo;default -> Sounds.shootFuse;};
            gun.chargeSound=Sounds.chargeLancer;
            if(kind==ShipWeapon.railgun){gun.soundPitchMin=.9f;gun.soundPitchMax=1f;}
            gun.parentizeEffects=true;
            gun.recoil=kind==ShipWeapon.flamethrower ? 0f : 1f;
            gun.alternate=kind==ShipWeapon.lancer;
            gun.inaccuracy=kind==ShipWeapon.superfactorial?24f:0f;
            guns.add(gun);
        }
        return guns;
    }
    public static float reload(ShipWeapon kind){ return switch(kind){case flamethrower->6f;case lancer->80f;case fuse->35f;case railgun->180f;case chain->40f;case missiles->45f;case caustic->30f;case asymptote->100f;case superfactorial->30f;default->0f;}; }
    public static float range(ShipTier tier, ShipWeapon kind){ if(kind.utility())return UtilityBalance.range(tier,kind);return kind==ShipWeapon.standard ? tier.base().weapons.first().range() : source(kind).range; }
    public static float damage(ShipTier tier, ShipWeapon kind){ if(kind.utility())return 0f;return (kind==ShipWeapon.standard ? tier.base().weapons.first().bullet : source(kind)).damage * WeaponBalance.damageFactor(tier,kind); }

    /** Bind private definitions before native Weapon.update and instant ray damage. */
    public static void bind(Unit unit){
        var guns=unit.type.weapons;
        boolean different=unit.mounts.length!=guns.size;
        if(!different) for(int i=0;i<guns.size;i++) if(unit.mounts[i].weapon!=guns.get(i)){ different=true;break; }
        if(!different) return;
        WeaponMount[] previous=unit.mounts, mounts=new WeaponMount[guns.size];
        for(int i=0;i<mounts.length;i++){
            Weapon weapon=guns.get(i);
            WeaponMount mount=weapon.mountType.get(weapon);
            if(i<previous.length){
                WeaponMount old=previous[i];
                mount.reload=old.reload;mount.rotation=old.rotation;mount.targetRotation=old.targetRotation;
                mount.aimX=old.aimX;mount.aimY=old.aimY;mount.shoot=old.shoot;mount.rotate=old.rotate;
                mount.side=old.side;mount.recoil=old.recoil;mount.heat=old.heat;mount.totalShots=old.totalShots;mount.barrelCounter=old.barrelCounter;
                if(mount instanceof ReloadMount fresh && old instanceof ReloadMount prior
                    && ((ReloadGun)mount.weapon).kind==((ReloadGun)old.weapon).kind)fresh.cooling=prior.cooling;
            }
            mounts[i]=mount;
        }
        for(WeaponMount old:previous){
            if(old.weapon.continuous){
                if(old.weapon instanceof CausticGun)CausticGun.off(unit,old);
                if(old.bullet!=null && old.bullet.owner==unit)old.bullet.remove();
            }
            if(old.sound!=null)old.sound.stop();
        }
        unit.mounts=mounts;
    }
    /** Stable non-firing marker IDs keep utility mounts compatible with native unit codecs. */
    private static final class UtilityBullet extends BulletType{
        UtilityBullet(){super(0f,0f);rangeOverride=100f;collides=false;hittable=false;absorbable=false;shootEffect=smokeEffect=hitEffect=despawnEffect=Fx.none;}
        BulletType painted(Color color,float reach){try{UtilityBullet b=(UtilityBullet)clone();tint(b,color);b.range=b.rangeOverride=reach;return b;}catch(CloneNotSupportedException e){throw new AssertionError(e);}}
    }
    private static class Gun extends Weapon{
        Gun(Weapon source){ super(source.name);NativeFields.copy(source,this,Weapon.class,Object.class); }
        @Override protected void bullet(Unit unit, WeaponMount mount,float x,float y,float angle,Mover mover){
            // Delayed charge callbacks must not fire a gun that has since been removed.
            boolean current=false;for(WeaponMount candidate : unit.mounts) if(candidate==mount){current=true;break;}
            if(current && mount.weapon==this) super.bullet(unit,mount,x,y,angle,mover);
        }
    }
    private static final class ReloadMount extends WeaponMount{
        boolean cooling;
        ReloadMount(Weapon weapon){super(weapon);}
    }
    public static boolean reloading(WeaponMount mount){
        return mount instanceof ReloadMount state && state.cooling && mount.reload>0f && !mount.charging;
    }
    /** Fade the reload's final 0.4s (Vector) or 0.2s (Asymptote), including reload modifiers. */
    public static float reloadIntensity(Unit unit,WeaponMount mount){
        if(!reloading(mount))return 0f;
        float fade=((ReloadGun)mount.weapon).kind==ShipWeapon.railgun?24f:12f;
        return Mathf.clamp(mount.reload/Math.max(.05f,unit.reloadMultiplier)/fade);
    }
    /** Render after both native and recoloured gun sprites; previews have no live reload. */
    public static void drawReload(Unit unit){
        if(!unit.isAdded())return;
        float z=Draw.z();
        for(var mount:unit.mounts)if(reloading(mount)){
            Weapon gun=mount.weapon;
            Draw.z(z+Math.max(0f,gun.layerOffset)+.01f);
            float rotation=unit.rotation-90f,angle=rotation+(gun.rotate?mount.rotation:gun.baseRotation);
            float recoil=Mathf.pow(mount.recoil,gun.recoilPow)*gun.recoil;
            float x=unit.x+Angles.trnsx(rotation,gun.x,gun.y)+Angles.trnsx(angle,gun.shootX,gun.shootY-recoil);
            float y=unit.y+Angles.trnsy(rotation,gun.x,gun.y)+Angles.trnsy(angle,gun.shootX,gun.shootY-recoil);
            WeaponVisuals.reload(((ReloadGun)gun).kind,x,y,angle+90f,gun.bullet.hitColor,
                arc.util.Time.time+unit.id*7f+gun.x*3f,reloadIntensity(unit,mount));
        }
        Draw.z(z);
    }
    private static final class ReloadGun extends Gun{
        final ShipWeapon kind;
        ReloadGun(Weapon source,ShipWeapon kind){super(source);this.kind=kind;mountType=ReloadMount::new;}
        @Override public void update(Unit unit,WeaponMount mount){
            // Native non-continuous weapons fade mount.sound each update. This private
            // reload loop owns that update, while retaining native removal cleanup.
            var vent=mount.sound;
            if(vent!=null)mount.sound=null;
            try{super.update(unit,mount);}finally{if(vent!=null)mount.sound=vent;}
            if(mindustry.Vars.headless || unit.mounts.length==0 || unit.mounts[0]!=mount)return;
            boolean venting=unit.isAdded() && reloading(mount);
            if(venting && mount.sound==null)mount.sound=new mindustry.audio.SoundLoop(
                kind==ShipWeapon.railgun?Sounds.loopSteam:Sounds.loopCircuit,1f);
            if(mount.sound!=null)mount.sound.update(unit.x,unit.y,venting,reloadIntensity(unit,mount));
        }
        @Override protected void shoot(Unit unit,WeaponMount mount,float x,float y,float angle){
            ((ReloadMount)mount).cooling=false;
            if(kind==ShipWeapon.railgun)shipyard.service.VectorReload.charging(unit,reload);
            super.shoot(unit,mount,x,y,angle);
        }
        @Override protected void handleBullet(Unit unit,WeaponMount mount,Bullet shot){
            super.handleBullet(unit,mount,shot);
            ((ReloadMount)mount).cooling=true;
        }
    }
    /** EigenService owns launch timing; native gun mounts retain their recoil and heat visuals. */
    private static final class DroneGun extends Gun{
        DroneGun(Weapon source){super(source);}
        @Override public void update(Unit unit,WeaponMount mount){
            mount.recoil=Math.max(0f,mount.recoil-arc.util.Time.delta/Math.max(1f,recoilTime));
            mount.heat=Math.max(0f,mount.heat-arc.util.Time.delta/Math.max(1f,cooldownTime));
        }
    }
    /** Spin is transient per mount: never alter the shared weapon or a player's status multipliers. */
    private static final class SpoolMount extends WeaponMount{
        float held,interval=30f;
        SpoolMount(Weapon weapon){super(weapon);}
    }
    private static final class SpoolGun extends Gun{
        SpoolGun(Weapon source){super(source);mountType=SpoolMount::new;}
        @Override protected void handleBullet(Unit unit,WeaponMount mount,Bullet shot){
            super.handleBullet(unit,mount,shot);
            float spin=((SpoolMount)mount).held/180f;
            SuperfactorialWeapons.penetration(shot,spin);
            WeaponVisuals.spoolShot.at(shot.x,shot.y,shot.rotation(),shot.type.hitColor,Float.valueOf(spin));
        }
        @Override public void draw(Unit unit,WeaponMount mount){
            super.draw(unit,mount);
            SpoolMount spin=(SpoolMount)mount;
            if(spin.held<=0f)return;
            float rotation=unit.rotation-90f,weaponRotation=rotation+(rotate?mount.rotation:baseRotation);
            float realRecoil=Mathf.pow(mount.recoil,recoilPow)*recoil;
            float mx=unit.x+Angles.trnsx(rotation,x,y)+Angles.trnsx(weaponRotation,shootX,shootY-realRecoil);
            float my=unit.y+Angles.trnsy(rotation,x,y)+Angles.trnsy(weaponRotation,shootX,shootY-realRecoil);
            WeaponVisuals.spoolGlow(mx,my,weaponRotation+90f,bullet.hitColor,spin.held/180f,arc.util.Time.time);
        }
        @Override public void update(Unit unit,WeaponMount mount){
            SpoolMount spin=(SpoolMount)mount;
            boolean held=unit.isAdded() && unit.isShooting && mount.shoot && unit.canShoot();
            spin.held=held?Math.min(180f,spin.held+arc.util.Time.delta):0f;
            float progress=spin.held/180f;
            float interval=reload/Mathf.pow(15f,progress*progress);
            // Preserve fractional reload progress as the firing interval accelerates or resets.
            mount.reload*=interval/spin.interval;spin.interval=interval;
            float carry=mount.reload>0f?Math.min(0f,mount.reload-arc.util.Time.delta*unit.reloadMultiplier):0f;
            int before=mount.totalShots;
            super.update(unit,mount);
            // Carry a partial tick so small Fire Rate upgrades still help at rapid intervals.
            if(mount.totalShots!=before)mount.reload=Math.max(0f,interval+carry);
        }
    }
    public static void resetSpin(Unit unit){
        for(var mount:unit.mounts){
            if(mount.weapon instanceof CausticGun)CausticGun.off(unit,mount);
            if(mount instanceof SpoolMount spin){
                mount.reload*=30f/spin.interval;spin.held=0f;spin.interval=30f;
            }
        }
    }
    /** Native continuous mounts, independently aimed at one common, bounded focal point. */
    private static final class CausticMount extends WeaponMount{
        boolean emitting;
        CausticMount(Weapon weapon){super(weapon);}
    }
    private static final class CausticGun extends Gun{
        CausticGun(Weapon source){
            super(source);continuous=alwaysContinuous=true;mountType=CausticMount::new;
            shootWarmupSpeed=1f;minWarmup=0f;recoil=0f;shootSoundVolume=.8f;
        }
        private void focus(Unit unit,WeaponMount mount,arc.math.geom.Vec2 out){
            var aim=shipyard.service.WeaponMotion.remote(unit);
            out.set((aim==null?mount.aimX:aim.x)-unit.x,(aim==null?mount.aimY:aim.y)-unit.y);
            if(out.len2()<.001f)out.trns(unit.rotation,16f);
            else out.setLength(Mathf.clamp(out.len(),16f,ExperimentalWeapons.focusRange));
            out.add(unit.x,unit.y);
        }
        @Override protected float bulletRotation(Unit unit,WeaponMount mount,float x,float y){
            arc.math.geom.Vec2 point=new arc.math.geom.Vec2();focus(unit,mount,point);
            return Angles.angle(x,y,point.x,point.y);
        }
        private static void off(Unit unit,WeaponMount mount){
            if(mount instanceof CausticMount state && state.emitting){
                state.emitting=false;
                if(unit.mounts.length>0 && unit.mounts[0]==mount)
                    WeaponVisuals.sound(Sounds.shootSap,unit.x,unit.y,1.6f,.65f);
            }
        }
        @Override public void update(Unit unit,WeaponMount mount){
            var aim=shipyard.service.WeaponMotion.remote(unit);
            if(aim!=null){mount.shoot=aim.shooting;mount.aimX=aim.x;mount.aimY=aim.y;}
            CausticMount state=(CausticMount)mount;
            if(state.emitting && mount.bullet!=null && !shipyard.service.WeaponMotion.shooting(unit)){
                arc.math.geom.Vec2 last=new arc.math.geom.Vec2();
                if(!shipyard.service.WeaponMotion.finalAim(unit,last))last.set(mount.aimX,mount.aimY);
                last.sub(unit.x,unit.y);
                if(last.len2()<.001f)last.trns(unit.rotation,16f);
                else last.setLength(Mathf.clamp(last.len(),16f,ExperimentalWeapons.focusRange));
                last.add(unit.x,unit.y);
                ExperimentalWeapons.finish(mount.bullet,last.x,last.y);
            }
            // Native continuous Weapon.update otherwise hardcodes a loop volume of 1.
            if(!mindustry.Vars.headless && mount.sound==null && mount.bullet!=null && mount.bullet.isAdded())
                mount.sound=new mindustry.audio.SoundLoop(shootSound,shootSoundVolume);
            float cone=shootCone;
            try{
                // Remote aim arrives ahead of the smoothly interpolated hull rotation. Do not gate
                // a presentation-only continuous beam on that older orientation.
                if(aim!=null)shootCone=360f;
                super.update(unit,mount);
            }finally{shootCone=cone;}
            if(mount.bullet!=null && mount.bullet.isAdded()){
                if(!mount.shoot || !shipyard.service.WeaponMotion.shooting(unit)){
                    mount.bullet.remove();mount.bullet=null;
                    if(mount.sound!=null)mount.sound.update(unit.x,unit.y,false);
                }else{
                    arc.math.geom.Vec2 point=new arc.math.geom.Vec2();focus(unit,mount,point);
                    mount.bullet.aimX=point.x;mount.bullet.aimY=point.y;
                    mount.bullet.rotation(Angles.angle(mount.bullet.x,mount.bullet.y,point.x,point.y));
                }
            }
            boolean emitting=mount.bullet!=null && mount.bullet.isAdded();
            if(emitting && !state.emitting){
                state.emitting=true;
                if(!mindustry.Vars.headless && !unit.inFogTo(mindustry.Vars.player.team()))
                    shipyard.graphics.CausticVisuals.ignition.at(mount.bullet.x,mount.bullet.y,mount.bullet.rotation(),bullet.hitColor,unit);
                if(unit.mounts.length>0 && unit.mounts[0]==mount)
                    WeaponVisuals.sound(Sounds.shootBeamPlasmaSmall,unit.x,unit.y,1.15f,.8f);
            }else if(!emitting)off(unit,mount);
        }
    }

    private static void borrow(BulletType target,ShipWeapon kind,Class<?> type){ NativeFields.copy(source(kind),target,type,Content.class); }
    private static void tint(BulletType bullet,Color color){ bullet.hitColor=color.cpy();bullet.trailColor=bullet.lightColor=bullet.hitColor; }
    private static final class Flame extends BulletType{
        @Override public void draw(Bullet b){super.draw(b);WeaponVisuals.flame(b);}
        @Override public void init(){ borrow(this,ShipWeapon.flamethrower,BulletType.class);shootEffect=flameShoot;hitEffect=WeaponVisuals.flameHit; }
        @Override public void load(){ init(); }
        @Override public void hitEntity(Bullet bullet,Hitboxc target,float health){
            // Physical collisions can collect several overlapping units in the same tick.
            if(pierceCap<0 || bullet.collided.size<=pierceCap)super.hitEntity(bullet,target,health);
        }
        BulletType painted(Color color){try{Flame b=(Flame)clone();tint(b,color);return b;}catch(CloneNotSupportedException e){throw new AssertionError(e);}}
    }
    private static final class Laser extends LaserBulletType{
        @Override public void init(){ NativeFields.copy(((PowerTurret)Blocks.lancer).shootType,this,LaserBulletType.class,Content.class);chargeEffect=charge;shootEffect=Fx.shootSmallColor;smokeEffect=laserEffect=Fx.none;hitEffect=Fx.hitLaserColor; }
        @Override public void load(){ init(); }
    }
    private static final class Shrapnel extends ShrapnelBulletType{
        @Override public void init(){ borrow(this,ShipWeapon.fuse,ShrapnelBulletType.class);shootEffect=Fx.shootSmallColor;smokeEffect=Fx.none;hitEffect=Fx.hitLaserColor; }
        @Override public void load(){ init(); }
        BulletType painted(Color color){try{Shrapnel b=(Shrapnel)clone();tint(b,color);b.fromColor=Color.white.cpy();b.toColor=color.cpy();return b;}catch(CloneNotSupportedException e){throw new AssertionError(e);}}
    }
}
