package shipyard.graphics;

import arc.graphics.Color;
import arc.audio.Sound;
import mindustry.Vars;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Vec2;
import mindustry.entities.Effect;
import mindustry.gen.*;
import mindustry.graphics.Drawf;

/** Bounded, engine-coloured decoration; no projectile movement, collision or damage changes. */
public final class WeaponVisuals{
    private WeaponVisuals(){}
    /** Lightning follows a jagged path in both passes: never overlay a straight beam. */
    public static final Effect recurrenceArc=new Effect(18f,420f,e->{
        if(!(e.data instanceof Vec2 end))return;
        float dx=end.x-e.x,dy=end.y-e.y,length=Mathf.dst(e.x,e.y,end.x,end.y);
        if(length<.01f)return;
        float nx=-dy/length,ny=dx/length;
        int segments=Math.min(24,Math.max(3,(int)(length/5f))),frame=(int)(e.time/2f);
        for(int pass=0;pass<2;pass++){
            Draw.color(e.color,Color.white,pass==0?0f:.35f);Draw.alpha(e.fout()*(pass==0?.25f:1f));Lines.stroke((pass==0?3f:.9f)*e.fout());
            Lines.beginLine();
            for(int i=0;i<=segments;i++){
                float p=i/(float)segments;
                float offset=i==0 || i==segments?0f:Mathf.randomSeed((long)e.id*31+i*131+frame*977,-5f,5f);
                Lines.linePoint(e.x+dx*p+nx*offset,e.y+dy*p+ny*offset);
            }
            Lines.endLine();
        }
        Draw.color(e.color);Draw.alpha(.7f*e.fout());Lines.stroke(.65f*e.fout());
        for(int i=1;i<=3;i++){
            float p=i/4f,side=i%2==0?1f:-1f,x=e.x+dx*p,y=e.y+dy*p;
            float bx=x+dx*.035f+nx*side*8f,by=y+dy*.035f+ny*side*8f;
            Lines.line(x,y,bx,by);Lines.line(bx,by,bx+dx*.025f-nx*side*3f,by+dy*.025f-ny*side*3f);
        }
        Draw.reset();Lines.stroke(1f);
    }).followParent(false);
    public static final Effect recurrenceHit=new Effect(22f,100f,e->{
        float radius=(e.data instanceof Float targetRadius?targetRadius:4f)+2f;
        Draw.color(e.color);Draw.alpha(e.fout());Lines.stroke(1.1f*e.fout());Lines.beginLine();
        for(int i=0;i<=16;i++){
            float a=i*22.5f,r=radius+Mathf.randomSeed((long)e.id*61+(i%16)*17+(int)(e.time/3f)*73,-1.5f,1.5f);
            Lines.linePoint(e.x+Angles.trnsx(a,r),e.y+Angles.trnsy(a,r));
        }
        Lines.endLine();
        for(int i=0;i<5;i++){float a=i*72f+e.time*9f;Lines.lineAngle(e.x+Angles.trnsx(a,radius),e.y+Angles.trnsy(a,radius),a,4f*e.fout());}
        Draw.color(Color.white);Fill.circle(e.x,e.y,1.5f*e.fout());Draw.reset();Lines.stroke(1f);
    }).followParent(false);
    public static final Effect flameHit=new Effect(18f,40f,e->{
        Draw.color(e.color);Draw.alpha(e.fout());
        for(int i=0;i<5;i++){float a=e.rotation+Mathf.randomSeed(e.id+i*31,-100f,100f),r=2f+e.fin()*9f;Fill.circle(e.x+Angles.trnsx(a,r),e.y+Angles.trnsy(a,r),(.5f+i*.1f)*e.fout());}
        Draw.reset();
    }).followParent(false);
    public static void flame(Bullet b){
        Draw.color(b.type.hitColor);Draw.alpha(.55f*b.fout());Lines.stroke(.8f*b.fout());
        for(int side=-1;side<=1;side+=2){Lines.beginLine();for(int i=0;i<=8;i++){
            float back=i*1.1f,offset=side*Mathf.sin(i*.6f-b.time*.25f)*i*.28f;
            Lines.linePoint(b.x+Angles.trnsx(b.rotation(),-back,offset),b.y+Angles.trnsy(b.rotation(),-back,offset));
        }Lines.endLine();}
        Draw.color(b.type.hitColor,Color.white,.3f);Draw.alpha(b.fout()*.7f);Fill.circle(b.x,b.y,.7f+b.fout());Draw.reset();Lines.stroke(1f);
    }
    public static void causticMuzzle(Bullet b){
        Draw.color(b.type.hitColor);Draw.alpha(.25f);Fill.circle(b.x,b.y,3.4f);
        Draw.alpha(.7f);Lines.stroke(.75f);Lines.arc(b.x,b.y,2.2f,.65f,b.time*9f);
        Drawf.tri(b.x,b.y,1.4f,6f,b.rotation());
        Draw.color(Color.white);Draw.alpha(.65f);Fill.circle(b.x,b.y,.7f);Draw.reset();Lines.stroke(1f);
    }
    public static final Effect spoolShot=new Effect(9f,48f,e->{
        float spin=e.data instanceof Float spinAmount?spinAmount:0f;
        Draw.color(e.color);Draw.alpha(e.fout());
        Drawf.tri(e.x,e.y,1f+spin,3f+spin*5f,e.rotation);
        for(int i=0;i<3;i++){
            float a=e.rotation+(i-1)*(15f+spin*20f),distance=(2f+e.fin()*(3f+spin*6f));
            Fill.circle(e.x+Angles.trnsx(a,distance),e.y+Angles.trnsy(a,distance),(.3f+spin*.45f)*e.fout());
        }
        Draw.color(Color.white);Fill.circle(e.x,e.y,(.5f+spin*.5f)*e.fout());Draw.reset();
    }).followParent(false);
    public static void spoolGlow(float x,float y,float angle,Color color,float spin,float time){
        Draw.color(color);Draw.alpha(.12f+spin*.28f);Fill.circle(x,y,1.5f+spin*2f);
        Draw.alpha(.45f+spin*.4f);Lines.stroke(.5f+spin*.4f);
        Lines.arc(x,y,1.7f+spin, .55f, time*(3f+spin*12f));
        if(spin>.95f){Draw.color(Color.white);Draw.alpha(.65f);Fill.circle(x,y,.7f);}
        Draw.reset();Lines.stroke(1f);
    }
    public static final Effect vectorCharge=new Effect(60f,80f,e->{
        float radius=4f+2f*e.fin(),orbit=e.time*9f,tilt=20f*Mathf.sin(e.time*.055f);
        float cx=e.x+Angles.trnsx(e.rotation,3f),cy=e.y+Angles.trnsy(e.rotation,3f);
        Draw.color(e.color);Draw.alpha(.35f+.45f*e.fin());Lines.stroke(.65f+.35f*e.fin());
        // A tilted orbit around the muzzle, rather than rails pointing downrange.
        Lines.beginLine();
        for(int i=0;i<=40;i++){
            float angle=i*9f;
            float x=Mathf.cosDeg(angle)*radius*.42f,y=Mathf.sinDeg(angle)*radius;
            Lines.linePoint(cx+Angles.trnsx(e.rotation+tilt,x,y),cy+Angles.trnsy(e.rotation+tilt,x,y));
        }
        Lines.endLine();
        for(int i=10;i>=0;i--){
            float angle=orbit-i*7f;
            float x=cx+Angles.trnsx(e.rotation+tilt,Mathf.cosDeg(angle)*radius*.42f,Mathf.sinDeg(angle)*radius);
            float y=cy+Angles.trnsy(e.rotation+tilt,Mathf.cosDeg(angle)*radius*.42f,Mathf.sinDeg(angle)*radius);
            Draw.color(e.color);Draw.alpha((1f-i/11f)*(.5f+.5f*e.fin()));Fill.circle(x,y,.45f+(1f-i/11f)*.65f);
            if(i==0){Draw.color(Color.white);Fill.circle(x,y,.5f);}
        }
        Draw.color(e.color);Draw.alpha(.25f+.5f*e.fin());Fill.circle(cx,cy,.8f+e.fin());
        Draw.reset();Lines.stroke(1f);
    }){
        @Override public void create(float x,float y,float rotation,Color color,Object effectData){
            if(effectData instanceof Unit owner)color=owner.type.weapons.first().bullet.hitColor;
            super.create(x,y,rotation,color,effectData);
        }
    }.rotWithParent(true);
    public static final Effect vectorLine=new Effect(22f,700f,e->{
        if(!(e.data instanceof Vec2 end))return;
        float length=Mathf.dst(e.x,e.y,end.x,end.y);
        Draw.color(e.color);Draw.alpha(e.fout());Lines.stroke(2.4f*e.fout());
        if(e.fin()<.25f)Lines.line(e.x,e.y,end.x,end.y);
        else{
            int segments=Math.min(20,Math.max(1,(int)(length/16f)));
            Lines.stroke(.9f*e.fout());
            for(int i=0;i<segments;i++){
                float a=i/(float)segments,b=Math.min(1f,a+(1f-e.fin()*.8f)/segments);
                Lines.line(Mathf.lerp(e.x,end.x,a),Mathf.lerp(e.y,end.y,a),Mathf.lerp(e.x,end.x,b),Mathf.lerp(e.y,end.y,b));
            }
        }
        if(e.fin()<.35f){Draw.color(Color.white);Draw.alpha(e.fout());Lines.stroke(.75f*e.fout());Lines.line(e.x,e.y,end.x,end.y);}
        Draw.reset();Lines.stroke(1f);
    }).followParent(false);
    public static final Effect vectorFarHit=new Effect(18f,48f,e->{
        Draw.color(e.color);Lines.stroke(1.3f*e.fout());
        for(int i=0;i<4;i++){
            float a=e.rotation+45+i*90,inner=1f+e.fin()*3,outer=inner+7f*e.fout();
            Lines.line(e.x+Angles.trnsx(a,inner),e.y+Angles.trnsy(a,inner),e.x+Angles.trnsx(a,outer),e.y+Angles.trnsy(a,outer));
        }
        Draw.color(Color.white);Fill.circle(e.x,e.y,1.5f*e.fout());Draw.reset();Lines.stroke(1f);
    }).followParent(false);
    public static final Effect asymptoteBlast=new Effect(34f,100f,e->{
        float radius=32f*Mathf.sqrt(e.fin());
        Draw.color(e.color);Draw.alpha(e.fout());Lines.stroke(1.6f*e.fout());
        // Four opening diamond faces mark the actual splash radius.
        for(int i=0;i<4;i++){
            float a=e.rotation+45f+i*90f;
            float x=e.x+Angles.trnsx(a,radius),y=e.y+Angles.trnsy(a,radius);
            Lines.line(x,y,e.x+Angles.trnsx(a+65f,radius*.6f),e.y+Angles.trnsy(a+65f,radius*.6f));
            Lines.line(x,y,e.x+Angles.trnsx(a-65f,radius*.6f),e.y+Angles.trnsy(a-65f,radius*.6f));
        }
        if(e.fin()>.2f){float p=(e.fin()-.2f)/.8f;Draw.alpha(.5f*(1-p));Lines.stroke(1f-p);Lines.circle(e.x,e.y,32f*p);}
        Draw.color(Color.white);Draw.alpha(e.fout());Fill.square(e.x,e.y,3f*e.fout(),e.rotation+45f);Draw.reset();Lines.stroke(1f);
    }).followParent(false);
    public static final Effect convergeBlast=new Effect(20f,64f,e->{
        Draw.color(e.color);Lines.stroke(1.1f*e.fout());Lines.circle(e.x,e.y,20f*e.fin());
        Draw.color(Color.white);Fill.circle(e.x,e.y,2.2f*e.fout());Draw.reset();Lines.stroke(1f);
    }).followParent(false);
    public static final Effect convergeSplit=new Effect(16f,48f,e->{
        Draw.color(e.color);Lines.stroke(.9f*e.fout());
        for(int i=0;i<3;i++){
            float a=e.rotation+(i-1)*40f,dist=2f+8f*e.fin();
            float x=e.x+Angles.trnsx(a,dist),y=e.y+Angles.trnsy(a,dist);
            Draw.alpha(e.fout()*.6f);Lines.line(e.x,e.y,x,y);Draw.alpha(e.fout());Fill.circle(x,y,1f*e.fout());
        }
        Draw.reset();Lines.stroke(1f);
    }).followParent(false);
    public static final Effect bombletBurst=new Effect(15f,32f,e->{
        Draw.color(e.color);Lines.stroke(.8f*e.fout());Lines.circle(e.x,e.y,10f*e.fin());
        for(int i=0;i<3;i++)Lines.lineAngle(e.x,e.y,e.rotation+i*120,6f*e.fin());
        Draw.color(Color.white);Fill.circle(e.x,e.y,1f*e.fout());Draw.reset();Lines.stroke(1f);
    }).followParent(false);

