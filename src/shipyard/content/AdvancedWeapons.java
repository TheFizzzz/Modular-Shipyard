package shipyard.content;

import arc.graphics.Color;
import arc.math.geom.*;
import arc.struct.IntSet;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import shipyard.model.ShipWeapon;
import shipyard.graphics.WeaponVisuals;

/** Fixed templates; palette clones retain their content IDs and own their colour fields. */
final class AdvancedWeapons{
    private static Rail rail;
    private static Chain chain;
    private static Missile missile;
    private static Bomblet bomblet;
    private AdvancedWeapons(){}
    static void load(){ rail=new Rail();chain=new Chain();missile=new Missile(); }
    // Append after every existing adapter to preserve old projectile IDs.
    static void loadBomblets(){bomblet=new Bomblet();}
    static void scaleFragments(BulletType bullet,float factor){bullet.fragBullet.damage*=factor;bullet.fragBullet.splashDamage*=factor;}
    static BulletType template(ShipWeapon kind){
        return switch(kind){case railgun -> rail;case chain -> chain;case missiles -> missile;default -> throw new IllegalArgumentException();};
    }
    static BulletType painted(ShipWeapon kind,Color color){
        return switch(kind){case railgun -> rail.painted(color);case chain -> chain.painted(color);case missiles -> missile.painted(color);default -> throw new IllegalArgumentException();};
    }
    private static void tint(BulletType bullet,Color color){
        bullet.hitColor=color.cpy();bullet.trailColor=bullet.lightColor=bullet.hitColor;
    }

    private static final class Rail extends RailBulletType{
        Rail(){
            damage=240f;length=320f;pierceCap=1;pierceDamageFactor=1f;
            lifetime=22f;drawSize=700f;
            chargeEffect=WeaponVisuals.vectorCharge;
            shootEffect=Fx.shootSmallColor;smokeEffect=Fx.none;
            hitEffect=Fx.hitBulletColor;pierceEffect=Fx.none;lineEffect=WeaponVisuals.vectorLine;
        }
        @Override public void init(Bullet b){
            // Probe before native rail collision: never call Damage.linecast inside its hit callback.
            Healthc first=Damage.linecast(b,b.x,b.y,b.rotation(),length);
            float distance=first==null?0:b.dst(first),x=first==null?0:first.x(),y=first==null?0:first.y();
            if(first!=null){
                float progress=arc.math.Mathf.clamp((distance-64f)/128f);
                b.damage*=.4f+.6f*progress;
            }
            super.init(b);
            if(first!=null && distance>=192f)WeaponVisuals.vectorFarHit.at(x,y,b.rotation(),hitColor);
        }
        BulletType painted(Color color){
            try{Rail b=(Rail)clone();tint(b,color);return b;}catch(CloneNotSupportedException e){throw new AssertionError(e);}
        }
    }

    /** First hit requires aim. Additional jumps select distinct nearby enemies, with damage falloff. */
    private static final class Chain extends RailBulletType{
        Chain(){
            damage=36f;length=112f;pierceCap=1;pierceDamageFactor=0f;
            lifetime=20f;drawSize=420f;status=StatusEffects.shocked;statusDuration=30f;
            shootEffect=Fx.shootSmallColor;smokeEffect=Fx.none;
            hitEffect=Fx.none;pierceEffect=Fx.none;lineEffect=Fx.none;
        }
        @Override public void hitEntity(Bullet b,Hitboxc entity,float health){
            super.hitEntity(b,entity,health);
            WeaponVisuals.authoritative(WeaponVisuals.recurrenceHit,entity.x(),entity.y(),b.rotation(),hitColor,Float.valueOf(entity.hitSize()*.5f));
            if(entity instanceof Teamc target) b.data=target;
        }
        @Override public void hitTile(Bullet b,Building build,float x,float y,float health,boolean direct){
            super.hitTile(b,build,x,y,health,direct);
            if(build.team!=b.team){WeaponVisuals.authoritative(WeaponVisuals.recurrenceHit,x,y,b.rotation(),hitColor,Float.valueOf(build.hitSize()*.5f));b.data=build;}
        }
        @Override public void init(Bullet b){
            // Wait until native line collision has finished: Damage.collideLine uses shared scratch state.
            super.init(b);
            if(mindustry.Vars.net.client())return;
            if(!(b.data instanceof Teamc first)){
                WeaponVisuals.authoritative(WeaponVisuals.recurrenceArc,b.x,b.y,b.rotation(),hitColor,new Vec2().trns(b.rotation(),length).add(b.x,b.y));
                return;
            }
            WeaponVisuals.authoritative(WeaponVisuals.recurrenceArc,b.x,b.y,b.rotation(),hitColor,new Vec2(first.x(),first.y()));
            IntSet visited=new IntSet();visited.add(first.id());
            float x=first.x(),y=first.y();
            for(int jump=0;jump<3;jump++){
                Teamc target=Units.closestTarget(b.team,x,y,64f,
                    unit -> unit.hittable() && unit.checkTarget(collidesAir,collidesGround) && !visited.contains(unit.id),
                    build -> collidesGround && !visited.contains(build.id));
                if(target==null) break;
                visited.add(target.id());
                WeaponVisuals.authoritative(WeaponVisuals.recurrenceArc,x,y,0f,hitColor,new Vec2(target.x(),target.y()));
                b.damage*=.75f;
                if(target instanceof Unit unit){
                    hitEntity(b,unit,unit.health);

                }else if(target instanceof Building build){
                    build.collision(b);
                }
                x=target.x();y=target.y();
            }
        }
        BulletType painted(Color color){
            try{Chain b=(Chain)clone();tint(b,color);return b;}catch(CloneNotSupportedException e){throw new AssertionError(e);}
        }
    }

