# Modular Shipyard

A Java mod for **Mindustry v160.2 / Java 17**, supporting Serpulo's Alpha, Beta and Gamma. **v0.29.7** uses a planet-neutral mod-menu description and provides a prepared Steam Workshop folder under `build/workshop/modular-shipyard`. Current gameplay support remains Serpulo; Erekir support is planned.

## Multiplayer presentation fixes

Caustic and Invariant use a small aim stream with bounded interpolation. Guests also send presentation-only aim frames, checked against the ship they actually control, so the host and other observers receive smooth input. Caustic's rendered length follows this stream while authoritative hits retain native input. Eigen drones have no pushing body, and Recurrence's confirmed follow-up arcs are replicated to observers.

Vector keeps its one-second charge and heavier Foreshadow report. Steam vents from its guns throughout the post-shot reload, tapering off over the final 0.4 seconds alongside a positional steam hiss; Asymptote produces Engine-coloured sparks throughout its reload, with a positional circuit crackle at 100% event volume fading over the last 0.2 seconds. Both decorations move with their guns, work with custom hull/accents, and stop when ready. Caustic retains its 80% sustain volume and short start/stop cues. The occupied Shipyard shows the saved ship palette beneath its pink fabrication scan.

A custom Engine colour also colours the construction beam and mining laser/target marker. Demolition retains the original game colour and rendering. Default Engine settings preserve vanilla work-beam colours.

## Playtest

Open **Ship cosmetics** in the main menu to set default colours before entering a map. These preferences stay on your device and are sent to modded hosts when you join. Saved Shipyard colours take priority. Stats, weapons and named loadouts remain in the in-game Shipyard.

1. Import `build/libs/modular-shipyardDesktop.jar` from Mindustry's Mods menu and restart. The server and every client need the same version.
2. Place the **3×3 Shipyard** from the units category. It remains unlocked for playtesting and costs 200 copper, 200 lead, 150 silicon and 100 titanium.
3. Connect a grid supplying its full **60 power/second** demand. A sandbox Power Source works; a physical connection is required even with power cheats.
4. Enable the possession game rule. While controlling an Alpha, Beta or Gamma within **8 tiles** of the Shipyard centre, use the native control-select gesture (desktop default: **Left Ctrl + click**). There is no configuration-button entry.
5. The player controls the block's actual native BlockUnit. The original ship is held inside the Shipyard, out of the world. A muted-pink native unit-fabricator scan runs over the landing pad while its editor is occupied; other players can see it too.
6. Adjust the draft and press **Apply & save**. **Close** or Escape returns the original ship with its cargo and current HP. Unapplied changes are discarded. **Reset**, then **Are you sure?**, resets the draft to vanilla settings; Apply commits it.

One player can use a Shipyard at a time. Power loss, disabling/removing the building and leaving control return the stored ship. A disconnected player's ship stays stored until that player reconnects. Occupied saves preserve the actual ship and its loadout; loading/reconnecting returns it safely rather than creating an extra core ship. The editor no longer depends on moving a ship around while open.

## Named loadouts

Open **Loadout: Custom** beside the ship tier. Choose **Save as…**, enter a name, then **Apply & save** in the editor. This saves the full stat spread, weapon and all three colours. Saving again with that name selected updates it.

Selecting a saved loadout fills the draft for review; **Apply & save** equips it. **Custom** keeps the draft and stops updating a named loadout. Reset keeps the selected name, so applying a reset overwrites that loadout with defaults. Deleting a loadout asks for confirmation and does not change the equipped ship.

Each player has up to **12 named loadouts per tier**, stored separately for Alpha, Beta and Gamma. Names accept 1–32 letters, numbers, spaces, dots, apostrophes, hyphens or underscores. Loadouts and the selected name persist on the host/server alongside existing player profiles. Unapplied changes and unsaved names are discarded when closing.

## Shipyard appearance

The 96×96 sprite retains the 3×3 footprint and Serpulo factory shading. Slim upright corner emitters overlap a recessed square landing pad, with paired desaturated-pink panels on each side. Unpowered/disabled blocks have dark panels; powered idle blocks show muted pink. During possession, emitter tips and side panels brighten and the native fabrication shader scans the held ship in pink. No connecting beams are drawn. The held ship display fits the pad for Alpha, Beta and Gamma without changing the actual ship's size or stats.

