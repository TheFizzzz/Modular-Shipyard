package shipyard.ui;

import arc.func.Prov;
import arc.graphics.g2d.Draw;
import arc.math.Mat;
import arc.scene.Element;
import mindustry.gen.Unit;
import shipyard.content.ShipVariants;
import shipyard.model.*;

/** Unadded preview unit: no world mutation, content registration or GPU texture allocation. */
final class ShipPreview extends Element{
    private final Unit source, preview;
    private final ShipTier tier;
    private final Prov<ShipLoadout> loadout;
    private final Prov<ShipCosmetics> cosmetics;
    private ShipLoadout lastLoadout;
    private ShipCosmetics lastCosmetics;
    private final Mat previous = new Mat(), transform = new Mat();

    ShipPreview(Unit source, Prov<ShipLoadout> loadout, Prov<ShipCosmetics> cosmetics){
        this.source = source;
        this.tier = ShipTier.of(source.type);
        this.loadout = loadout;
        this.cosmetics = cosmetics;
        preview = tier.base().create(source.team);
        preview.rotation = 90f;
        preview.elevation = 1f;
    }

    @Override
    public void draw(){
        ShipLoadout stats = loadout.get();
        ShipCosmetics colours = cosmetics.get();
        if(!stats.valid(tier)) stats = ShipVariants.current(source.type);
        if(!stats.equals(lastLoadout) || !colours.equals(lastCosmetics)){
            preview.setType(ShipVariants.get(tier, stats, colours));
            shipyard.content.ShipWeapons.bind(preview);
            lastLoadout = stats;
            lastCosmetics = colours;
        }
        preview.team = source.team;
        preview.health = preview.maxHealth * source.healthf();
        float halfWidth = preview.type.region.width * preview.type.region.scl() / 2f;
        float halfHeight = preview.type.region.height * preview.type.region.scl() / 2f;
        float minX = -halfWidth, maxX = halfWidth, minY = -halfHeight, maxY = halfHeight;
        // Include the full engine pulse; its glow extends past the hull sprite.
        for(var engine : preview.type.engines){
            float radius = engine.radius * 1.25f;
            minX = Math.min(minX, engine.x - radius);maxX = Math.max(maxX, engine.x + radius);
            minY = Math.min(minY, engine.y - radius);maxY = Math.max(maxY, engine.y + radius);
        }
        float scale = .78f * Math.min(width / Math.max(1f, maxX - minX), height / Math.max(1f, maxY - minY));
        previous.set(Draw.trans());
        float z = Draw.z(), colour = Draw.getColorPacked(), mix = Draw.getMixColor().toFloatBits();
        float sx = Draw.xscl, sy = Draw.yscl;
        transform.set(previous).translate(x + width / 2f - (minX + maxX) * scale / 2f,
            y + height / 2f - (minY + maxY) * scale / 2f).scale(scale, scale);
        try{
            Draw.trans(transform);
            preview.type.drawOutline(preview);
            preview.type.drawEngines(preview);
            preview.type.drawBody(preview);
            preview.type.drawCell(preview);
            preview.type.drawWeapons(preview);
        }finally{
            Draw.reset();
            Draw.trans(previous);
            Draw.z(z);
            Draw.scl(sx, sy);
            Draw.color(colour);
            Draw.mixcol(mix);
        }
    }
}
