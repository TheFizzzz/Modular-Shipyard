package shipyard.graphics;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;

/** Pale activity highlights for dark engine palettes; never edits the selected colour. */
public final class EngineHighlights{
    private EngineHighlights(){}
    public static float mix(Color colour,float white){
        float brightness=Math.max(colour.r,Math.max(colour.g,colour.b));
        float lift=.32f*(1f-Mathf.clamp(brightness/.6f));
        return white+(1f-white)*lift;
    }
    public static Color colour(Color colour,float white){return colour.cpy().lerp(Color.white,mix(colour,white));}
    public static void draw(Color colour){draw(colour,0f);}
    public static void draw(Color colour,float white){Draw.color(colour,Color.white,mix(colour,white));}
}