Editable SVG sources for the powered sprite, unpowered sprite and occupied-light overlay are in `assets-raw/`; runtime PNGs are in `assets/sprites/`. See [sprite build notes](assets-raw/README.md).

## Stats and points

Budgets are **Alpha 6 / Beta 10 / Gamma 14**. Positive steps cost 2 points; deliberate negative steps refund 1. Refunds belong to the current configuration and cannot be banked by repeatedly applying it. Automatic penalties return no points.

| Slider | Per step | Positive-step trade-off |
| --- | --- | --- |
| Speed | 10% base movement speed | — |
| Mining & Cargo | 15% mining speed and 20% capacity | −5% base movement speed |
| Construction | 15% build speed and 10% build range | −8% fire-rate factor |
| Durability | 15% maximum HP; positive steps also grant 3% damage reduction (15% maximum) | — |
| Fire Rate | 10% fire-rate factor | — |

All sliders run from −4 to +5 steps. Fire Rate and Construction combine as `(1 + fireStep*0.10) * (1 - max(0,constructionStep)*0.08)`. Capacity rounds to whole items. Mining tier/access/range and armor are unchanged. The live comparisons include all trade-offs, shot damage and weapon range.

Durability preserves health percentage; changing loadouts does not heal the ship. Other changes preserve current HP. Unload before reducing capacity below the amount carried: cargo is never silently deleted.

To test construction range/speed, disable sandbox infinite resources and instant build, supply materials and use an ordinary power grid. Those cheats bypass range or conceal speed differences.

## Weapons and tier scaling

| Choice | Behaviour | Reach | Points |
| --- | --- | --- | ---: |
| Scalar | Original tier blasters and firing pattern | Original tier range | 0 |
| Flux | Scorch-style flames with Burning; two-target cap | About 7.5 tiles | 2 |
| Involution | Cursor-guided blade; exact retrace, +25% return damage, accelerating final catch | 24.84 tiles outward | 3 |
| Vector | Single-target rail; 40% damage within 8 tiles, full at 24; 60-tick charge, 180-tick reload; no piercing | 40 tiles | 4 |
| Recurrence | Aimed first hit, then 3 nearby targets; 25% loss per jump | 14 tiles initially, 8 per jump | 3 |
| Converge | Stronger short-flight homing missile; timeout releases three weaker homing bomblets | 14 tiles main; bomblets up to 25.5 more | 2 |
| Eigen | Hold fire to launch and direct nine weaving energy orbs; release to recall; spent drones rebuild in 3s | 24-tile cursor anchor | 4 |
| Superfactorial | Minigun ramps from 2 to 30 shots/s per gun over 3s; ±24° spread | 20 tiles | 2 |
| Divergence | Radial, damage-free push; heavier enemies resist more | 7.5 / 10 / 12.5 tiles by tier | 1 |
| Operator | Hold on one enemy to grab, drag with the cursor, release to drop; energy-powered | 12 / 16 / 20 tiles by tier | 3 |
| Invariant | Hold fire for a close, aimed C-shaped shield; release to recharge | 4 / 5 / 6 tiles radius by tier | 2 |
| Caustic | Narrow wavy strands; sustained tracking applies Corroded and lets your beams ignore 50% armour | 15.6 tiles to the convergence point | 2 |
| Asymptote | Slow-launch accelerating orb; low direct damage and a 4-tile-radius blast; damage charges over 25 tiles | 30 tiles | 2 |

The clustered picker uses centered Damage/Utility headings, equal card sizes, ascending costs within each cluster, **orange combat titles** and **green utility titles**, including the selected weapon in Configuration. Short descriptions explain the role; numeric mechanics sit underneath. Fuse is removed from the picker; its stable token/projectile remains readable for older saves/loadouts as **Chord (legacy)**. Existing saved weapons retain their internal tokens, so the cosmetic renames do not invalidate profiles.

Combat weapons hit **air and ground**; Divergence pushes both and Operator can grab either. Native gun positions/sprites are retained. Weapons consume the same points as stats; the picker and Configuration show their costs. Switching combat weapons starts the new gun's cooldown and cancels pending charged shots. Fire Rate/Construction affect combat reloads, Caustic's tick interval and Divergence's pulse interval. Utility recovery timers are independent of those sliders. Range/charge/homing/property sliders are not implemented.

### Weapon roles and budget changes

