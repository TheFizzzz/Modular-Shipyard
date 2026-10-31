package shipyard.graphics;

import arc.Core;
import arc.graphics.Pixmap;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.graphics.MultiPacker;
import mindustry.type.Weapon;
import shipyard.model.ShipTier;
import shipyard.model.ShipWeapon;

/** Vanilla gun artwork adapted into private weapon mounts and emitters. */
public final class ShipGunSprites{
    private ShipGunSprites(){}

    public static String name(ShipTier tier,ShipWeapon kind){
        return "modular-shipyard-"+switch(kind){case railgun->"vector";case superfactorial->"superfactorial";case flamethrower->"flux";case missiles->"converge";case caustic->"caustic";case lancer->"involution";case asymptote->"asymptote";case chain->"recurrence";case eigen->"eigen";default->throw new IllegalArgumentException("No custom gun for "+kind);}+"-"+tier.name();
    }
    private static float scale(ShipTier tier,ShipWeapon kind){
        return switch(kind){case railgun->(1.4f+tier.ordinal()*.1f)*.8f;case flamethrower,caustic,asymptote->1f;case lancer->.9f;case chain->1.55f;case eigen->1.1f;case missiles->1.05f+tier.ordinal()*.05f;case superfactorial->.8f;default->throw new IllegalArgumentException("No custom gun for "+kind);};
    }
    public static void mount(Weapon gun,ShipTier tier,ShipWeapon kind,int index){
        gun.name=name(tier,kind);
        if(kind==ShipWeapon.chain || kind==ShipWeapon.eigen){
            gun.mirror=gun.alternate=gun.rotate=gun.flipSprite=false;gun.otherSide=-1;
            gun.baseRotation=gun.x=gun.shootX=gun.xRand=gun.yRand=0f;
            gun.y=2f+tier.ordinal()*.9f;
            gun.shootY=tier.base().weapons.first().shootY*scale(tier,kind);
            if(kind==ShipWeapon.chain)gun.shootY+=.5f*scale(tier,kind);
            else gun.shootY*=.8f;
            gun.recoil=kind==ShipWeapon.chain?.9f:.65f;
            gun.recoilTime=14f;gun.cooldownTime=25f;
            gun.top=true;gun.layerOffset=gun.shadow=0f;gun.ejectEffect=mindustry.content.Fx.none;
            if(!mindustry.Vars.headless && Core.atlas!=null)gun.load();
            return;
        }
        if(kind==ShipWeapon.lancer || kind==ShipWeapon.asymptote){
            gun.mirror=false;
            if(kind==ShipWeapon.lancer){
                // Preserve native alternate/otherSide pairing: the two blades still launch in turn.
                gun.alternate=true;gun.y-=.25f;gun.shootY*=.75f*scale(tier,kind);
                gun.recoil=.75f;gun.recoilTime=18f;
            }else{
                gun.alternate=false;gun.otherSide=-1;
                gun.y+=.5f;gun.shootY+=.5f;gun.recoil=1.1f;gun.recoilTime=24f;
            }
            gun.cooldownTime=25f;
            if(!mindustry.Vars.headless && Core.atlas!=null)gun.load();
            return;
        }
        if(kind==ShipWeapon.flamethrower || kind==ShipWeapon.missiles || kind==ShipWeapon.caustic){
            gun.mirror=gun.alternate=false;gun.otherSide=-1;
            if(kind==ShipWeapon.missiles){
                gun.x*=.95f;gun.y-=.5f;gun.shootY*=.75f*scale(tier,kind);
                gun.recoil=1.25f;gun.recoilTime=16f;gun.cooldownTime=20f;
            }else{
                gun.recoil=0f;
                if(kind==ShipWeapon.caustic)gun.shootY-=.25f;
            }
            if(!mindustry.Vars.headless && Core.atlas!=null)gun.load();
            return;
        }
        gun.mirror=gun.alternate=gun.rotate=false;gun.otherSide=-1;
        gun.flipSprite=kind==ShipWeapon.superfactorial && index<2;
        gun.baseRotation=0f;gun.shootX=gun.xRand=gun.yRand=0f;
        float scale=1f+tier.ordinal()*.3f;
        gun.x=kind==ShipWeapon.railgun?0f:new float[]{-2.5f,-.85f,.85f,2.5f}[index]*scale;
        gun.y=kind==ShipWeapon.railgun?2.25f+tier.ordinal()*1.25f:index==1 || index==2?1.4f+tier.ordinal()*.4f:0f;
        if(kind==ShipWeapon.railgun && tier==ShipTier.alpha)gun.y-=1f;
        gun.shootY=tier.base().weapons.first().shootY*scale(tier,kind);
        gun.recoil=kind==ShipWeapon.railgun?1.8f+tier.ordinal()*.3f:.65f;
        // Private guns bypass UnitType.init's sentinel normalization.
        gun.recoilTime=kind==ShipWeapon.railgun?50f:10f;gun.cooldownTime=30f;
        gun.top=true;gun.layerOffset=0f;gun.shadow=0f;gun.ejectEffect=mindustry.content.Fx.none;
        if(!mindustry.Vars.headless && Core.atlas!=null)gun.load();
    }
    public static void pack(MultiPacker packer){
        for(ShipTier tier:ShipTier.values())for(ShipWeapon kind:new ShipWeapon[]{ShipWeapon.railgun,ShipWeapon.superfactorial,ShipWeapon.flamethrower,ShipWeapon.missiles,ShipWeapon.caustic,ShipWeapon.lancer,ShipWeapon.asymptote,ShipWeapon.chain,ShipWeapon.eigen}){
            String name=name(tier,kind);
            if(packer.has(MultiPacker.PageType.main,name))continue;
            boolean vector=kind==ShipWeapon.railgun,central=kind==ShipWeapon.chain || kind==ShipWeapon.eigen;
            Weapon nativeGun=tier.base().weapons.first();
            Pixmap source=Core.atlas.getPixmap(nativeGun.name).crop();
            // Bring the intact gun sprites closer together rather than distorting their widths.
            int overlap=vector?Math.min(source.width-1,source.width/3+3+tier.ordinal()):central?source.width/2:0;
            int cropTop=kind==ShipWeapon.missiles || kind==ShipWeapon.lancer?source.height/4:kind==ShipWeapon.eigen?source.height/5:0;
            int extension=kind==ShipWeapon.asymptote || kind==ShipWeapon.chain?source.height/6:0;
            Pixmap layout=new Pixmap(vector || kind==ShipWeapon.eigen?source.width*2-overlap:source.width,source.height-cropTop+extension);
            float scale=scale(tier,kind);
            Pixmap body=new Pixmap(Math.max(1,Math.round(layout.width*scale)),Math.max(1,Math.round(layout.height*scale)));
            Pixmap heat=new Pixmap(body.width,body.height);
            Pixmap engine=kind==ShipWeapon.eigen?new Pixmap(layout.width,layout.height):null;
            Pixmap engineBody=engine==null?null:new Pixmap(body.width,body.height);
            try{
                if(vector){
                    // Join two COMPLETE default guns in their native left/right orientations.
                    copy(source,layout,0,true);
                    copy(source,layout,source.width-overlap,false);
                }else if(kind==ShipWeapon.chain)accelerator(source,layout,extension);
                else if(kind==ShipWeapon.eigen){
                    for(int y=0;y<layout.height;y++)for(int x=0;x<source.width;x++){
                        int pixel=source.getRaw(source.width-1-x,y+cropTop);
                        if((pixel&255)!=0)layout.setRaw(x,y,pixel);
                        pixel=source.getRaw(x,y+cropTop);
                        if((pixel&255)!=0)layout.setRaw(x+source.width-overlap,y,pixel);
                    }
                    symmetrize(layout);
                    launchTube(layout,engine);
                }else if(kind==ShipWeapon.flamethrower)taper(source,layout,tier==ShipTier.alpha);
                else if(kind==ShipWeapon.caustic)fork(source,layout);
                else if(kind==ShipWeapon.asymptote)accelerator(source,layout,extension);
                else if(kind==ShipWeapon.missiles || kind==ShipWeapon.lancer){
                    // Shorten the exposed barrel; preserve the native rear housing and palette.
                    for(int y=0;y<layout.height;y++)for(int x=0;x<layout.width;x++)
                        layout.setRaw(x,y,source.getRaw(x,y+cropTop));
                    if(kind==ShipWeapon.lancer)cradle(layout);
                }else copy(source,layout,0,false);
                if(vector)symmetrize(layout);
                resize(layout,body);
                // Nearest-neighbour rounding must preserve symmetry at the final pixel size, too.
                if(vector || central)symmetrize(body);
                if(kind==ShipWeapon.chain){slimConductorHousing(body);shadeConductor(body);}
                if(engine!=null){resize(engine,engineBody);symmetrize(engineBody);}
                if(vector && tier==ShipTier.alpha){trimRearCap(body);widenLowerAccent(body);}
                if(kind==ShipWeapon.missiles){
                    if(tier==ShipTier.gamma)roundPodOutside(body,(nativeGun.x>0)!=nativeGun.flipSprite);
                    shadePodMuzzle(body);
                }
                SpritePalette.snap(body,kind==ShipWeapon.missiles?podPalette:gunPalette);
                if(engineBody!=null)SpritePalette.snap(engineBody,emitterPalette);
                packer.add(MultiPacker.PageType.main,name,body);
                packer.add(MultiPacker.PageType.main,name+"-heat",heat);
                if(engineBody!=null)packer.add(MultiPacker.PageType.main,name+"-engine",engineBody);
            }finally{
                source.dispose();layout.dispose();body.dispose();heat.dispose();
                if(engine!=null)engine.dispose();if(engineBody!=null)engineBody.dispose();
            }
        }
    }
    /** Flare the mouth outward using rows from the native housing and a recessed bore. */
    private static void launchTube(Pixmap pixels,Pixmap engine){
        int depth=Math.max(4,pixels.height/3);
        Pixmap original=new Pixmap(pixels.width,pixels.height);
        try{
            copy(pixels,original,0,false);
            for(int y=0;y<depth;y++){
                int sampleY=Math.min(pixels.height-1,depth+y/2),first=pixels.width,last=-1;
                for(int x=0;x<pixels.width;x++)if((original.getRaw(x,sampleY)&255)!=0){first=Math.min(first,x);last=x;}
                pixels.fillRect(0,y,pixels.width,1,0);
                if(last<first)continue;
                float progress=y/(float)(depth-1);
                int width=Math.max(6,Math.round(pixels.width*(.94f-.22f*progress)));
                width=Math.min(pixels.width,width+((pixels.width-width)&1));
                int left=(pixels.width-width)/2;
                int bore=Math.max(4,Math.round(width*.48f));bore=Math.min(width-2,bore+((width-bore)&1));
                for(int x=0;x<width;x++){
                    // One paired step softens the outside mouth corners without a heavy outline.
                    if(y==0 && (x==0 || x==width-1))continue;
                    int sampleX=first+Math.min(last-first,(2*x+1)*(last-first+1)/(2*width));
                    int pixel=original.getRaw(sampleX,sampleY),alpha=pixel&255;
                    if(x>=(width-bore)/2 && x<(width+bore)/2 && alpha!=0){
                        int shade=Math.round(250f-16f*progress);
                        engine.setRaw(left+x,y,(shade<<24)|(shade<<16)|(shade<<8)|alpha);
                        pixel=0;
                    }
                    pixels.setRaw(left+x,y,pixel);
                }
            }
        }finally{original.dispose();}
    }
    /** Keep a small collar around the accent and taper the rear instead of covering the ship. */
    private static void slimConductorHousing(Pixmap pixels){
        int first=pixels.height,last=-1;
        for(int y=0;y<pixels.height;y++)for(int x=0;x<pixels.width;x++)if(accent(pixels.getRaw(x,y))){first=Math.min(first,y);last=Math.max(last,y);}
        if(last<0)return;
        // The enlarged native front housing otherwise leaves broad shoulders on the prong.
        int barrelWidth=Math.max(4,Math.round(pixels.width*.65f));
        barrelWidth=Math.min(pixels.width,barrelWidth+((pixels.width-barrelWidth)&1));
        for(int y=0;y<first;y++)for(int x=0;x<pixels.width;x++)
            if(x<(pixels.width-barrelWidth)/2 || x>=(pixels.width+barrelWidth)/2)pixels.setRaw(x,y,0);
        for(int y=first;y<pixels.height;y++){
            int left=pixels.width,right=-1;
            for(int x=0;x<pixels.width;x++)if(accent(pixels.getRaw(x,y))){left=Math.min(left,x);right=x;}
            if(right>=left){left=Math.max(0,left-1);right=Math.min(pixels.width-1,right+1);}
            else{
                float tail=1f-(y-last)/(float)Math.max(1,pixels.height-last);
                int width=Math.max(0,Math.round(pixels.width*.45f*tail));
                width=Math.min(pixels.width,width+((pixels.width-width)&1));
                left=(pixels.width-width)/2;right=left+width-1;
            }
            for(int x=0;x<pixels.width;x++)if(x<left || x>right)pixels.setRaw(x,y,0);
        }
    }
    /** Keep the native barrel face and round the accent collar into its grey housing. */
    private static void shadeConductor(Pixmap pixels){
        int first=pixels.height,last=-1;
        for(int y=0;y<pixels.height;y++)for(int x=0;x<pixels.width;x++)if(accent(pixels.getRaw(x,y))){first=Math.min(first,y);last=Math.max(last,y);}
        for(int y=first;y<pixels.height;y++){
            int left=pixels.width,right=-1;
            for(int x=0;x<pixels.width;x++)if((pixels.getRaw(x,y)&255)!=0){left=Math.min(left,x);right=x;}
            int inset=y-first<2?2:y-first<4?1:0;
            for(int x=left;x<=right;x++){
                int pixel=pixels.getRaw(x,y),alpha=pixel&255;
                if(alpha==0)continue;
                // A small paired shoulder step rounds the top collar without changing its silhouette.
                if(x<left+inset || x>right-inset)pixels.setRaw(x,y,0x6e708000|alpha);
                else if(y>last || !accent(pixel))pixels.setRaw(x,y,0xd4806b00|alpha);
            }
        }
    }
    private static final int[] gunPalette={0x6e7080ff,0x989aa4ff,0x8a8b99ff,0xd4806bff,0xffd27eff};
    private static final int[] podPalette={0x4a4b53ff,0x6e7080ff,0x989aa4ff,0x8a8b99ff,0xd4806bff,0xffd27eff};
    private static final int[] emitterPalette={0xeaeaeaff,0xf2f2f2ff,0xfafafaff};
    /** Engine-coloured muzzle insert, shared by live, painted and preview rendering. */
    public static void drawEngineTip(Unit unit,WeaponMount mount,TextureRegion region){
        if(region==null || !region.found())return;
        Weapon gun=mount.weapon;
        float rotation=unit.rotation-90f,angle=rotation+(gun.rotate?mount.rotation:gun.baseRotation);
        float recoil=Mathf.pow(mount.recoil,gun.recoilPow)*gun.recoil;
        float x=unit.x+Angles.trnsx(rotation,gun.x,gun.y)+Angles.trnsx(angle,0,-recoil);
        float y=unit.y+Angles.trnsy(rotation,gun.x,gun.y)+Angles.trnsy(angle,0,-recoil);
        float z=Draw.z(),colour=Draw.getColorPacked();
        Draw.z(z+Math.max(0f,gun.layerOffset)+.015f);
        Draw.color(unit.type.engineColor==null?unit.team.color:unit.type.engineColor);Draw.rect(region,x,y,angle);
        Draw.color(colour);Draw.z(z);
    }
    /** A shallow V-shaped seat cut into the shortened stock barrel. */
    private static void cradle(Pixmap pixels){
        int depth=Math.max(3,pixels.height/4),mouth=Math.max(4,(pixels.width*2/3)&~1);
        for(int y=0;y<depth;y++){
            int gap=Math.max(2,(Math.round(mouth*(1f-y/(float)depth))+1)&~1);
            pixels.fillRect((pixels.width-gap)/2,y,gap,1,0);
        }
    }
    /** Extend just the native front barrel, retaining the stock rear housing and accent detail. */
    private static void accelerator(Pixmap source,Pixmap target,int extension){
        int front=Math.max(1,source.height/3),length=front+extension;
        for(int y=0;y<target.height;y++){
            int sy=y<length?y*front/length:y-extension;
            for(int x=0;x<source.width;x++)target.setRaw(x,y,source.getRaw(x,sy));
            if(y<length){
                int left=source.width,right=-1;
                for(int x=0;x<source.width;x++)if((target.getRaw(x,y)&255)!=0){left=Math.min(left,x);right=x;}
                if(right-left>=6){target.setRaw(left,y,0);target.setRaw(right,y,0);}
                // A recessed mid-grey rail, light enough to follow the hull paint shading.
                for(int x=(source.width-1)/2;x<=source.width/2;x++){
                    int alpha=target.getRaw(x,y)&255;
                    if(alpha!=0)target.setRaw(x,y,0x8a8b9900|alpha);
                }
            }
        }
    }
    /** Small seated energy blade; reads reload state without spawning effects or changing gameplay. */
    public static void drawBlade(Unit unit,WeaponMount mount,float ready){
        if(ready<=.01f)return;
        Weapon gun=mount.weapon;
        float rotation=unit.rotation-90f,angle=rotation+(gun.rotate?mount.rotation:gun.baseRotation);
        float recoil=Mathf.pow(mount.recoil,gun.recoilPow)*gun.recoil;
        float x=unit.x+Angles.trnsx(rotation,gun.x,gun.y)+Angles.trnsx(angle,gun.shootX,gun.shootY-recoil);
        float y=unit.y+Angles.trnsy(rotation,gun.x,gun.y)+Angles.trnsy(angle,gun.shootX,gun.shootY-recoil);
        float z=Draw.z(),colour=Draw.getColorPacked();
        Draw.z(z+Math.max(0f,gun.layerOffset)+.015f);
        float size=.8f*ready;
        float lx=x+Angles.trnsx(angle,-size,-size*.35f),ly=y+Angles.trnsy(angle,-size,-size*.35f);
        float cx=x+Angles.trnsx(angle,0,size),cy=y+Angles.trnsy(angle,0,size);
        float rx=x+Angles.trnsx(angle,size,-size*.35f),ry=y+Angles.trnsy(angle,size,-size*.35f);
        Draw.color(gun.bullet.hitColor);Draw.alpha(ready);Lines.stroke(.6f*ready);
        Lines.line(lx,ly,cx,cy);Lines.line(cx,cy,rx,ry);
        Draw.color(gun.bullet.hitColor,Color.white,.5f);Draw.alpha(ready);Lines.stroke(.25f*ready);
        Lines.line(lx,ly,cx,cy);Lines.line(cx,cy,rx,ry);
        Lines.stroke(1f);Draw.color(colour);Draw.z(z);
    }
    /** Widen only the lighter tail below the dark-pink cap; leave the cap and its collar unchanged. */
    private static void widenLowerAccent(Pixmap pixels){
        int base=accentBase(pixels);
        if(base<0)return;
        for(int y=base+1;y<Math.min(pixels.height,base+4);y++){
            int left=pixels.width,right=-1;
            for(int x=0;x<pixels.width;x++)if(accent(pixels.getRaw(x,y))){left=Math.min(left,x);right=x;}
            if(right<0)continue;
            if(left>0)pixels.setRaw(left-1,y,pixels.getRaw(left,y));
            if(right+1<pixels.width)pixels.setRaw(right+1,y,pixels.getRaw(right,y));
        }
    }
    private static int accentBase(Pixmap pixels){
        int widest=0,last=-1;
        for(int y=0;y<pixels.height;y++){
            int count=0;for(int x=0;x<pixels.width;x++)if(accent(pixels.getRaw(x,y)))count++;
            if(count>0 && count>=widest){widest=count;last=y;}
        }
        return last;
    }
    /** Keep the front collar around the marking; trim only behind its widest pink section. */
    private static void trimRearCap(Pixmap pixels){
        int base=accentBase(pixels);
        if(base<0)return;
        int rearStart=base+1;
        for(int y=rearStart;y<pixels.height;y++)for(int x=0;x<pixels.width;x++)
            if(!accent(pixels.getRaw(x,y)))pixels.setRaw(x,y,0);
    }
    private static boolean accent(int pixel){
        int r=pixel>>>24,g=(pixel>>>16)&255,b=(pixel>>>8)&255;
        return (pixel&255)!=0 && r-b>20 && g-b>5;
    }
    /** Soften only the pod's outboard corners; native mount mirroring handles the opposite side. */
    private static void roundPodOutside(Pixmap pixels,boolean right){
        int minX=pixels.width,maxX=-1,minY=pixels.height,maxY=-1;
        for(int y=0;y<pixels.height;y++)for(int x=0;x<pixels.width;x++)if((pixels.getRaw(x,y)&255)!=0){
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        if(maxX<0)return;
        for(int y=minY;y<=maxY;y++){
            int inset=Math.max(0,2-Math.min(y-minY,maxY-y));
            for(int i=0;i<inset;i++)pixels.setRaw(right?maxX-i:minX+i,y,0);
        }
    }
    /** A two-pixel muzzle lip using the native gun's dark and medium-grey palette. */
    private static void shadePodMuzzle(Pixmap pixels){
        int first=pixels.height;
        for(int y=0;y<pixels.height && first==pixels.height;y++)for(int x=0;x<pixels.width;x++)
            if((pixels.getRaw(x,y)&255)!=0){first=y;break;}
        for(int y=first;y<Math.min(first+2,pixels.height);y++)for(int x=0;x<pixels.width;x++){
            int alpha=pixels.getRaw(x,y)&255;
            if(alpha!=0)pixels.setRaw(x,y,(y==first?0x4a4b5300:0x6e708000)|alpha);
        }
    }
    private static void symmetrize(Pixmap pixels){
        for(int y=0;y<pixels.height;y++)for(int x=0;x<pixels.width/2;x++){
            int pixel=pixels.getRaw(x,y),opposite=pixels.getRaw(pixels.width-1-x,y);
            if((pixel & 255)==0)pixel=opposite;
            pixels.setRaw(x,y,pixel);pixels.setRaw(pixels.width-1-x,y,pixel);
        }
    }
    /** Open a shallow fork in the native barrel; leave its rear body, shading and palette intact. */
    private static void fork(Pixmap source,Pixmap target){
        copy(source,target,0,false);
        int gap=Math.max(2,(source.width/3+1)&~1),left=(source.width-gap)/2;
        int length=Math.max(3,source.height/3);
        for(int y=0;y<length;y++){
            // A stepped round at the inside root avoids the former square-cut corners.
            int inset=y>=length-2 && gap>2?1:0;
            target.fillRect(left+inset,y,gap-inset*2,1,0);
        }
    }
    /** Compress only the front half into a narrow nozzle, retaining the original palette/body. */
    private static void taper(Pixmap source,Pixmap target,boolean aligned){
        int length=Math.max(1,source.height/2);
        for(int y=0;y<source.height;y++){
            float factor=y>=length?1f:.35f+.65f*y/length;
            int width=Math.max(3,Math.round(source.width*factor));
            // Alpha's small nozzle needs paired pixel steps so its centre does not wander by a pixel.
            if(aligned)width=Math.min(source.width,width+((source.width-width)&1));
            int left=(source.width-width)/2;
            for(int x=0;x<width;x++){
                int sx=aligned?(2*x+1)*source.width/(2*width):x*source.width/width;
                target.setRaw(left+x,y,source.getRaw(sx,y));
            }
        }
    }
    /** Preserve the complete sprite, including asymmetrical native shading and barrel details. */
    private static void copy(Pixmap source,Pixmap target,int offset,boolean flip){
        for(int y=0;y<source.height;y++)for(int x=0;x<source.width;x++){
            int pixel=source.getRaw(flip?source.width-1-x:x,y);
            if((pixel & 255)!=0)target.setRaw(x+offset,y,pixel);
        }
    }
    /** Uniform nearest-neighbour scaling: no stretching or half-sprite reconstruction. */
    private static void resize(Pixmap source,Pixmap target){
        for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++)
            target.setRaw(x,y,source.getRaw(x*source.width/target.width,y*source.height/target.height));
    }
}
