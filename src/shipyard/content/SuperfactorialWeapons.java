package shipyard.content;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;

/** Appended fixed projectile ID; palette clones keep that ID without registering new content. */
final class SuperfactorialWeapons{
    private static Round round;
    private SuperfactorialWeapons(){}
    static void load(){round=new Round();}
    static BulletType template(){return round;}
    static BulletType painted(Color color){
        try{
            Round b=round.paletteClone();
            b.backColor=b.hitColor=b.trailColor=b.lightColor=color.cpy();
            b.frontColor=color.cpy().lerp(Color.white,.35f);
            return b;
        }catch(CloneNotSupportedException e){throw new AssertionError(e);}
    }
    static void penetration(mindustry.gen.Bullet shot,float spin){
        if(!(shot.type instanceof Round source))return;
        try{
            Round copy=source.paletteClone();
            copy.armorMultiplier=1f-.9f*arc.math.Mathf.clamp(spin);
            shot.type=copy;
        }catch(CloneNotSupportedException e){throw new AssertionError(e);}
    }
    private static final class Round extends BasicBulletType{
        Round paletteClone()throws CloneNotSupportedException{return (Round)clone();}
        Round(){
            super(8f,4f);lifetime=20f;keepVelocity=false;
            width=2f;height=6f;hitSize=2f;
            shootEffect=Fx.none;smokeEffect=Fx.none;
            hitEffect=Fx.hitBulletColor;despawnEffect=Fx.none;
        }
    }
}