Costs are a first-pass opportunity cost, shared across tiers: the weapon competes with stat upgrades within the existing 6/10/14 budgets. Manual stat reductions can fund it; removing a weapon never grants extra refund points. Scalar remains free, and legacy Chord costs two points. Costs are enforced on Apply by the authority, not only by the UI.

Vector's damage rises linearly from 40% within eight tiles to 100% at 24 tiles, with full damage through its unchanged 40-tile reach. Distance is measured to the first target from the muzzle at firing time. Involution replaces Secant's charged beam with a returning blade that rewards repositioning. Flux rewards close sweeping attacks and Burning; Caustic rewards sustained narrow tracking and Corroded. Recurrence chains immediately through clustered enemies, whereas Asymptote delivers a delayed area blast. Converge provides homing assistance, and Superfactorial trades precision and quick reactions for sustained fire. These distinctions guide the costs; no further weapon removal or broad damage rebalance is included.

An older stat spread that fits its tier but becomes over budget solely due to weapon cost restores with Scalar and its original stats/colours. Its profile text remains intact. Named presets remain available with an **over budget** marker so players can load them into the draft, reallocate points and Apply. Occupied saves preserve the actual held ship, cargo and HP during this fallback. Invalid stat spreads remain rejected.

### Involution

Involution fires an Engine-coloured spinning blade with no charge delay. Outbound blades follow the cursor regardless of fire input, up to 24.84 tiles from launch; total travel is bounded to prevent endless cursor loops. Outbound speed is 5 world units/tick. The actual recorded path then retraces at 6 units/tick and its flowing filaments erase as the blade returns. Return hits deal 25% more damage: 18.75 / 48.75 / 150 across tiers, compared with outbound 15 / 39 / 120. A full-volume pitched-up Sap cue marks turnaround. After retracing, the blade aims directly at its original owner and accelerates until caught, without the old short catch timeout. Owner removal, docking, death or team change removes the blade safely.

Native guns alternate roughly every 0.67 seconds before Fire Rate modifiers. Each blade hits at most two enemies per leg and each enemy once per leg; units and buildings share that cap and allies remain safe. The `lancer` save token and three-point cost are unchanged.

The existing `lancer` save token and three-point cost are retained, so saved Secant loadouts automatically use Involution. The old laser's projectile ID stays registered for compatibility, while Involution receives one appended fixed ID. Each blade's collision flags and hit history belong to that shot.

### Eigen

Eigen costs **4 points** and controls at most **nine native flying energy orbs per owner**. Holding fire launches them from alternating gun muzzles over about three seconds and directs them toward a cursor anchor clamped to 24 tiles. Instead of clustering tightly, phase-offset figure-eight passes weave through a broad area around the cursor. Release recalls surviving drones to a moving orbit. Commanded guidance remains capped at 4.8 world units/tick. Involution retains its much stronger individual hits and exact outgoing/returning path for lineup shots.

The old six-drone damage and energy potential are split across nine drones: each contact and each drone's budget are two-thirds of their previous values. Flat armour is scaled by the same factor for these contacts, preserving the full swarm's previous damage against armour instead of penalizing the larger number of smaller hits. Actual hit rate/TTK can differ because the formation now covers a wider area.

| Ship tier | Energy budget per drone | Base contact damage per drone |
| --- | ---: | ---: |
| Alpha | 13.33 | 2.8 |
| Beta | 36.67 | 6.4 |
| Gamma | 86.67 | 16 |

Native swept contact catches crossings between updates. Actual relative movement can still grant up to double swipe damage, with an energy-efficient bonus; fake velocity, matching target motion and idle drones do not qualify. Contact cadence is 0.5 seconds per drone before Fire Rate/Construction modifiers. Contact spends damage energy, not healable health. Spent drones need three seconds to rebuild and held fire to redeploy. Initial deployment, replacements, docking and reselection never create an automatic idle swarm.

Bright cores, curved fourteen-tick trails and muzzle flashes use the Engine colour. Enemies cannot target or damage the drones, allied repair turrets ignore them, and they do not consume the army cap. Deployment/contact/burnout sounds remain full-volume positional cues. Native acceleration preserves external forces. Ownership, palette and state remain separate per player.

