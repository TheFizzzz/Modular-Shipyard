package shipyard.ui;

import arc.Core;
import arc.func.Floatp;
import arc.graphics.Color;
import arc.scene.ui.Slider;
import arc.scene.ui.Label;
import arc.scene.ui.layout.*;
import arc.util.Strings;
import arc.util.Align;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import shipyard.content.ShipVariants;
import shipyard.content.ShipWeapons;
import shipyard.model.*;
import shipyard.service.LoadoutService;
import shipyard.service.PresetService;
import shipyard.world.Shipyard.ShipyardBuild;

/** Responsive editor; previews are local and Apply remains authoritative. */
public final class ShipyardDialog{
    private static BaseDialog active;
    private ShipyardDialog(){}

    public static void open(ShipyardBuild yard, Unit ship){
        if(Vars.headless || Vars.player == null || yard.ship != ship || !yard.editing(Vars.player)) return;
        if(active != null) active.hide();
        ShipTier tier = ShipTier.of(ship.type);
        ShipLoadout[] draft = {ShipVariants.current(ship.type)};
        ShipCosmetics[] colours = {ShipVariants.cosmetics(ship.type)};
        float uiScale = Scl.scl(1f);
        float available = Math.max(320f, Core.graphics.getWidth() / uiScale - 56f);
        boolean compact = available < 960f;
        float panelWidth = compact ? Math.min(540f, available) : Math.min(540f, (available - 48f) / 3f);
        float inner = panelWidth - 24f;
        ColourEditor colourEditor = new ColourEditor(ship, colours, inner);
        LoadoutService.feedback = "";
        BaseDialog dialog = new BaseDialog("@shipyard.title");
        active = dialog;
        dialog.addCloseListener();
        String[] selectedPreset={""};
        boolean[] presetReady={false};
        PresetService.request(yard);
        var restorePreset=new java.util.concurrent.atomic.AtomicReference<arc.func.Cons<PresetService.Preset>>();


        Table left = panel("shipyard.statistics"), center = panel("shipyard.cosmetics"), right = panel("shipyard.configuration");
        comparison(left, inner, "shipyard.speed", () -> ship.type.speed * 60f / Vars.tilesize,
            () -> draft[0].speed(tier.base()) * 60f / Vars.tilesize, " tiles/s");
        comparison(left, inner, "shipyard.cargo", () -> ship.type.itemCapacity, () -> draft[0].capacity(tier.base()), "");
        comparison(left, inner, "shipyard.build", () -> ship.type.buildSpeed, () -> draft[0].buildSpeed(tier.base()), "x");
        comparison(left, inner, "shipyard.buildrange", () -> ship.type.buildRange / Vars.tilesize,
            () -> draft[0].buildRange(tier.base()) / Vars.tilesize, " tiles");
        comparison(left, inner, "shipyard.firerate", () -> ShipVariants.current(ship.type).fireRateFactor() * 100f,
            () -> draft[0].fireRateFactor() * 100f, "% base");
        comparison(left, inner, "shipyard.mine", () -> ship.type.mineSpeed, () -> draft[0].mineSpeed(tier.base()), "x");
        comparison(left, inner, "shipyard.health", () -> ship.type.health, () -> draft[0].health(tier.base()), " HP");
        comparison(left,inner,"shipyard.damageReduction",()->ShipVariants.current(ship.type).damageReduction()*100f,
            ()->draft[0].damageReduction()*100f,"%");
        comparison(left, inner, "shipyard.weapondamage", () -> ShipWeapons.damage(tier, ShipVariants.current(ship.type).weapon()),
            () -> ShipWeapons.damage(tier, draft[0].weapon()), "");
        comparison(left, inner, "shipyard.weaponrange", () -> ShipWeapons.range(tier, ShipVariants.current(ship.type).weapon()) / Vars.tilesize,
            () -> ShipWeapons.range(tier, draft[0].weapon()) / Vars.tilesize, " tiles");
        center.add(colourEditor).growX();

        right.button("", () -> chooseWeapon(dialog, tier, draft)).name("shipyard-weapon-selector")
            .width(inner).height(34f).padBottom(8f)
            .update(button -> {
                button.setText(Core.bundle.format("shipyard.weaponchoice", draft[0].weapon().display()));
                button.getLabel().setColor(draft[0].weapon().titleColour());
            })
            .with(button -> button.getLabel().setFontScale(.85f)).row();
        Slider speed = control(right, inner, "shipyard-speed",
            () -> Core.bundle.format("shipyard.speedtuning", signed(draft[0].speed() * 10)), draft[0].speed());
        speed.changed(() -> draft[0] = draft[0].withSpeed(Math.round(speed.getValue())));
        Slider cargo = control(right, inner, "shipyard-cargo",
            () -> Core.bundle.format("shipyard.cargotuning", signed(draft[0].cargo() * 15), signed(draft[0].cargo() * 20)), draft[0].cargo());
        cargo.changed(() -> draft[0] = draft[0].withCargo(Math.round(cargo.getValue())));
        Slider construction = control(right, inner, "shipyard-construction",
            () -> Core.bundle.format("shipyard.constructiontuning", signedStep(draft[0].construction())), draft[0].construction());
        construction.changed(() -> draft[0] = draft[0].withConstruction(Math.round(construction.getValue())));
        Slider durability = control(right, inner, "shipyard-durability",
            () -> Core.bundle.format("shipyard.durabilitytuning", signed(draft[0].durability() * 15),Strings.autoFixed(draft[0].damageReduction()*100f,0)), draft[0].durability());
        durability.changed(() -> draft[0] = draft[0].withDurability(Math.round(durability.getValue())));
        Slider fireRate = control(right, inner, "shipyard-fireRate",
            () -> Core.bundle.format("shipyard.firetuning", signed(draft[0].fireRate() * 10)), draft[0].fireRate());
        fireRate.changed(() -> draft[0] = draft[0].withFireRate(Math.round(fireRate.getValue())));
        right.add(Core.bundle.format("shipyard.points", draft[0].upgradePoints(), draft[0].refundPoints(), draft[0].remainingPoints(tier), tier.budget, draft[0].weapon().cost())).name("shipyard-points").wrap().width(inner).minHeight(44f).fontScale(.9f).padTop(6f).update(label -> {
            label.setText(Core.bundle.format("shipyard.points", draft[0].upgradePoints(), draft[0].refundPoints(), draft[0].remainingPoints(tier), tier.budget, draft[0].weapon().cost()));
            label.setColor(draft[0].valid(tier) ? Pal.accent : Color.scarlet);
        }).row();

        restorePreset.set(preset->{
            var stats=preset.loadout();
            speed.setValue(stats.speed());cargo.setValue(stats.cargo());construction.setValue(stats.construction());
            durability.setValue(stats.durability());fireRate.setValue(stats.fireRate());
            draft[0]=stats;colours[0]=preset.colours();colourEditor.refresh();LoadoutService.feedback="";
        });
        Table layout = new Table();
        int[] section = {1};
        Table[] panels = {left, center, right};
        if(compact){
            layout.table(tabs -> {
                String[] keys = {"shipyard.statistics", "shipyard.cosmetics", "shipyard.configuration"};
                for(int i = 0; i < keys.length; i++){
                    final int selected = i;
                    tabs.button("@" + keys[i], Styles.flatTogglet, () -> section[0] = selected)
                        .width(panelWidth / 3f).height(42f).checked(button -> section[0] == selected)
                        .with(button -> button.getLabel().setFontScale(.8f));
                    panels[i].visible(() -> section[0] == selected);
                }
            }).padBottom(12f).row();
            layout.stack(left, center, right).width(panelWidth).row();
        }
        boolean[] rules = {false};
        Table preview = new Table(Styles.grayPanel);
        preview.name = "shipyard-preview-panel";
        preview.margin(12f);
        preview.add("@shipyard.preview").color(Pal.accent).fontScale(.9f).padBottom(6f).row();
        preview.add(ship.type.localizedName).fontScale(.8f).padBottom(8f).row();
        preview.add(new ShipPreview(ship, () -> draft[0], () -> colours[0]))
            .name("shipyard-ship-preview").grow();
        Table help = new Table(Styles.grayPanel);
        help.name = "shipyard-rules-panel";
        help.top().margin(12f);
        help.add("@shipyard.balancerules").color(Pal.accent).padBottom(6f).row();
        Label rulesText = new Label(Core.bundle.get("shipyard.rules"));
        rulesText.name = "shipyard-rules-text";
        rulesText.setWrap(true);
        rulesText.setFontScale(.95f);
        rulesText.setWidth((inner - 40f) * uiScale);
        float rulesHeight = Math.min(340f, Math.max(200f, rulesText.getPrefHeight() / uiScale + 58f));
        help.pane(text -> {
            text.top().left();
            text.add(rulesText).width(inner - 40f).top().left();
        }).grow().get().setScrollingDisabled(true, false);
        if(compact){
            layout.add(preview).size(panelWidth, 250f).padTop(12f).row();
            layout.collapser(container -> container.add(help).size(panelWidth, rulesHeight), () -> rules[0]).padTop(18f).row();
        }else{
            help.visible(() -> rules[0]);
            float upperHeight = Math.max(right.getPrefHeight(), center.getPrefHeight()) / uiScale;
            float room = Core.graphics.getHeight() / uiScale - 165f;
            float lowerHeight = Math.max(left.getPrefHeight() / uiScale, Math.min(250f, Math.max(180f, room - upperHeight - 12f)));
            layout.table(columns -> {
                columns.table(pairs -> {
                    pairs.add(right).size(panelWidth, upperHeight);
                    pairs.add(center).size(panelWidth, upperHeight).padLeft(24f).row();
                    pairs.add(left).size(panelWidth, lowerHeight).padTop(12f);
                    pairs.add(preview).size(panelWidth, lowerHeight).padLeft(24f).padTop(12f);
                });
                Table rulesSlot = new Table();
                Cell<Table> rulesCell = columns.add(rulesSlot).width(0f).height(rulesHeight);
                boolean[] shown = {false};
                columns.update(() -> {
                    if(shown[0] != rules[0]){
                        shown[0] = rules[0];
                        rulesSlot.clearChildren();
                        if(rules[0]) rulesSlot.add(help).size(panelWidth, rulesHeight);
                        rulesCell.width(rules[0] ? panelWidth : 0f).padLeft(rules[0] ? 24f : 0f);
                        columns.invalidateHierarchy();
                    }
                });
            }).row();
        }
        var content = dialog.cont.pane(layout).grow().get();
        content.setScrollingDisabled(true, false);
        content.setScrollbarsOnTop(true);
        content.setFadeScrollBars(true);
        // Confirmation applies only to the draft the player saw on the first click.
        ShipLoadout[] resetStats = {null};
        ShipCosmetics[] resetPalette = {null};

        float buttonWidth = Math.min(155f, (available - 42f) / 4f);
        dialog.buttons.button("",()->PresetDialog.show(dialog,yard,tier,selectedPreset,preset->restorePreset.get().get(preset)))
            .name("shipyard-loadouts").width(Math.min(320f,available)).height(38f).padTop(8f).padBottom(2f)
            .update(button->button.setText(Core.bundle.format("shipyard.loadoutselected",
                selectedPreset[0].isEmpty()?Core.bundle.get("shipyard.customloadout"):selectedPreset[0])))
            .with(button->button.getLabel().setFontScale(.85f)).row();
        dialog.buttons.table(actions -> {
            actions.defaults().width(buttonWidth).height(46f).pad(7f);
            actions.button("@shipyard.apply", () -> {
                resetStats[0] = null;
                LoadoutService.request(yard, ship, draft[0], colours[0],selectedPreset[0]);
            }).name("shipyard-apply")
                .disabled(button -> !draft[0].valid(tier) || ship.stack.amount > draft[0].capacity(tier.base())
                    || !colourEditor.validInput() || LoadoutService.waiting() || !presetReady[0]
                    || (draft[0].equals(ShipVariants.current(ship.type)) && colours[0].equals(ShipVariants.cosmetics(ship.type))
                        && PresetService.local(tier)!=null && selectedPreset[0].equals(PresetService.local(tier).selected())))
                .with(button -> button.getLabel().setFontScale(.9f));
            actions.button("@shipyard.reset", () -> {
                if(resetStats[0] == null || !resetStats[0].equals(draft[0]) || !resetPalette[0].equals(colours[0])){
                    resetStats[0] = draft[0];resetPalette[0] = colours[0];
                    return;
                }
                resetStats[0] = null;
                speed.setValue(0);cargo.setValue(0);construction.setValue(0);durability.setValue(0);fireRate.setValue(0);
                draft[0] = ShipLoadout.defaults;colourEditor.resetColours();LoadoutService.feedback = "";
            }).name("shipyard-reset").with(button -> button.getLabel().setFontScale(.85f))
                .update(button -> button.setText(Core.bundle.get(resetStats[0] == null ? "shipyard.reset" : "shipyard.confirmreset")));
            actions.button("@shipyard.balancerules", Styles.togglet, () -> rules[0] = !rules[0])
                .checked(button -> rules[0]).with(button -> button.getLabel().setFontScale(.85f));
            actions.button("@close", dialog::hide).with(button -> button.getLabel().setFontScale(.9f));
        }).padTop(8f).row();
        arc.func.Prov<CharSequence> status = () -> ship.stack.amount > draft[0].capacity(tier.base()) ? Core.bundle.get("shipyard.unload")
            : LoadoutService.waiting() ? Core.bundle.get("shipyard.waiting") : LoadoutService.feedback;
        dialog.buttons.collapser(messages -> messages.label(status)
            .name("shipyard-feedback").wrap().width(Math.min(850f, available)).fontScale(.8f).padBottom(10f)
            .with(label -> label.setAlignment(Align.center)), () -> !status.get().toString().isEmpty()).row();
        dialog.update(() -> {
            if(!presetReady[0] && PresetService.local(tier)!=null){selectedPreset[0]=PresetService.local(tier).selected();presetReady[0]=true;}
            if(resetStats[0] != null && (!resetStats[0].equals(draft[0]) || !resetPalette[0].equals(colours[0]))) resetStats[0] = null;
            if(!Vars.state.isGame() || Vars.player == null || yard.ship != ship
                || ShipTier.of(ship.type) != tier || !yard.editing(Vars.player)) dialog.hide();
        });
        dialog.hidden(() -> { if(active == dialog) active = null;shipyard.service.ShipyardSessions.exit(yard); });
        dialog.show();
    }

