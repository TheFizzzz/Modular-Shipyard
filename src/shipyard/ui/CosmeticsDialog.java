package shipyard.ui;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.*;
import arc.util.Align;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ResizeEvent;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import shipyard.model.*;
import shipyard.service.CosmeticPreferences;

/** Main-menu colour preferences; no stat controls and no world units. */
public final class CosmeticsDialog{
    private CosmeticsDialog(){}

    public static void init(){
        if(!Vars.headless)Events.on(ClientLoadEvent.class,event->{
            Vars.ui.menufrag.addButton(Core.bundle.get("shipyard.menucosmetics"),Icon.pencil,CosmeticsDialog::show);
            // MenuFragment rebuilds its button tables on resize; adding a custom button only stores it.
            Core.app.post(()->Events.fire(new ResizeEvent()));
        });
    }

    public static void show(){
        if(Vars.headless || !Vars.state.isMenu())return;
        ShipCosmetics[] draft={CosmeticPreferences.saved()};
        ShipTier[] selected={ShipTier.alpha};
        Unit source=ShipTier.alpha.base().create(Team.sharded);
        float available=Math.max(280f,Core.graphics.getWidth()/Scl.scl(1f)-70f);
        float width=Math.min(390f,available-24f);
        BaseDialog dialog=new BaseDialog("@shipyard.menucosmetics");
        dialog.name="shipyard-default-cosmetics";
        dialog.addCloseListener();
        Table content=new Table();
        float total=Math.min(available,width+280f+32f);
        content.add("@shipyard.defaultcolourshint").wrap().width(total).color(Pal.lightishGray).fontScale(.85f)
            .with(label->label.setAlignment(Align.center)).padBottom(16f).colspan(2).row();
        ColourEditor editor=new ColourEditor(source,draft,width);
        Table preview=new Table();preview.top();
        preview.add("@shipyard.preview").color(Pal.accent).padBottom(8f).row();
        preview.table(tabs->{
            for(ShipTier tier:ShipTier.values())tabs.button(tier.base().localizedName,Styles.flatTogglet,()->selected[0]=tier)
                .name("shipyard-default-preview-"+tier.name()).width(84f).height(38f).checked(button->selected[0]==tier);
        }).padBottom(12f).row();
        Stack ships=new Stack();
        for(ShipTier tier:ShipTier.values()){
            Unit sample=tier.base().create(Team.sharded);
            ShipPreview picture=new ShipPreview(sample,()->ShipLoadout.defaults,()->draft[0]);
            picture.name="shipyard-default-ship-"+tier.name();picture.visible(()->selected[0]==tier);
            ships.addChild(picture);
        }
        preview.add(ships).size(248f,280f).row();
        if(available<700f){content.add(editor).pad(8f).colspan(2).row();content.add(preview).pad(12f).colspan(2).row();}
        else{content.add(editor).pad(8f).top();content.add(preview).pad(12f).top().row();}
        dialog.cont.pane(content).grow().get().setScrollingDisabled(true,false);
        dialog.buttons.button("@shipyard.savedefaultcolours",()->{CosmeticPreferences.save(draft[0]);dialog.hide();})
            .name("shipyard-save-default-colours").size(200f,48f).disabled(button->!editor.validInput());
        dialog.buttons.button("@close",dialog::hide).size(150f,48f);
        dialog.show();
    }
}
