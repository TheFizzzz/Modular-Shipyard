package shipyard.model;

import arc.graphics.Color;
import mindustry.gen.Unit;

/** Opaque RGB channels, or -1 for native defaults. Cosmetics never alter the stat budget. */
public record ShipCosmetics(int hull, int accents, int engine){
    public static final ShipCosmetics defaults = new ShipCosmetics(-1, -1, -1);

    public boolean valid(){ return valid(hull) && valid(accents) && valid(engine); }
    private static boolean valid(int rgb){ return rgb >= -1 && rgb <= 0xffffff; }
    public int channel(int index){
        return switch(index){ case 0 -> hull; case 1 -> accents; case 2 -> engine; default -> throw new IllegalArgumentException("Unknown colour channel"); };
    }
    public ShipCosmetics withChannel(int index, int rgb){
        return switch(index){
            case 0 -> new ShipCosmetics(rgb, accents, engine);
            case 1 -> new ShipCosmetics(hull, rgb, engine);
            case 2 -> new ShipCosmetics(hull, accents, rgb);
            default -> throw new IllegalArgumentException("Unknown colour channel");
        };
    }
    public String encode(){ return token(hull) + "," + token(accents) + "," + token(engine); }
    public static ShipCosmetics parse(String raw){
        String[] channels = raw.split(",", -1);
        if(channels.length != 3) throw new IllegalArgumentException("Invalid colour field count");
        return new ShipCosmetics(parseToken(channels[0]), parseToken(channels[1]), parseToken(channels[2]));
    }
    private static int parseToken(String raw){ return raw.equals("-") ? -1 : parseHex(raw); }
    public static boolean validHex(String raw){return raw!=null && raw.matches("#?[0-9a-fA-F]{6}");}
    public static int parseHex(String raw){
        if(!validHex(raw))throw new IllegalArgumentException("Use six HEX digits");
        String hexDigits=raw.startsWith("#")?raw.substring(1):raw;
        return Integer.parseInt(hexDigits,16);
    }
    public static String token(int rgb){return rgb==-1?"-":String.format(java.util.Locale.ROOT,"%06X",rgb);}
    public static Color colour(int rgb){ return new Color((rgb << 8) | 0xff); }
    public Color displayed(int index, Unit ship){
        int rgb = channel(index);
        if(rgb != -1) return colour(rgb);
        return switch(index){
            case 0 -> Color.white.cpy();
            case 1 -> ship.team.color.cpy();
            case 2 -> {
                var tier = ShipTier.of(ship.type);
                var base = tier == null ? ship.type : tier.base();
                yield (base.engineColor == null ? ship.team.color : base.engineColor).cpy();
            }
            default -> throw new IllegalArgumentException("Unknown colour channel");
        };
    }
}
