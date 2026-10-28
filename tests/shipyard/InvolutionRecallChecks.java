package shipyard;

import arc.audio.Sound;
import arc.math.Angles;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.*;

/** Actual positional cue dispatch, pursuit acceleration, age beyond expiry, catch and orphan cleanup. */
final class InvolutionRecallChecks{
    static final class Probe extends Sound{
        int count;float pitch,volume;
        @Override public int at(float x,float y,float pitch,float volume){count++;this.pitch=pitch;this.volume=volume;return 0;}
    }
    static void run()throws Exception{
        for(int tier=0;tier<3;tier++){
            Groups.bullet.clear();
            Unit owner=new mindustry.type.UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier].create(Team.sharded);
            owner.set(320,400);owner.add();ExperimentalChecks.equip(owner,tier,"lancer");
            var type=owner.mounts[0].weapon.bullet;
            var soundField=type.getClass().getDeclaredField("returnSound");soundField.setAccessible(true);
            check(soundField.get(type)==Sounds.shootSap,"wrong return sound");
            Probe probe=new Probe();soundField.set(type,probe);
            Bullet blade=type.create(owner,owner.team,owner.x,owner.y,0);
            for(int tick=0;tick<80 && phase(blade)<1;tick++)InvolutionChecks.tick();
            check(probe.count==1,"turnaround cue missing/duplicated");near(probe.pitch,1.25f,"return pitch");near(probe.volume,1f,"return volume");
            // Deliberately create a catch that takes longer than the old expiry and homing limits.
            owner.set(3000,400);
            for(int tick=0;tick<80 && phase(blade)<2;tick++)InvolutionChecks.tick();
            check(blade.isAdded() && phase(blade)==2,"no final pursuit");
            float previous=blade.vel.len();int elapsed=0;
            while(blade.isAdded() && elapsed++<300){
                if(elapsed==25)owner.y+=100; // Sharp change must not leave the blade orbiting.
                InvolutionChecks.tick();
                if(blade.isAdded()){
                    check(blade.vel.len()+.02f>=previous,"pursuit did not accelerate");
                    check(Angles.angleDist(blade.rotation(),blade.angleTo(owner))<.2f,"pursuit stopped aiming directly at owner");
                    previous=blade.vel.len();
                }
                if(elapsed==80)check(blade.isAdded(),"long pursuit expired before catch");
            }
            check(elapsed>80 && elapsed<300 && !blade.isAdded(),"blade did not finish long pursuit");
            check(probe.count==1,"return cue repeated during pursuit/catch");

            owner.set(320,400);blade=type.create(owner,owner.team,owner.x,owner.y,0);
            owner.set(900,400);int ticks=0;
            while(blade.isAdded() && ticks++<400){
                owner.x+=10f;owner.vel.set(10f,0f);InvolutionChecks.tick();
            }
            check(!blade.isAdded() && ticks<400,"blade could not catch continuously retreating owner");
            check(probe.count==2,"second throw did not announce once");

            owner.vel.setZero();owner.set(320,400);blade=type.create(owner,owner.team,owner.x,owner.y,0);owner.set(3000,400);
            for(int tick=0;tick<100 && phase(blade)<2;tick++)InvolutionChecks.tick();
            check(blade.isAdded(),"cleanup probe never reached pursuit");owner.remove();InvolutionChecks.tick();
            check(!blade.isAdded(),"pursuit keepAlive left an orphan after docking/death");

            Unit another=UnitTypes.alpha.create(Team.sharded);another.set(320,400);another.add();
            Bullet absorbed=type.create(another,another.team,another.x,another.y,0);int cues=probe.count;absorbed.absorb();InvolutionChecks.tick();
            check(probe.count==cues,"absorption played a return cue");another.remove();soundField.set(type,Sounds.shootSap);
        }
        System.out.println("PASS: Involution one full-volume Sap return cue, acceleration/direct pursuit, long catch beyond old expiry, retreating-owner catch and pursuit orphan/absorption cleanup on all tiers");
    }
    static int phase(Bullet b)throws Exception{return (int)InvolutionSteeringChecks.field(b,"phase");}
    static void check(boolean value,String why){InvolutionChecks.check(value,why);}
    static void near(float a,float b,String why){InvolutionChecks.near(a,b,why);}
}