    private static void chooseWeapon(BaseDialog editor, ShipTier tier, ShipLoadout[] draft){
        BaseDialog choices = new BaseDialog("@shipyard.weapons");
        float available=Core.graphics.getWidth()/Scl.scl(1f)-64f;
        int damageColumns=available>=1750f?4:available>=1480f?3:2;
        float cardWidth=Math.min(360f,Math.max(240f,(available-32f)/(damageColumns+1)-12f));
        var ordered=arc.struct.Seq.with(ShipWeapon.values()).select(ShipWeapon::selectable);
        ordered.sort((first,second)->first.cost()!=second.cost()?Integer.compare(first.cost(),second.cost()):Integer.compare(first.ordinal(),second.ordinal()));
        var cards=new arc.struct.ObjectMap<ShipWeapon,Table>();
        float cardHeight=240f;
        for(ShipWeapon kind:ordered){
            Table card=weaponCard(choices,tier,draft,kind,cardWidth);card.pack();card.validate();
            cardHeight=Math.max(cardHeight,card.getPrefHeight());cards.put(kind,card);
        }
        final float rowHeight=cardHeight;
        choices.cont.pane(clusters -> {
            clusters.top();
            for(boolean utility:new boolean[]{false,true}){
                int columns=utility?1:damageColumns;
                Table group=new Table();group.top();
                group.name=utility?"shipyard-utility-weapons":"shipyard-damage-weapons";
                group.add(utility?"@shipyard.utilityweapons":"@shipyard.damageweapons")
                    .name(utility?"shipyard-utility-heading":"shipyard-damage-heading")
                    .color(utility?ShipWeapon.invariant.titleColour():Color.orange)
                    .growX().padBottom(8f).with(label->label.setAlignment(Align.center)).row();
                var kinds=ordered.select(kind->kind.utility()==utility);
                for(int start=0;start<kinds.size;start+=columns){
                    Table row=new Table();
                    for(int index=start;index<Math.min(start+columns,kinds.size);index++)
                        row.add(cards.get(kinds.get(index))).size(cardWidth,rowHeight).top().pad(6f);
                    // Each row keeps the full grid width; an incomplete row centers its cards.
                    group.add(row).width(columns*(cardWidth+12f)).row();
                }
                clusters.add(group).top();
                if(!utility)clusters.image(mindustry.gen.Tex.whiteui).color(Color.darkGray)
                    .width(2f).growY().padLeft(14f).padRight(14f);
            }
        }).grow().get().setScrollingDisabled(false,false);
        choices.addCloseButton();
        choices.update(() -> {if(!editor.isShown()) choices.hide();});
        choices.show();
    }