The stable Eigen save chunk writes version four with nine slots and launch state. Versions one through three migrate their six slots, proportional energy and launch timing; the three new slots remain empty until fired. Snapshot packet version three carries nine slots. All peers should use v0.29.7. A native v160.7 listen-host/client test now covers saving, private loadout lists and Eigen movement; ordinary multiplayer wave balance and adverse network conditions still need playtesting.

### Superfactorial

Hold fire to accelerate from two to thirty shots per second **per gun** over three seconds. The ramp steepens toward full speed. Rounds deal 1 / 3 / 8 damage across Alpha / Beta / Gamma and fly up to 20 tiles, with random ±24° spread. Releasing fire, being disarmed, docking or replacing the mounts resets spin. Fire Rate and Construction affect cadence, but not the three-second ramp. Spin belongs to each mount and starts cold after respawn/load; it is not a saved loadout setting. Native simulation allows at most one round per gun per update, so extreme reload bonuses and low simulation rates can cap throughput. Shots use the Engine colour. Armour penetration increases with spin, up to 90% of armour ignored at full speed. Each round snapshots its own penetration at launch; its damage stays unchanged. Native unit shields and building damage handling remain in use.

### Caustic and Asymptote

Hold fire with Caustic to project three curling strands from each gun. The strands spread most at the midpoint and converge at the cursor. They deal low contact damage and stop at the first enemy unit or building on each path. Several strands hitting one target count once per gun for direct damage, and once per player–target pair for exposure. Track enemy units for **1.5 seconds** to apply native **Corroded for 3 seconds**. Continued contact refreshes the status; brief misses drain exposure gradually at 0.75 seconds of progress per second. Native status immunities are respected. Buildings receive contact damage and block strands, but cannot receive unit status effects.

The former focal damage multiplier is removed. Per-gun contact ticks still deal 0.9 / 2.7 / 8.4 damage across Alpha/Beta/Gamma, before armour/status effects. Base ticks are every 0.1 seconds; Fire Rate and Construction affect direct tick speed, while corrosion buildup follows elapsed contact time. Corroded uses the game's native periodic damage, shared unchanged with vanilla. The status persists through normal saves/snapshots; unfinished exposure is transient and clears on world load or source/target removal.

**Flux** rewards short-range sweeping passes and quickly applies Burning. **Caustic** has a narrower medium-range envelope and rewards sustained tracking before Corroded takes effect.

Asymptote starts at 0.25 world units/tick for 0.3 seconds, then ramps smoothly but sharply to 12 world units/tick over the next 0.2 seconds, without homing or inherited ship velocity. Its physical travel and displayed range are both limited to 30 tiles. Distance from its launch point increases damage from 20% to full strength over 25 tiles. Per-orb full direct damage is 2.5 / 8.5 / 25 across tiers, plus up to 16 / 54.4 / 160 splash damage within a four-tile radius (native splash falloff applies), with a 1.67-second base reload per gun. The orb grows and leaves a longer-looking luminous trail as it develops. Moving or changing the firing ship after launch does not change that shot's stored damage or palette. These are starting values for playtesting, not a finished balance pass.

### Utilities

Divergence sends an Engine-coloured expanding ring centered on the ship. Holding fire repeats the burst at its reload interval. It applies a bounded native impulse to enemy units, scaled by native mass and distance, without damaging or moving allies. Base reload is 2.5 seconds for every ship tier; Fire Rate and Construction still modify the interval.

Invariant projects a flattened C-shaped band around the front and sides of the ship, following aim. Its 200-degree profile is compressed by 20% along the aiming direction, retaining its width. Animated Engine-coloured beams connect the native gun muzzles to the shield’s inner edge while active. Its radius is 4 / 5 / 6 tiles, half the previous reach. It absorbs enemy absorbable projectiles touching or crossing the band; the rear opening and interior are unshielded, and allied shots pass through. Native instant/unabsorbable beams, units and damage already applied are not blocked. It does not repair allies or confer general invulnerability.

| Invariant | Alpha | Beta | Gamma | 2 |
| --- | --- | --- | --- |
| Shield capacity | 120 HP | 360 HP | 900 HP |
| Released recharge | 30 HP/s | 75 HP/s | 150 HP/s |
| Broken lockout | 10 s | 10 s | 10 s |

