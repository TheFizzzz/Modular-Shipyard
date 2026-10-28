package shipyard.graphics;

import arc.Events;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.Shader;
import arc.math.*;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.DisposeEvent;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

/** One palette shader for painted surfaces; native cells, outlines and heat remain separate. */
public final class HullPaint{
    private static PaintShader shader;
    private HullPaint(){}

    public static void rect(TextureRegion region, float x, float y, float rotation, Color paint, Color accent){
        float colour = Draw.getColorPacked(), mix = Draw.getMixColorPacked();
        float sx = Draw.xscl, sy = Draw.yscl;
        float pr = paint == null ? 1f : paint.r, pg = paint == null ? 1f : paint.g, pb = paint == null ? 1f : paint.b;
        float ar = accent == null ? 1f : accent.r, ag = accent == null ? 1f : accent.g, ab = accent == null ? 1f : accent.b;
        float hullEnabled = paint == null ? 0f : 1f, accentEnabled = accent == null ? 0f : 1f;
        // v160.2 sorts world sprites. Shader changes must execute inside a queued draw.
        Draw.draw(Draw.z(), () -> {
            if(shader == null){
                shader = new PaintShader();
                Events.on(DisposeEvent.class, event -> { if(shader != null){ shader.dispose(); shader = null; } });
            }
            Shader previous = Draw.getShader();
            float oldColour = Draw.getColorPacked(), oldMix = Draw.getMixColorPacked();
            float oldSx = Draw.xscl, oldSy = Draw.yscl;
            try{
                shader.pr = pr; shader.pg = pg; shader.pb = pb;
                shader.ar = ar; shader.ag = ag; shader.ab = ab;
                shader.hullEnabled = hullEnabled; shader.accentEnabled = accentEnabled;
                Draw.shader(shader);
                Draw.color(colour); Draw.mixcol(mix); Draw.scl(sx, sy);
                Draw.rect(region, x, y, rotation);
            }finally{
                Draw.shader(previous);
                Draw.color(oldColour); Draw.mixcol(oldMix); Draw.scl(oldSx, oldSy);
            }
        });
    }

    /** Native v160.2 weapon transforms; only the base sprite uses the paint shader. */
    public static void weapon(Unit unit, WeaponMount mount, Color paint, Color accent){
        var weapon = mount.weapon;
        // The supported vanilla core ships have no draw parts. Delegate unfamiliar visuals.
        if(weapon.parts.size > 0){ weapon.draw(unit, mount); return; }
        float z = Draw.z();
        Draw.z(z + weapon.layerOffset);
        float rotation = unit.rotation - 90f;
        float recoil = Mathf.pow(mount.recoil, weapon.recoilPow) * weapon.recoil;
        float angle = rotation + (weapon.rotate ? mount.rotation : weapon.baseRotation);
        float x = unit.x + Angles.trnsx(rotation, weapon.x, weapon.y) + Angles.trnsx(angle, 0, -recoil);
        float y = unit.y + Angles.trnsy(rotation, weapon.x, weapon.y) + Angles.trnsy(angle, 0, -recoil);
        if(weapon.shadow > 0) Drawf.shadow(x, y, weapon.shadow);
        if(weapon.top) weapon.drawOutline(unit, mount);
        float previous = Draw.xscl;
        Draw.xscl *= -Mathf.sign(weapon.flipSprite);
        unit.type.applyColor(unit);
        if(weapon.region.found()) rect(weapon.region, x, y, angle, paint, accent);
        if(weapon.cellRegion.found()){
            Draw.color(unit.type.cellColor(unit));
            Draw.rect(weapon.cellRegion, x, y, angle);
            Draw.color();
        }
        if(weapon.heatRegion.found() && mount.heat > 0){
            Draw.color(weapon.heatColor, mount.heat);
            Draw.blend(Blending.additive);
            Draw.rect(weapon.heatRegion, x, y, angle);
            Draw.blend(); Draw.color();
        }
        Draw.xscl = previous;
        Draw.z(z);
    }

    private static final class PaintShader extends Shader{
        float pr, pg, pb, ar, ag, ab, hullEnabled, accentEnabled;
        PaintShader(){
            super("""
                attribute vec4 a_position;
                attribute vec4 a_color;
                attribute vec2 a_texCoord0;
                attribute vec4 a_mix_color;
                uniform mat4 u_projTrans;
                varying vec4 v_color;
                varying vec4 v_mix_color;
                varying vec2 v_texCoords;
                void main(){
                    v_color = a_color;
                    v_color.a *= 255.0/254.0;
                    v_mix_color = a_mix_color;
                    v_mix_color.a *= 255.0/254.0;
                    v_texCoords = a_texCoord0;
                    gl_Position = u_projTrans * a_position;
                }
                """, """
                varying lowp vec4 v_color;
                varying lowp vec4 v_mix_color;
                varying highp vec2 v_texCoords;
                uniform sampler2D u_texture;
                uniform vec3 u_paint;
                uniform vec3 u_accent;
                uniform float u_hullEnabled;
                uniform float u_accentEnabled;
                void main(){
                    vec4 c = texture2D(u_texture, v_texCoords);
                    float tone = max(c.r, max(c.g, c.b));
                    float surface = smoothstep(0.27, 0.38, tone);
                    // Native gold/orange markings are baked into hull and weapon sprites.
                    float warm = smoothstep(0.08, 0.18, c.r - c.b) * smoothstep(0.02, 0.08, c.g - c.b);
                    float shade = 0.62 + 0.38 * clamp((tone - 0.30) / 0.50, 0.0, 1.0);
                    vec3 hull = mix(c.rgb, u_paint * shade, surface * u_hullEnabled);
                    // Keep distinct orange shadows/yellow highlights, with gentler contrast.
                    // Max RGB compresses those two colours and flattens the gun details.
                    float nativeAccentShade = clamp(dot(c.rgb, vec3(0.2126, 0.7152, 0.0722)) / 0.84, 0.0, 1.0);
                    float accentShade = mix(1.0, nativeAccentShade, 0.75);
                    vec3 accent = mix(c.rgb, u_accent * accentShade, u_accentEnabled);
                    c.rgb = mix(hull, accent, warm);
                    gl_FragColor = v_color * mix(c, vec4(v_mix_color.rgb, c.a), v_mix_color.a);
                }
                """);
        }
        @Override public void apply(){
            setUniformf("u_paint", pr, pg, pb); setUniformf("u_accent", ar, ag, ab);
            setUniformf("u_hullEnabled", hullEnabled); setUniformf("u_accentEnabled", accentEnabled);
        }
    }
}
