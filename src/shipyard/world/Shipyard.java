package shipyard.world;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.math.Mat;
import arc.util.Time;
import arc.util.io.*;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.Block;
import mindustry.world.blocks.ControlBlock;
import mindustry.world.meta.Env;
import shipyard.model.ShipTier;
import shipyard.service.ShipyardSessions;
import shipyard.ui.ShipyardDialog;

public class Shipyard extends Block{
    public static final float editorRange=8f*Vars.tilesize;
    public static final Color fabricationColor=Color.valueOf("c7a4af");
    private TextureRegion offRegion,lightsRegion;
    public Shipyard(String name){
        super(name);size=3;health=1600;solid=true;destructible=true;update=true;
        consumePower(1f);configurable=false;saveConfig=false;copyConfig=false;
        envDisabled=Env.scorching;buildType=ShipyardBuild::new;
    }
    @Override public void load(){
        super.load();
        offRegion=Core.atlas.find(name+"-off",region);
        lightsRegion=Core.atlas.find(name+"-lights");
    }
    public class ShipyardBuild extends Building implements ControlBlock{
        private final BlockUnitc proxy=(BlockUnitc)UnitTypes.block.create(team);
        public Unit ship;
        private Unit portrait;
        private final Mat portraitTransform=new Mat(),previousTransform=new Mat();
        public int ownerId=-1;
        public String ownerUuid="";
        public long session;
        public boolean wasCore,opened;
        public float entryX,entryY;
        public boolean hasEditorPower(){return power!=null && power.graph.all.size>1 && power.status>=.999f;}
        public boolean usable(){return isValid() && enabled && hasEditorPower() && block.supportsEnv(Vars.state.rules.env);}
        public boolean acceptsEditor(Unit unit){
            return usable() && unit!=null && unit.isValid() && unit.isPlayer() && unit.team==team
                && unit.dst2(this)<=editorRange*editorRange && ShipTier.of(unit.type)!=null;
        }
        public boolean owns(Player player){return ship!=null && player!=null && player.id==ownerId;}
        public boolean editing(Player player){return owns(player) && player.team()==team && ship.team==team && player.unit()==unit() && usable();}
        @Override public Unit unit(){proxy.tile(this);proxy.team(team);return (Unit)proxy;}
        // Route entry through native buildingControlSelect so the original core ship is captured
        // before vanilla's unitControl path can despawn it. The resulting possession is a real BlockUnit.
        @Override public boolean canControl(){return false;}
        @Override public boolean shouldAutoTarget(){return false;}
        @Override public boolean canControlSelect(Unit unit){return Vars.state.rules.possessionAllowed && ship==null && acceptsEditor(unit);}
        @Override public void onControlSelect(Unit original){
            if(Vars.net.client() || !canControlSelect(original))return;
            Player player=original.getPlayer();if(player==null)return;
            ship=original;ownerId=player.id;ownerUuid=player.uuid();session=System.nanoTime();opened=false;
            entryX=ship.x;entryY=ship.y;wasCore=ship.spawnedByCore;
            shipyard.content.ShipWeapons.resetSpin(ship);
            ship.spawnedByCore=false;ship.vel.setZero();player.unit(unit());ship.remove();
            ShipyardSessions.remember(this);
            ShipyardSessions.sync(this,ship,false);
        }
        public void release(Player player){
            if(Vars.net.client() || ship==null)return;
            Unit returning=ship;
            // Stored units are serialized by the building, outside the normal unit-state chunk.
            // Re-register the view before release so subsequent native snapshots can repair it.
            shipyard.service.UnitLoadouts.track(returning);
            // Broadcast before clearing the session so clients get the same unit and owner.
            ShipyardSessions.sync(this,returning,true);
            ShipyardSessions.forget(this);
            ship=null;opened=false;ownerUuid="";
            Unit replacement=player==null?null:player.unit();
            if(replacement!=null && replacement!=unit() && replacement!=returning && replacement.spawnedByCore && player.team()==returning.team){
                replacement.spawnedByCore=false;replacement.remove();
            }
            returning.spawnedByCore=wasCore;returning.set(entryX,entryY);returning.vel.setZero();
            proxy.resetController();
            if(player!=null && player.team()==returning.team){
                player.justSwitchFrom=null;player.justSwitchTo=null;player.unit(returning);
            }else{
                returning.spawnedByCore=false;
                if(player!=null && player.unit()==unit())player.clearUnit();
            }
            returning.add();ownerId=-1;
        }
        @Override public void updateTile(){
            if(ship==null)return;
            if(Vars.net.client()){
                Unit ghost=Groups.unit.getByID(ship.id);if(ghost!=null)ghost.remove();
            }
            Player owner=Groups.player.getByID(ownerId);
            if(!Vars.net.client()){
                if(ship.team!=team){release(owner);return;}
                if(ownerId<0){
                    // Saves and disconnects keep the original ship safe until its owner returns.
                    for(Player player:Groups.player) if(player.uuid().equals(ownerUuid) && player.team()==team && !player.dead() && ShipTier.of(player.unit().type)!=null){
                        ownerId=player.id;release(player);return;
                    }
                }else if(!usable() || owner==null || owner.team()!=team || owner.unit()!=unit()){
                    release(owner);return;
                }
            }
            if(!Vars.headless && owner==Vars.player && editing(owner) && !opened){
                opened=true;ShipyardDialog.open(this,ship);
            }
        }
        @Override public void onRemoved(){
            if(ship!=null && !Vars.net.client() && !Vars.world.isGenerating())release(Groups.player.getByID(ownerId));
            super.onRemoved();
        }
        public boolean fabricating(){return ship!=null && ownerId>=0 && isControlled() && usable();}
        @Override public void draw(){
            Draw.rect(usable()?region:offRegion,x,y,drawrot());
            drawTeamTop();
            if(fabricating()){
                Draw.color(Color.white,.65f+Mathf.absin(Time.globalTime,8f,.35f));
                Draw.rect(lightsRegion,x,y);Draw.reset();
                Unit preview=ship;
                Draw.draw(Layer.blockOver,()->drawFabrication(preview));
            }
        }
        private void drawFabrication(Unit preview){
            TextureRegion icon=preview.type.fullIcon;
            // The selected 96-pixel sprite has a 56-pixel usable landing area.
            float pad=size*Vars.tilesize*(56f/96f);
            float fit=Math.min(1f,pad/(Math.max(icon.width,icon.height)*Draw.scl));
            drawPortrait(preview,fit);
            float previousX=Draw.xscl,previousY=Draw.yscl;
            try{
                Draw.scl(fit);
                // The coordinate overload uses the supplied tint without the building helper's yellow scan line.
                Drawf.construct(x,y,icon,fabricationColor,0f,Mathf.absin(Time.globalTime,45f,1f),.14f,Time.globalTime);
            }finally{Draw.scl(previousX,previousY);}
            Draw.color(fabricationColor,.85f);
            Lines.stroke(1f);
            Lines.lineAngleCenter(x+Mathf.sin(Time.globalTime,20f,pad/2f),y,90f,pad);
            Draw.reset();
        }
        private void drawPortrait(Unit source,float fit){
            ShipTier tier=ShipTier.of(source.type);
            if(portrait==null || ShipTier.of(portrait.type)!=tier)portrait=tier.base().create(source.team);
            if(portrait.type!=source.type){portrait.setType(source.type);shipyard.content.ShipWeapons.bind(portrait);}
            portrait.set(0f,0f);portrait.rotation=90f;portrait.team=source.team;
            portrait.health=portrait.maxHealth*source.healthf();
            previousTransform.set(Draw.trans());portraitTransform.set(previousTransform).translate(x,y).scale(fit,fit);
            float z=Draw.z();
            try{
                Draw.trans(portraitTransform);
                portrait.type.drawOutline(portrait);portrait.type.drawBody(portrait);
                portrait.type.drawCell(portrait);portrait.type.drawWeapons(portrait);
                Draw.flush();
            }finally{Draw.trans(previousTransform);Draw.reset();Draw.z(z);}
        }
        @Override public byte version(){return 1;}
        @Override public void write(Writes out){
            super.write(out);out.bool(ship!=null);
            if(ship!=null){
                out.i(ownerId);out.str(ownerUuid);out.l(session);out.bool(wasCore);out.f(entryX);out.f(entryY);
                ShipyardSessions.writeShip(out,ship);
            }
        }
        @Override public void read(Reads in,byte revision){
            super.read(in,revision);
            if(revision>=1 && in.bool()){
                int savedOwner=in.i();ownerUuid=in.str();session=in.l();wasCore=in.bool();entryX=in.f();entryY=in.f();
                ship=ShipyardSessions.readShip(in);ownerId=Vars.net.client()?savedOwner:-1;opened=false;ShipyardSessions.remember(this);
            }
        }
    }
}