The shield cannot recharge while active. Breaking it forces the full lockout even if fire stays held. Its bar turns amber and fills during recovery. There is no persistent shield outline or projection beam while released or on cooldown; the brief break effect remains. A flash and sound mark readiness. Releasing an intact shield recharges it normally. Activation and release use native shieldWave sounds at higher/lower pitches, separate from shield breaking and the ready cue. Divergence retains shootArc at 60% pitch. All utility cues play at 100% event volume, subject to the game’s sound settings and positional attenuation. Utility audio uses native positional network calls so peers hear it too. Divergence also has a reload bar. These values are an initial utility pass and still need ordinary wave playtesting.

Per-unit utility state is authoritative and independent of loadout selection. Weapon swaps, Reset and Shipyard Apply do not reset shield health, lockout or pulse reload. Timers advance with game simulation, including docking, and stop when paused. World saves, occupied-Shipyard saves and multiplayer join state preserve them; respawning a new ship starts fresh. Native shield postprocessing affects the active barrier; recovery bars use a separate readable overlay layer.

Custom direct and splash damage now scale separately for Alpha/Beta/Gamma through per-weapon curves; the editor shows the current tier's damage. Standard vanilla weapons are unchanged. The first pass uses **420 native-engine TTK scenarios** before/after across ten ground/air enemies from T1–T5. Alpha loses excessive early burst; Gamma is more useful against higher-tier armor. Moving targets, groups, range and upgrades still matter. See [the balance report](docs/balance-ttk.md) for curves, timings, caveats and reproducible test instructions.

See the [support-role balance report](docs/balance-role.md) for the v0.10.1 limits, updated single-target results and 420 before/after six-enemy pack tests, including maximum Fire Rate. These tests measure ideal throughput; actual mixed waves and kiting need gameplay testing.

## Colours and UI

Choose **Hull**, **Accents** or **Engine**, then enter six HEX digits (`RRGGBB`, optionally prefixed with `#`) or use Hue/Saturation/Brightness. **Randomize** changes only the selected category; **Randomize all** beneath it changes Hull, Accents and Engine together. Both affect only colours. **Default colour** resets only the selected category. Colours are opaque, free, and remain a draft until Apply.

Hull paint preserves shading/outlines; Accents controls ship cells, the nose dot and gun markings without changing team indicators. Engine colours cover engines, trails, custom projectiles, charge/firing/hit effects and lights with bright cores. Existing shots retain their palette. Default Engine restores the selected weapon's default palette. The world ship and editor preview show custom colours; ordinary HUD/database icons remain vanilla.

The wide layout pairs Configuration over Statistics and Cosmetics over Preview, with optional rules on the right. Loadouts sits centered beneath the panels and above Apply/Reset/Balance rules/Close; the duplicate top tier/budget label is removed. Narrow windows use section tabs and scrolling. Apply/Reset/Balance rules/Close remain accessible. Slider labels retain knob clearance. There is no persistent footer hint.

## Saves, authority and compatibility

Profiles are keyed by server player UUID and tier in `modular-shipyard/loadouts-v1.properties` under Mindustry's data/config directory. Each server keeps its own profiles and private named-loadout catalogue; every client and server should use v0.29.7. Alpha, Beta and Gamma restore separately on respawn/reconnect, subject to their budgets. Erekir and unsupported ships are rejected.

After a core ship appears on a multiplayer client, a versioned, world-scoped request asks the authority to restore and resend that ship's persisted setup and private named catalogue. The server supplies the identity, checks the current core ship, applies its own validated profile and acknowledges the sync; the client cannot supply stats or another player's identity. Native unit/profile save formats remain unchanged. A colour-only handshake separately supplies local default colours, once per world/connection, without changing stats, weapons or named snapshots. Native Steam connections use an account ID, while IP connections use the game UUID; changing connection method can select a different native identity. Reconnect, full client restart and host restart tests pass with a Steam-format numeric identity over localhost; actual Steam matchmaking still needs a two-account test.


The server validates session ownership, controlled block, grid, environment, loadout limits, budget, cargo and target ship ID. Clients cannot choose a UUID or edit another player's stored ship. Exit requests carry a session token. Only the native possession interaction starts a session.

