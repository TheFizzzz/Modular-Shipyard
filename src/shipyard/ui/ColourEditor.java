package shipyard.ui;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.graphics.g2d.Fill;
import arc.scene.Element;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.ui.Styles;
import shipyard.model.*;

/** Native Arc controls: immediate HEX/HSV draft editing, with no world-side effects. */
final class ColourEditor extends Table{
    private final Unit ship;
    private final ShipCosmetics[] draft;
    private TextField hex;
    private final Slider hue, saturation, brightness;
    private final Color selected = new Color();
    private final float[] hsv = new float[3];
    private int channel;
    private boolean updating = true;

    ColourEditor(Unit ship, ShipCosmetics[] draft, float width){
        this.ship = ship;
        this.draft = draft;
        table(tabs -> {
            String[] names = {"shipyard.primary", "shipyard.secondary", "shipyard.tertiary"};
            for(int index = 0; index < names.length; index++){
                final int chosen = index;
                tabs.button("@" + names[index], Styles.flatTogglet, () -> {
                    channel = chosen;
                    sync();
                }).name("shipyard-colour-tab-"+index).width(width / 3f).height(42f).checked(button -> channel == chosen)
                    .with(button -> button.getLabel().setFontScale(.8f));
            }
        }).padBottom(8f).row();

        image(Tex.whiteui).size(width, 22f).update(image -> image.setColor(selected)).row();
        table(input -> {
            input.add("HEX #").fontScale(.85f).padRight(6f);
            hex = input.field("", this::fromHex).name("shipyard-hex").width(width - 74f).height(42f).get();
        }).padTop(12f).padBottom(6f).row();
        hex.setMaxLength(7);
        hex.setProgrammaticChangeEvents(false);
        hex.setValidator(ShipCosmetics::validHex);
        hue = new Slider(0f, 360f, .1f, false);
        saturation = new Slider(0f, 1f, .001f, false);
        brightness = new Slider(0f, 1f, .001f, false);
        Slider[] sliders = {hue, saturation, brightness};
        String[] labels = {"shipyard.hue", "shipyard.saturation", "shipyard.brightness"};
        String[] names = {"shipyard-hue", "shipyard-saturation", "shipyard-brightness"};
        for(int i = 0; i < sliders.length; i++){
            final int index = i;
            table(row -> {
                row.add("@" + labels[index]).left().width(96f).fontScale(.8f);
                sliders[index].name = names[index];
                row.stack(new Gradient(index), sliders[index]).size(width - 108f, 38f);
            }).padTop(6f).row();
            sliders[i].changed(this::fromSliders);
        }
        label(() -> !validInput() ? Core.bundle.get("shipyard.invalidhex")
            : Core.bundle.get(draft[0].channel(channel) == -1 ? "shipyard.defaultcolour" : "shipyard.customcolour"))
            .fontScale(.8f).wrap().width(width).minHeight(32f).color(Color.lightGray).pad(4f).row();
        button("@shipyard.resetcolour", () -> {
            draft[0] = draft[0].withChannel(channel, -1);
            sync();
        }).width(width).height(40f).row();
        button("@shipyard.randomizecolour", () -> {
            randomize(channel);
            sync();
        }).name("shipyard-randomize-colour").width(width).height(40f).padTop(8f).row();
        button("@shipyard.randomizeall",()->{
            for(int index=0;index<3;index++)randomize(index);
            sync();
        }).name("shipyard-randomize-all").width(width).height(40f).padTop(8f).row();
        sync();
    }

    private void randomize(int index){
        int current=draft[0].channel(index),rgb=Mathf.random(0xfffffe);
        if(current<0)rgb=Mathf.random(0xffffff);else if(rgb>=current)rgb++;
        draft[0]=draft[0].withChannel(index,rgb);
    }
    void refresh(){sync();}

    boolean validInput(){ return hex != null && hex.isValid(); }

    void resetColours(){
        draft[0] = ShipCosmetics.defaults;
        sync();
    }

    private void sync(){
        updating = true;
        selected.set(draft[0].displayed(channel, ship));
        selected.toHsv(hsv);
        hue.setValue(hsv[0]); saturation.setValue(hsv[1]); brightness.setValue(hsv[2]);
        hex.setText(ShipCosmetics.token(selected.rgb888()));
        updating = false;
    }

    private void fromHex(String text){
        // Incomplete typing keeps the last valid draft; the field validator blocks Apply.
        if(updating || !ShipCosmetics.validHex(text))return;
        int rgb=ShipCosmetics.parseHex(text);
        draft[0]=draft[0].withChannel(channel,rgb);selected.rgb888(rgb).toHsv(hsv);
        updating=true;
        hue.setValue(hsv[0]);saturation.setValue(hsv[1]);brightness.setValue(hsv[2]);
        updating=false;
    }

    private void fromSliders(){
        if(updating) return;
        selected.fromHsv(hue.getValue(), saturation.getValue(), brightness.getValue());
        draft[0] = draft[0].withChannel(channel, selected.rgb888());
        hex.setText(ShipCosmetics.token(selected.rgb888()));
    }

    private final class Gradient extends Element{
        private final int kind;
        private final Color left = new Color(), right = new Color();
        Gradient(int kind){ this.kind = kind; }
        @Override
        public void draw(){
            int segments = kind == 0 ? 6 : 1;
            for(int i = 0; i < segments; i++){
                if(kind == 0){
                    left.fromHsv(i * 60f, 1f, 1f); right.fromHsv((i + 1) * 60f, 1f, 1f);
                }else if(kind == 1){
                    left.fromHsv(hue.getValue(), 0f, brightness.getValue());
                    right.fromHsv(hue.getValue(), 1f, brightness.getValue());
                }else{
                    left.fromHsv(hue.getValue(), saturation.getValue(), 0f);
                    right.fromHsv(hue.getValue(), saturation.getValue(), 1f);
                }
                float a = left.a(parentAlpha).toFloatBits(), b = right.a(parentAlpha).toFloatBits();
                float x1 = x + width * i / segments, x2 = x + width * (i + 1) / segments;
                Fill.quad(x1, y, a, x2, y, b, x2, y + height, b, x1, y + height, a);
            }
        }
    }
}