    /** Continuous gun-local reload decoration. No effect allocation or network spam per frame. */
    public static void reload(shipyard.model.ShipWeapon kind,float x,float y,float angle,Color colour,float clock,float intensity){
        if(kind==shipyard.model.ShipWeapon.railgun){
            Draw.color(Color.white);
            for(int i=0;i<6;i++){
                float life=((clock+i*4f)%24f)/24f,side=i%2==0?1f:-1f;
                float distance=1.5f+life*9f;
                Draw.alpha(.65f*(1f-life)*intensity);
                Fill.circle(x+Angles.trnsx(angle+side*90f,distance)-Angles.trnsx(angle,life*2f),
                    y+Angles.trnsy(angle+side*90f,distance)-Angles.trnsy(angle,life*2f),.8f+life*1.7f);
            }
        }else{
            Draw.color(colour,Color.white,.45f);Draw.alpha(.8f*intensity);Lines.stroke(.65f);
            for(int i=0;i<3;i++){
                float direction=angle+i*120f+Mathf.randomSeed((long)(clock/3f)+i*177,0f,100f);
                float radius=2f+Mathf.absin(clock+i*3f,2f,2f);
                float ex=x+Angles.trnsx(direction,radius),ey=y+Angles.trnsy(direction,radius);
                Lines.line(x,y,ex,ey);Lines.line(ex,ey,ex+Angles.trnsx(direction+65f,1.5f),ey+Angles.trnsy(direction+65f,1.5f));
            }
        }
        Draw.reset();Lines.stroke(1f);
    }

