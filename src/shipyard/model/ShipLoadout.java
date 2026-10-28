package shipyard.model;

import mindustry.type.UnitType;

/** Integer slider steps keep client previews and authoritative calculations identical. */
public record ShipLoadout(int speed, int cargo, int construction, int durability, int fireRate, ShipWeapon weapon){
    public static final int minStep = -4, maxStep = 5;
    public static final ShipLoadout defaults = new ShipLoadout(0, 0, 0, 0, 0);

    public ShipLoadout(int speed, int cargo, int construction, int durability, int fireRate){
        this(speed, cargo, construction, durability, fireRate, ShipWeapon.standard);
    }

    /** Earlier callers default new controls to neutral. */
    public ShipLoadout(int speed, int cargo){
        this(speed, cargo, 0, 0, 0);
    }

    public ShipLoadout(int speed, int cargo, int construction){
        this(speed, cargo, construction, 0, 0);
    }

    public ShipLoadout withSpeed(int steps){ return new ShipLoadout(steps, cargo, construction, durability, fireRate, weapon); }
    public ShipLoadout withCargo(int steps){ return new ShipLoadout(speed, steps, construction, durability, fireRate, weapon); }
    public ShipLoadout withConstruction(int steps){ return new ShipLoadout(speed, cargo, steps, durability, fireRate, weapon); }
    public ShipLoadout withDurability(int steps){ return new ShipLoadout(speed, cargo, construction, steps, fireRate, weapon); }
    public ShipLoadout withFireRate(int steps){ return new ShipLoadout(speed, cargo, construction, durability, steps, weapon); }

    public ShipLoadout withWeapon(ShipWeapon weaponChoice){ return new ShipLoadout(speed, cargo, construction, durability, fireRate, weaponChoice); }

    public String encode(){ return stats() + (weapon == ShipWeapon.standard ? "" : "," + weapon.name()); }

    public String stats(){
        return speed + "," + cargo + "," + construction + "," + durability + "," + fireRate;
    }

    /** Two/three-field player profiles predate Durability and Fire Rate. */
    public static ShipLoadout parse(String raw){
        String[] fields = raw.split(",", -1);
        if(fields.length != 2 && fields.length != 3 && fields.length != 5 && fields.length != 6) throw new IllegalArgumentException("Invalid loadout field count");
        return new ShipLoadout(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
            fields.length >= 3 ? Integer.parseInt(fields[2]) : 0,
            fields.length >= 5 ? Integer.parseInt(fields[3]) : 0,
            fields.length >= 5 ? Integer.parseInt(fields[4]) : 0,
            fields.length == 6 ? ShipWeapon.parse(fields[5]) : ShipWeapon.standard);
    }

    public boolean inBounds(){
        return weapon != null && speed >= minStep && speed <= maxStep && cargo >= minStep && cargo <= maxStep
            && construction >= minStep && construction <= maxStep
            && durability >= minStep && durability <= maxStep && fireRate >= minStep && fireRate <= maxStep;
    }

    public int points(){
        return upgradePoints() + (weapon==null?0:weapon.cost()) - refundPoints();
    }

    /** Keep pre-cost save data loadable without silently altering a player's stat spread. */
    public ShipLoadout forRestore(ShipTier tier){
        if(valid(tier))return this;
        ShipLoadout scalar=withWeapon(ShipWeapon.standard);
        return scalar.valid(tier)?scalar:this;
    }

    public int upgradePoints(){
        return 2 * (Math.max(0, speed) + Math.max(0, cargo) + Math.max(0, construction) + Math.max(0, durability) + Math.max(0, fireRate));
    }

    public int refundPoints(){
        return Math.max(0, -speed) + Math.max(0, -cargo) + Math.max(0, -construction) + Math.max(0, -durability) + Math.max(0, -fireRate);
    }

    public int remainingPoints(ShipTier tier){
        return tier.budget - points();
    }

    public boolean valid(ShipTier tier){
        return tier != null && inBounds() && points() <= tier.budget;
    }

    public float speedFactor(){
        return 1f + speed * 0.10f - Math.max(0, cargo) * 0.05f;
    }

    public float speed(UnitType base){
        return base.speed * speedFactor();
    }

    public float buildSpeed(UnitType base){
        return base.buildSpeed * (1f + construction * 0.15f);
    }

    public float buildRange(UnitType base){
        return base.buildRange * (1f + construction * 0.10f);
    }

    public float fireRateFactor(){
        return (1f + fireRate * 0.10f) * (1f - Math.max(0, construction) * 0.08f);
    }

    public float damageReduction(){return Math.max(0,durability)*.03f;}

    public float health(UnitType base){
        return base.health * (1f + durability * 0.15f);
    }

    public float mineSpeed(UnitType base){
        return base.mineSpeed * (1f + cargo * 0.15f);
    }

    public int capacity(UnitType base){
        return Math.max(1, Math.round(base.itemCapacity * (1f + cargo * 0.20f)));
    }
}
