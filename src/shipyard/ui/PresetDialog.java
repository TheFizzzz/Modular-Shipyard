package shipyard.ui;

import arc.Core;
import arc.func.Cons;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import shipyard.model.ShipTier;
import shipyard.service.PresetService;
import shipyard.world.Shipyard.ShipyardBuild;

/** Selection changes the draft; the editor's Apply commits the selected snapshot atomically. */
final class PresetDialog{
    private PresetDialog(){}
    static void show(BaseDialog editor,ShipyardBuild yard,ShipTier tier,String[] selected,Cons<PresetService.Preset> restore){
        BaseDialog dialog=new BaseDialog("@shipyard.loadouts");
        float width=Math.min(540f,Core.graphics.getWidth()/arc.scene.ui.layout.Scl.scl(1f)-90f);
        dialog.cont.add("@shipyard.loadouthint").wrap().width(width).fontScale(.85f).padBottom(12f).row();
        Table list=new Table();dialog.cont.pane(list).width(width).growY().row();
        int[] seen={-1};
        dialog.update(()->{
            if(!editor.isShown()){dialog.hide();return;}
            if(seen[0]==PresetService.revision)return;
            seen[0]=PresetService.revision;list.clear();
            var catalog=PresetService.local(tier);
            if(catalog==null){list.add("@shipyard.waiting").row();return;}
            list.button("@shipyard.customloadout",Styles.togglet,()->{selected[0]="";dialog.hide();})
                .name("shipyard-preset-custom").width(width).height(42f).checked(b->selected[0].isEmpty()).padBottom(8f).row();
            for(var entry:catalog.presets().entrySet()){
                String name=entry.getKey();var preset=entry.getValue();
                list.table(row->{
                    row.button(name+(preset.loadout().valid(tier)?"":Core.bundle.get("shipyard.presetoverbudget")),Styles.togglet,()->{selected[0]=name;restore.get(preset);dialog.hide();})
                        .name("shipyard-preset-"+name).width(width-104f).height(42f).checked(b->selected[0].equals(name));
                    row.button("@delete",()->Vars.ui.showConfirm(Core.bundle.format("shipyard.deletepreset",name),()->{
                        if(selected[0].equals(name))selected[0]="";
                        PresetService.requestDelete(yard,name);
                    })).name("shipyard-delete-"+name).width(96f).height(42f).padLeft(8f);
                }).padBottom(8f).row();
            }
        });
        dialog.buttons.button("@shipyard.saveas",()->Vars.ui.showTextInput("@shipyard.saveas","@shipyard.loadoutname",32,selected[0],raw->{
            try{
                String name=PresetService.name(raw);var catalog=PresetService.local(tier);
                if(!catalog.presets().containsKey(name) && catalog.presets().size()>=PresetService.maxPresets)
                    throw new IllegalArgumentException("This ship tier already has 12 loadouts. Delete one first.");
                selected[0]=name;dialog.hide();
            }catch(IllegalArgumentException error){Vars.ui.showInfo(error.getMessage());}
        })).name("shipyard-preset-saveas").disabled(b->PresetService.local(tier)==null).size(210f,64f);
        dialog.addCloseButton();dialog.show();
    }
}