    private static final class Missile extends MissileBulletType{
        Missile(){
            super(2.8f,48f);
            lifetime=40f;width=8f;height=13f;keepVelocity=false;
            homingPower=.09f;homingRange=100f;homingDelay=6f;
            splashDamage=27f;splashDamageRadius=20f;
            fragOnHit=fragOnDespawn=fragOnAbsorb=false;fragBullets=3;
            fragRandomSpread=0f;fragSpread=40f;fragVelocityMin=fragVelocityMax=1f;
            fragLifeMin=fragLifeMax=1f;fragOffsetMin=fragOffsetMax=0f;
            trailLength=12;trailWidth=1.5f;trailChance=0f;
            shootEffect=Fx.shootSmallColor;smokeEffect=Fx.none;
            hitEffect=WeaponVisuals.convergeBlast;despawnEffect=Fx.none;
        }
        @Override public void draw(Bullet shot){super.draw(shot);WeaponVisuals.converge(shot,false);}
        @Override public void despawned(Bullet shot){
            if(shot.absorbed)return;
            super.despawned(shot);
            // Native despawn also runs on interception: split only on an unabsorbed timeout.
            if(!shot.absorbed && shot.time>=shot.lifetime-.001f && shot.frags==0){
                createFrags(shot,shot.x,shot.y);
                WeaponVisuals.convergeSplit.at(shot.x,shot.y,shot.rotation(),hitColor);
            }
        }
        BulletType painted(Color color){
            try{
                Missile b=(Missile)clone();tint(b,color);b.fragBullet=bomblet.painted(color);
                b.backColor=color.cpy();b.frontColor=shipyard.graphics.EngineHighlights.colour(color,.25f);
                return b;
            }catch(CloneNotSupportedException e){throw new AssertionError(e);}
        }
    }
    private static final class Bomblet extends MissileBulletType{
        Bomblet(){
            super(3.4f,8f);lifetime=60f;width=4f;height=7f;keepVelocity=false;
            homingPower=.14f;homingRange=112f;homingDelay=4f;
            splashDamage=4f;splashDamageRadius=10f;
            trailLength=6;trailWidth=.7f;trailChance=0f;
            shootEffect=smokeEffect=despawnEffect=Fx.none;hitEffect=WeaponVisuals.bombletBurst;
            hitSound=Sounds.explosionPlasmaSmall;hitSoundPitch=1.2f;hitSoundVolume=1f;
        }
        @Override public void draw(Bullet shot){super.draw(shot);WeaponVisuals.converge(shot,true);}
        @Override public void despawned(Bullet shot){if(!shot.absorbed)super.despawned(shot);}
        BulletType painted(Color color){
            try{Bomblet b=(Bomblet)clone();tint(b,color);b.backColor=color.cpy();b.frontColor=shipyard.graphics.EngineHighlights.colour(color,.65f);return b;}
            catch(CloneNotSupportedException e){throw new AssertionError(e);}
        }
    }

}