    private static Table weaponCard(BaseDialog choices,ShipTier tier,ShipLoadout[] draft,ShipWeapon kind,float width){
        Table card=new Table(Styles.grayPanel);card.top().left().margin(12f);
        card.button(kind.display(),Styles.togglet,()->{
            draft[0]=draft[0].withWeapon(kind);choices.hide();
        }).name("shipyard-choose-"+kind.name()).width(width-24f).height(42f)
            .checked(button->draft[0].weapon()==kind).with(button->{
                button.getLabel().setFontScale(.85f);button.getLabel().setColor(kind.titleColour());
            }).row();
        card.add(Core.bundle.format(kind.cost()==1?"shipyard.weaponcost.single":"shipyard.weaponcost",kind.cost())).color(Pal.accent)
            .fontScale(.8f).left().padTop(5f).row();
        card.add(Core.bundle.get("shipyard.weaponinfo."+kind.name())).color(Color.lightGray)
            .wrap().width(width-24f).fontScale(.82f).padTop(8f).left().row();
        card.add(weaponSpecs(tier,kind)).name("shipyard-specs-"+kind.name())
            .wrap().width(width-24f).fontScale(.8f).padTop(8f).left().row();
        return card;
    }

    private static String weaponSpecs(ShipTier tier,ShipWeapon kind){
        if(kind.utility())return utilitySpecs(tier,kind);
        float damage=ShipWeapons.damage(tier,kind);
        if(kind==ShipWeapon.eigen)return Core.bundle.format("shipyard.eigenspecs",Strings.autoFixed(damage,1));
        if(kind==ShipWeapon.missiles)return Core.bundle.format("shipyard.convergespecs",Strings.autoFixed(damage,1),Strings.autoFixed(27f*WeaponBalance.damageFactor(tier,kind),1),Strings.autoFixed(8f*WeaponBalance.damageFactor(tier,kind),1));
        if(kind==ShipWeapon.lancer)return Core.bundle.format("shipyard.involutionspecs",Strings.autoFixed(damage,1));
        if(kind==ShipWeapon.superfactorial)return Core.bundle.format("shipyard.superfactorialspecs",
            Strings.autoFixed(damage,1),Strings.autoFixed(ShipWeapons.range(tier,kind)/Vars.tilesize,1));
        if(kind==ShipWeapon.caustic)return Core.bundle.format("shipyard.causticspecs",
            Strings.autoFixed(damage,1),Strings.autoFixed(ShipWeapons.range(tier,kind)/Vars.tilesize,1));
        if(kind==ShipWeapon.asymptote)return Core.bundle.format("shipyard.asymptotespecs",
            Strings.autoFixed(damage*.2f,1),Strings.autoFixed(damage,1),Strings.autoFixed(ShipWeapons.range(tier,kind)/Vars.tilesize,1),
            Strings.autoFixed(ShipWeapons.source(kind).splashDamage*WeaponBalance.damageFactor(tier,kind),1));
        if(kind==ShipWeapon.railgun)return Core.bundle.format("shipyard.vectorspecs",Strings.autoFixed(damage,1));
        if(kind==ShipWeapon.chain)return Core.bundle.format("shipyard.recurrencespecs",Strings.autoFixed(damage,1));
        if(kind==ShipWeapon.flamethrower)return Core.bundle.format("shipyard.fluxspecs",Strings.autoFixed(damage,1),Strings.autoFixed(ShipWeapons.range(tier,kind)/Vars.tilesize,1));
        float reload=kind==ShipWeapon.standard?tier.base().weapons.first().reload:ShipWeapons.reload(kind);
        return Core.bundle.format("shipyard.weaponspecs",Strings.autoFixed(damage,1),
            Strings.autoFixed(ShipWeapons.range(tier,kind)/Vars.tilesize,1),Strings.autoFixed(reload/60f,2));
    }
    private static String utilitySpecs(ShipTier tier,ShipWeapon kind){
        float reach=UtilityBalance.range(tier,kind)/Vars.tilesize;
        if(kind==ShipWeapon.operator)return Core.bundle.format("shipyard.operatorspecs",Strings.autoFixed(reach,1));
        return kind==ShipWeapon.divergence?Core.bundle.format("shipyard.pulsespecs",Strings.autoFixed(reach,1),Strings.autoFixed(UtilityBalance.pulseReload(tier)/60f,2))
            :Core.bundle.format("shipyard.shieldspecs",Strings.autoFixed(UtilityBalance.capacity(tier),0),Strings.autoFixed(reach,1),Strings.autoFixed(UtilityBalance.recovery(tier)/60f,1));
    }

