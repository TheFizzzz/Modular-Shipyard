package shipyard.graphics;

import arc.graphics.Pixmap;

/** Palette snapping for generated pixel artwork; runs only while packing the atlas. */
public final class SpritePalette{
    private SpritePalette(){}
    public static void snap(Pixmap pixels,int... palette){
        if(palette.length==0)throw new IllegalArgumentException("A sprite palette must not be empty");
        for(int y=0;y<pixels.height;y++)for(int x=0;x<pixels.width;x++){
            int pixel=pixels.getRaw(x,y);
            if((pixel&255)<128){pixels.setRaw(x,y,0);continue;}
            int closest=palette[0],distance=Integer.MAX_VALUE;
            for(int colour:palette){
                int r=(pixel>>>24)-(colour>>>24),g=((pixel>>>16)&255)-((colour>>>16)&255),b=((pixel>>>8)&255)-((colour>>>8)&255);
                int difference=r*r+g*g+b*b;
                if(difference<distance){distance=difference;closest=colour;}
            }
            pixels.setRaw(x,y,closest);
        }
    }
}