    /** Instant-hit results are chosen once on the authority and replayed with native effect codecs. */
    public static void authoritative(Effect effect,float x,float y,float angle,Color colour,Object details){
        if(Vars.net.client())return;
        effect.at(x,y,angle,colour,details);
        if(Vars.net.server())Call.effect(effect,x,y,angle,colour,details);
    }
    public static void sound(Sound sound,float x,float y,float pitch,float volume){
        if(Vars.net.client())return;
        if(Vars.net.server())Call.soundAt(sound,x,y,volume,pitch);
        else sound.at(x,y,pitch,volume);
    }

    public static void converge(Bullet b,boolean small){
        Color color=b.type.hitColor;
        if(small){
            Draw.color(color);Draw.alpha(.35f);Lines.stroke(.6f);
            for(int side=-1;side<=1;side+=2){
                Lines.beginLine();
                for(int i=0;i<=6;i++){
                    float back=i*1.7f,offset=side*Mathf.sin(i*.45f)*1.2f;
                    Lines.linePoint(b.x+Angles.trnsx(b.rotation(),-back,offset),b.y+Angles.trnsy(b.rotation(),-back,offset));
                }
                Lines.endLine();
            }
        }else{
            for(int i=0;i<3;i++){
                float a=b.time*10+i*120,forward=1f+Mathf.cosDeg(a)*2.4f,side=Mathf.sinDeg(a)*2.4f;
                float x=b.x+Angles.trnsx(b.rotation(),forward,side),y=b.y+Angles.trnsy(b.rotation(),forward,side);
                Draw.color(color);Draw.alpha(.4f);Fill.circle(x,y,1.5f);
                Draw.color(Color.black);Draw.alpha(.4f);Fill.circle(x,y,1.05f);
                Draw.color(color);Draw.alpha(1f);Fill.circle(x,y,.8f);
                Draw.color(Color.white);Fill.circle(x,y,.35f);
            }
        }
        Draw.reset();Lines.stroke(1f);
    }
    public static void asymptote(Bullet b,float charge){
        float speed=Mathf.clamp(b.vel.len()/12f),radius=4.5f+charge*2f,angle=b.time*5f;
        Draw.color(b.type.hitColor);Draw.alpha(.18f);Fill.circle(b.x,b.y,radius*1.5f);
        Draw.alpha(.95f);Lines.stroke(1.1f);
        // Separate rotating diamond faces open visibly as the projectile accelerates.
        for(int i=0;i<4;i++){
            float a=angle+i*90f,spread=radius+speed*2f;
            float x=b.x+Angles.trnsx(a,spread),y=b.y+Angles.trnsy(a,spread);
            Lines.line(x,y,b.x+Angles.trnsx(a+50f,radius*.65f),b.y+Angles.trnsy(a+50f,radius*.65f));
            Lines.line(x,y,b.x+Angles.trnsx(a-50f,radius*.65f),b.y+Angles.trnsy(a-50f,radius*.65f));
        }
        Draw.alpha(.8f);Fill.square(b.x,b.y,1.6f+charge,angle+45f);
        Draw.color(Color.white);Fill.square(b.x,b.y,.65f+charge*.45f,angle+45f);
        Draw.reset();Lines.stroke(1f);
    }
}