The player ships still use **three hidden hull types**. Eigen adds **one fixed hidden drone type** with three unregistered tier-stat views. There are eighteen fixed projectile/marker adapters; private player stat/gun/palette views and drone tier views share those fixed IDs. No vanilla UnitType, turret ammunition or weapon definition is globally changed. New enum values and content are appended to preserve previous tokens and IDs. Held ships use native unit payload serialization plus explicit loadout metadata; normal unit snapshots use the versioned repair state. Eigen adds its own versioned native save/join chunk and server snapshots. Its movement feed sends at most one small unreliable frame per swarm every three simulation ticks, with six ticks of client buffering and bounded extrapolation. This affects drawing only; native unit positions, damage, energy and spawning remain server controlled. Empty swarms send no movement frames, and enemy motion follows native fog visibility.

Older profiles/chunks and 3,000 legacy type-name aliases remain supported. Old profiles default missing fields/colours/weapons to their native values. Gamma configurations above 14 points do not restore. Utility saves require v0.12.0; occupied-Shipyard saves without utilities require v0.10.0; keep the mod installed when loading customized units. Custom maps use environment flags: the Shipyard rejects Erekir's scorching environment rather than requiring an exact planet identity.

## Build and verify

Requires JDK 17+ and the bundled Gradle wrapper. Compilation uses the pinned official v160.2 dependency jar as `compileOnly`; it is not bundled into the mod.

```sh
./gradlew jar
./gradlew smokeTest -PmindustryRuntimeJar=/absolute/path/server-release.jar
SHIPYARD_TTK=1 ./gradlew smokeTest -PmindustryRuntimeJar=/absolute/path/server-release.jar
```

Use the matching [v160.2 server runtime](https://github.com/Anuken/Mindustry/releases/download/v160.2/server-release.jar). Output: `build/libs/modular-shipyardDesktop.jar`. Tests run real content, native building/mining/firing/collision, budgets, migrations, per-player state, dock ownership/lifecycle, occupied saves, cargo/health preservation, and native save/join/snapshot codecs. Desktop and two-client checks exercise rendering and network workflows; details are in [implementation notes](docs/implementation-notes.md).

The retained Android `deploy` task requires an Android SDK and `d8`. Android/touch, prolonged play, other game versions and compatibility with other mods remain unverified. Java mods do not run on iOS.

## Source layout

- `model`: tier budgets, loadout/trade-offs, colours and weapon damage curves.
- `content`, `type`: stable hulls, private views, weapon/projectile templates and registration.
- `service`: authority, docking, profiles, snapshots and projectile effects.
- `world`, `ui`: powered possession, fabricator rendering and editor.
- `tests`: real-engine checks and the TTK benchmark, excluded from the jar.
- `assets-raw/shipyard.svg`: editable block sprite source.

Based on the official [Mindustry Java Mod Template](https://github.com/Anuken/MindustryJavaModTemplate), commit `32898e9270bf986337388353b5ba6fe2f9c1b95b`. Retains its wrapper and optional Android packaging.

Operator has 100 energy, recharges 30 per second while idle, and drains 18–72 per second while holding, based on target mass. Heavy units resist the tether. Emptying the meter releases the target; release fire and recover to 25 energy before grabbing again. Enemies retain their movement and weapons. The host selects the target and owns the energy reserve; docking or swapping weapons does not refill it. Saving preserves energy but safely releases a held enemy.

The focused Flux/Caustic and Converge/Asymptote role comparison is documented in [weapon pair tests](docs/weapon-pair-review.md), with reproducible native-engine results.

Caustic’s [moving combat review](docs/caustic-moving-review.md) covers actual HP, dodging routes and native enemy return fire. The 50% armour reduction helps medium armour but still leaves heavy enemies above the beam’s damage threshold.

## Weapon effects

Vector aligns paired rails during its charge, flashes a precise shot, then leaves a segmented afterimage; full-strength distant connections add a brief cross-shaped impact. Asymptote has counter-rotating arcs that stretch into a wake during acceleration, followed by two staggered blast ripples. Converge carries three small core lights, unfolds into a three-pronged split, and gives its bomblets curved ribbons and compact detonation rings. All use the firing loadout’s engine colour.

Caustic’s firing cue and continuous loops use 80% event volume. Bomblet detonations use the native small-plasma explosion cue at 100%, with positional attenuation and game sound settings still applying. These effects and audio changes do not change damage, ranges, cooldowns or point costs.

See [the cleanup audit](docs/cleanup-audit.md) for removed code, retained compatibility paths and validation. Routine builds enable Java compiler lint.

Asymptote uses the native `explosionPlasmaSmall` cue at full event volume for actual impact/timeout detonations. Absorbed orbs stay quiet.
