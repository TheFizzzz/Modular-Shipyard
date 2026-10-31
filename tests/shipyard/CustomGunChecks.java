package shipyard;

import arc.Events;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/** Native barrel counts, normalized output and state preservation through neutral snapshots. */
final class CustomGunChecks{
    static void run()throws Exception{
        var loader=Vars.mods.getMod("modular-shipyard").loader;
        var install=loader.loadClass("shipyard.service.UnitLoadouts").getMethod("install",Unit.class,ExperimentalChecks.tiers,ExperimentalChecks.model,ExperimentalChecks.colours);
        int content=Vars.content.bullets().size;
        for(int tier=0;tier<3;tier++)for(String kind:new String[]{"railgun","superfactorial","chain","eigen"}){
            UnitType base=new UnitType[]{UnitTypes.alpha,UnitTypes.beta,UnitTypes.gamma}[tier];
            var nativeWeapons=base.weapons;float nativeReload=nativeWeapons.first().reload;
            Unit ship=base.create(Team.sharded);ship.set(320,320);ship.add();
            install.invoke(null,ship,ExperimentalChecks.tiers.getEnumConstants()[tier],ExperimentalChecks.loadout(kind),ExperimentalChecks.palette);
            int count=kind.equals("superfactorial")?4:1;
            check(ship.mounts.length==count,"wrong barrel count");
            for(var mount:ship.mounts){
                check(mount.weapon.otherSide==-1 && !mount.weapon.mirror && !mount.weapon.rotate,"inherited paired mount state");
                check(mount.weapon.recoilTime>0,"negative recoil sentinel");
                mount.reload=37;mount.recoil=.6f;mount.totalShots=9;
            }
            var mounts=ship.mounts;
            if(count==1){
                check(ship.mounts[0].weapon.x==0,"central gun is not centred");
                float old=kind.equals("railgun")?240f*new float[]{.15f,.65f,2f}[tier]*nativeWeapons.size:kind.equals("chain")?36f*new float[]{.5f,1.1f,3f}[tier]*nativeWeapons.size:8f*new float[]{.35f,.8f,2f}[tier];
                near(ship.mounts[0].weapon.bullet.damage,old,"central gun damage changed");
                if(kind.equals("chain"))near(ship.mounts[0].weapon.bullet.armorMultiplier,nativeWeapons.size,"combined chain armour deduction changed");
            }else{
                float aggregate=0;for(var mount:ship.mounts)aggregate+=60f/mount.weapon.reload;
                near(aggregate,nativeWeapons.size*60f/30f,"cold aggregate fire rate changed");
                check(ship.mounts[0].weapon.x<0 && ship.mounts[1].weapon.x<0 && ship.mounts[2].weapon.x>0 && ship.mounts[3].weapon.x>0,"gun spacing");
            }
            // Exercise the native setType reset and the actual registered-view repair path.
            for(int pass=0;pass<3;pass++){
                ship.setType(Vars.content.unit("modular-shipyard-ship-"+base.name+"-hull"));Events.fire(Trigger.update);
                check(ship.mounts==mounts,"snapshot replaced active mounts");
                for(var mount:ship.mounts){near(mount.reload,37,"snapshot reload reset");near(mount.recoil,.6f,"snapshot recoil reset");check(mount.totalShots==9,"snapshot shots reset");}
            }
            if(count==4){
                ship.rotation=0;ship.spawnedByCore=true;
                int next=0,volleys=0,hotRounds=0;
                // Observe actual native shots rather than trusting the scheduler's fields.
                for(int tick=0;tick<360;tick++){
                    int[] before=new int[4];for(int i=0;i<4;i++)before[i]=ship.mounts[i].totalShots;
                    ExperimentalChecks.fire(ship,ship.x+140,ship.y,1);
                    int[] fired=new int[4];int total=0;
                    for(int i=0;i<4;i++){fired[i]=ship.mounts[i].totalShots-before[i];total+=fired[i];}
                    check(total==0 || total==2,"Superfactorial fired outside a two-gun volley");
                    if(total==2){
                        check(fired[0]==fired[3] && fired[1]==fired[2],"guns did not fire as symmetric pairs");
                        check((next==0?fired[0]:fired[1])==1,"outer/inner volley order changed");
                        next^=1;volleys++;
                    }
                    if(tick>=300)hotRounds+=total;
                }
                check(volleys>60,"spin-up failed to produce sustained volleys");
                near(hotRounds,60,"full-spin aggregate output changed");
            }
            check(base.weapons==nativeWeapons && nativeWeapons.first().reload==nativeReload,"vanilla weapon mutated");
            ship.remove();
        }
        check(Vars.content.bullets().size==content,"custom mounts registered bullets");
        System.out.println("PASS: Vector/Recurrence combined central damage, Eigen single launcher, four Superfactorial mounts with alternating symmetric two-gun volleys and normalized rate, tier geometry, vanilla isolation and repeated native snapshot state repair");
    }
    static void near(float actual,float expected,String why){check(Math.abs(actual-expected)<.03f,why+": "+actual+" != "+expected);}
    static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
