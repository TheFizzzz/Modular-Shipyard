# Caustic penetration — moving combat review (v0.20.3)

Caustic now ignores **50% of a unit's armour after that firing ship completes its own corrosion buildup**. The beam's starting damage, 1.5-second buildup, point cost and range are unchanged. The reward refreshes during sustained contact and lasts up to three seconds after contact, while Corroded remains present. Another player's Corroded does not grant it. Ordinary Corroded damage, other weapons and shared enemy armour are unchanged.

## Results

The moving-player tests confirm that the reward helps medium-armour matchups, but **does not solve heavy armour**. These are scripted moving encounters under native enemy fire; they replace the stationary/no-retaliation comparison as the primary gameplay probe for this change.

Example: Gamma against a Fortress with three Dagger attackers, maximum Durability, five-tile orbit, continuous fire:

| Weapon / reward | Kill time | Player HP lost | Player outcome |
| --- | ---: | ---: | --- |
| Caustic, previous behaviour | 16.00s | 30.60 | Survives |
| Caustic, 50% armour ignored after buildup | 8.72s | 22.95 | Survives |
| Flux, motion-compensated aim | 1.50s | 7.65 | Survives |

The comparable weaving Caustic cases change from 16.00s to 8.72s as well; the ship takes more hits on that route. Flux retains the close-range damage advantage even with Caustic's reward. Current-position Flux aiming is separately recorded: inherited ship velocity can carry flames past a target while orbiting, so this is not treated as its best possible damage output.

### Why 50% is insufficient for T4–5 armour

Gamma's Caustic beam deals 8.4 damage per hit. A Scepter has 20 armour: half is still 10, above that hit's damage. Native armour therefore still reduces it to the 10% minimum, or approximately 0.84 damage per hit. Reign's 30 armour likewise remains too high after halving. Corroded's periodic damage receives no added penetration. Native Scepter shield regeneration compounds this weakness.

Some close T4–5 encounters kill the player before the 1.5-second buildup completes, despite continuous movement and maximum Durability. In those cases the reward never activates; the CSV records both survival and penetration uptime. These results should not be interpreted as proof that skilled human dodging is impossible: the routes are predetermined and do not react to visible bullets or time their approach between enemy volleys. Enemy guns start ready, and the benchmark does not provide cover or friendly defenses.

**Suggested next discussion:** if the goal is to make small Caustic hits consistently useful against heavy armour, half of the damage bypassing armour is a different mechanic from halving the armour value. Larger, less-frequent damage ticks would also cross the armour threshold. Neither is implemented here; the requested value remains exactly 50% of armour ignored after buildup.

## Method

1,056 cases on each runtime, **v160.2 and v160.7, with identical CSV rows**. All normal regression checks also pass.

- Both player weapon tests start already moving and continue along an orbit or reversing weave. Speed and steering acceleration are bounded by native ship values; movement is asserted on at least 95% of simulated frames (over 99% in these runs).
- The focused enemy also moves laterally. Its native guns fire if they can hit air and reach the player. Three or ten native Dagger attackers add crossfire from around the encounter. Every recorded case has actual enemy shots; native projectiles, armour, shields, status damage and death run normally.
- Player HP is real, not inflated. Cases end on player death, target death, or 20 seconds. Both neutral stats and a legal +5 Durability loadout are tested. The defensive profile funds its stats with -4 Mining/Cargo and -2 Construction; both weapons use the same profile. No Fire Rate upgrade is used.
- Flux is tested at five tiles with both current-position and velocity-compensated aiming. Caustic is tested at five and twelve tiles with current-position beam aiming. Both continuous fire and one second on / half a second off are included.
- Alpha faces Dagger/Mace/Flare; Beta faces Mace/Fortress/Zenith; Gamma faces Fortress/Scepter/Reign/Antumbra/Eclipse. Lower-tier matchups are included to measure where the 50% reward actually crosses the armour threshold.
- Before/after Caustic comparisons run the same production code, but the old-behaviour fixture clears only its private penetration timer after each tick. Corrosion buildup and native Corroded damage remain enabled. A fixed random seed and reset world animation tick are used per case.
- No auto-retargeting to the harassment units, human reaction model, reactive bullet-avoidance planner, terrain, cover or allied defenses is simulated. Player death, missed shots, interrupted buildup and shield absorption remain in the results. A capped case is not an exact TTK.

Raw data: [v160.7](moving-combat-1607.csv), [v160.2](moving-combat-1602.csv). `target_hp_lost` measures HP lost, excluding damage absorbed by shields; `player_hp_lost` records health actually lost after damage reduction. `enemy_shots` counts native weapon shots, not guaranteed hits. `beam_damage` is the weapon's base per-projectile damage (Flux uses physical flame projectiles).

Reproduce:

```sh
SHIPYARD_MOVING_COMBAT=1 ./gradlew smokeTest -PmindustryRuntimeJar=/absolute/path/mindustry-runtime.jar
```

Dedicated regressions also verify that foreign Corroded grants no benefit, partial buildup grants no benefit, the correct owner receives native armour-adjusted damage, the other owner does not, the three-second tail expires, and shared projectile/status/armour definitions remain unchanged. Penetration ownership is transient like the existing buildup: loading a save retains native Corroded but requires rebuilding this ship's own reward.
