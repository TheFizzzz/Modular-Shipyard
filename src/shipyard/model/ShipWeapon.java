package shipyard.model;

import arc.Core;
import arc.graphics.Color;

/** Stable save/packet tokens and shared client/server selection costs. */
public enum ShipWeapon{
    standard, flamethrower, lancer, fuse, railgun, chain, missiles, divergence, invariant, caustic, asymptote, superfactorial, operator, eigen;
    private static final Color utilityGreen=Color.valueOf("8FE388");
    public boolean utility(){return this==divergence || this==invariant || this==operator;}
    /** Fuse stays readable for old saves and in-flight projectiles, but is no longer offered. */
    public boolean selectable(){return this!=fuse;}
    public int cost(){
        return switch(this){
            case standard -> 0;
            case divergence -> 1;
            case flamethrower,fuse,missiles,caustic,invariant,asymptote,superfactorial -> 2;
            case lancer,chain,operator -> 3;
            case railgun,eigen -> 4;
        };
    }
    public Color titleColour(){ return utility()?utilityGreen:Color.orange; }
    public String display(){ return Core.bundle.get("shipyard.weapon." + name()); }
    public static ShipWeapon parse(String token){ return valueOf(token); }
}
