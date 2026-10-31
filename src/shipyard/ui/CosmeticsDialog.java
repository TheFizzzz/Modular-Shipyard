package shipyard.ui;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.*;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ResizeEvent;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import shipyard.api.CosmeticFamily;
import shipyard.model.ShipCosmetics;

/** One menu entry; installed addons contribute independent family pages. */
public final class CosmeticsDialog{
    private static final Seq<CosmeticFamily> families=Seq.with(new SerpuloCosmetics());
    private CosmeticsDialog(){}

    /** Call from the addon's Mod.init, before ClientLoadEvent builds the menu. */
    public static void registerFamily(CosmeticFamily family){
        if(family==null || family.id()==null || family.id().isBlank())
            throw new IllegalArgumentException("Cosmetic families need an ID");
        if(families.contains(existing->existing.id().equals(family.id())))
            throw new IllegalArgumentException("Duplicate cosmetic family: "+family.id());
        families.add(family);
    }

    public static void init(){
        if(!Vars.headless)Events.on(ClientLoadEvent.class,event->{
            Vars.ui.menufrag.addButton(Core.bundle.get("shipyard.menucosmetics"),Icon.pencil,CosmeticsDialog::show);
            // MenuFragment rebuilds its button tables on resize; adding a custom button only stores it.
            Core.app.post(()->Events.fire(new ResizeEvent()));
        });
    }

    public static void show(){
        if(Vars.headless || !Vars.state.isMenu())return;
        float available=Math.max(280f,Core.graphics.getWidth()/Scl.scl(1f)-70f);
        float width=Math.min(390f,available-24f);
        BaseDialog dialog=new BaseDialog("@shipyard.menucosmetics");
        dialog.name="shipyard-default-cosmetics";
        dialog.addCloseListener();
        int[] selectedFamily={0};
        Seq<FamilyPage> pages=new Seq<>();
        Stack content=new Stack();
        for(int index=0;index<families.size;index++){
            final int chosen=index;
            CosmeticFamily family=families.get(index),next=families.get((index+1)%families.size);
            FamilyPage page=new FamilyPage(family,available,width,
                families.size>1?next:null,()->selectedFamily[0]=(chosen+1)%families.size);
            page.visible(()->selectedFamily[0]==chosen);
            pages.add(page);content.addChild(page);
        }
        dialog.cont.pane(content).grow().get().setScrollingDisabled(true,false);
        dialog.buttons.button("@shipyard.savedefaultcolours",()->{
            // Switching families retains drafts. Save commits every edited family together.
            for(FamilyPage page:pages)if(!page.draft[0].equals(page.family.saved()))page.family.save(page.draft[0]);
            dialog.hide();
        }).name("shipyard-save-default-colours").size(200f,48f)
            .disabled(button->pages.contains(page->!page.editor.validInput()));
        dialog.buttons.button("@close",dialog::hide).size(150f,48f);
        dialog.show();
    }

    private static final class FamilyPage extends Table{
        final CosmeticFamily family;
        final ShipCosmetics[] draft;
        final ColourEditor editor;

        FamilyPage(CosmeticFamily family,float available,float width,CosmeticFamily next,Runnable switchFamily){
            this.family=family;
            name="shipyard-family-"+family.id();
            draft=new ShipCosmetics[]{family.saved()};
            var ships=family.ships();
            if(ships.length==0)throw new IllegalArgumentException("Cosmetic family has no ships: "+family.id());
            int[] selectedShip={0};
            // Preserve existing Serpulo control names for the in-game editor and desktop checks.
            String prefix=family.id().equals("serpulo")?"shipyard":"shipyard-"+family.id();
            Unit source=ships[0].create(Team.sharded);
            float total=Math.min(available,width+280f+32f);
            add(family.hint()).wrap().width(total).color(Pal.lightishGray).fontScale(.85f)
                .with(label->label.setAlignment(Align.center)).padBottom(16f).colspan(2).row();
            editor=new ColourEditor(source,draft,width,prefix);
            Table preview=new Table();preview.top();
            preview.add("@shipyard.preview").color(Pal.accent).padBottom(8f).row();
            preview.table(tabs->{
                for(int index=0;index<ships.length;index++){
                    final int chosen=index;
                    tabs.button(ships[index].localizedName,Styles.flatTogglet,()->selectedShip[0]=chosen)
                        .name(prefix+"-default-preview-"+ships[index].name).width(90f).height(38f)
                        .checked(button->selectedShip[0]==chosen).with(button->button.getLabel().setFontScale(.85f));
                }
            }).padBottom(12f).row();
            Stack pictures=new Stack();
            for(int index=0;index<ships.length;index++){
                final int chosen=index;
                Unit sample=ships[index].create(Team.sharded);
                ShipPreview picture=new ShipPreview(sample,family,()->draft[0]);
                picture.name=prefix+"-default-ship-"+ships[index].name;
                picture.visible(()->selectedShip[0]==chosen);pictures.addChild(picture);
            }
            preview.add(pictures).size(248f,280f).row();
            if(next!=null)preview.button(Core.bundle.format("shipyard.switchfamily",next.name()),switchFamily)
                .name("shipyard-switch-family-"+family.id()).size(248f,48f).padTop(8f).row();
            if(available<700f){add(editor).pad(8f).colspan(2).row();add(preview).pad(12f).colspan(2).row();}
            else{add(editor).pad(8f).top();add(preview).pad(12f).top().row();}
        }
    }
}