    private static Table panel(String key){
        Table panel = new Table(Styles.grayPanel);
        panel.name = key;
        panel.top().left().margin(12f);
        panel.add("@" + key).name(key + "-heading").color(Pal.accent).growX().padBottom(6f)
            .with(label -> label.setAlignment(Align.center)).row();
        return panel;
    }
    private static Slider control(Table table, float width, String name, arc.func.Prov<String> title, int initialStep){
        table.label(() -> title.get()).name(name + "-label").fontScale(.9f).width(width).left().padBottom(14f).row();
        Slider slider = new Slider(ShipLoadout.minStep, ShipLoadout.maxStep, 1f, false);
        slider.setValue(initialStep);
        table.add(slider).name(name).width(width).height(30f).padBottom(6f).row();
        return slider;
    }
    private static void comparison(Table table, float width, String key, Floatp current, Floatp proposed, String suffix){
        boolean inline = width >= 320f;
        Table row = inline ? new Table() : table;
        row.add(Core.bundle.get(key)).color(Color.lightGray).fontScale(.85f).left()
            .width(inline ? width * .5f : width).wrap().padBottom(inline ? 0f : 2f);
        if(!inline) row.row();
        row.add(Strings.autoFixed(current.get(), 2) + " > " + Strings.autoFixed(proposed.get(), 2) + suffix)
            .fontScale(.85f).width(inline ? width * .5f : width).right().with(label -> label.setAlignment(inline ? Align.right : Align.left)).update(label -> {
                label.setText(Strings.autoFixed(current.get(), 2) + " > " + Strings.autoFixed(proposed.get(), 2) + suffix);
                float difference = proposed.get() - current.get();
                label.setColor(Math.abs(difference) < .001f ? Color.lightGray : difference > 0 ? Color.green : Color.scarlet);
            });
        if(inline) table.add(row).width(width).padBottom(2f).row();
        else row.row();
    }

    private static String signedStep(int step){ return (step > 0 ? "+" : "") + step; }
    private static String signed(int step){ return signedStep(step) + "%"; }
}
