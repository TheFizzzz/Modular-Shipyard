# v0.10.1 support-role balance

The intended matchup ceilings are Alpha at T1–2, Beta at T3, and Gamma at T4–5. A player ship should assist defenses and handle individual enemies rather than replace an army throughout a run. These are soft gameplay ceilings: ships can still contribute damage above them.

## Changes

- Alpha railgun damage factor drops from 0.25 to 0.15 (36 starting damage per gun). Beta and Gamma railgun damage stay unchanged.
- Flamethrower projectiles, Lancer beams and railgun shots hit at most two targets each.
- Each Fuse ray hits one target; its two rays per gun/four per ship are retained.
- Chain lightning keeps its existing three-jump limit and damage falloff; missiles keep their small blast. Neither receives unlimited line piercing.
- Other tier damage factors, points, charge/reload/range and cosmetics are unchanged.

## Method and limitations

210 single-target scenarios refresh the earlier [tier balance timings](balance-ttk.md). Another 420 pack scenarios compare before/after across three player tiers, seven weapons, five ground enemy tiers and two loadouts: neutral or +5 Fire Rate. Alpha funds that upgrade with −4 Mining/Cargo, keeping every fixture within its budget.

Each pack contains six stationary enemies in an artificially dense line, 18 world units apart. The shooter instantly repositions to five tiles from the next living enemy and aims perfectly. Dead targets are removed and native enemy trees are rebuilt between ticks. There is no enemy retaliation, travel delay, cover or friendly defense. Native firing/charge queues, movement of bullets, collision, armor, status effects, splash and chain jumps run at 60 Hz. Results cap at 120 seconds. This is an optimistic throughput test, not a simulation or proof of full-wave survival. Single-target timings do not account for group benefits.

Some ground enemies cannot shoot flying players; damage tuning cannot prohibit all kiting strategies. Core pressure, mixed enemy waves, mobility, return fire and map layout still determine whether a solo defense succeeds. Moving-target and actual campaign-wave tests remain useful future work.

Reproduce with `SHIPYARD_TTK=1 SHIPYARD_PACK_TTK=1 ./gradlew smokeTest -PmindustryRuntimeJar=/absolute/path/server-release.jar`. To reproduce the old pack damage/caps within the test harness, add `SHIPYARD_PACK_UNBOUNDED=1` and omit `SHIPYARD_TTK`; this modifies only private benchmark weapons and is not a game option.

Raw data: [pack before](pack-ttk-before.csv), [pack after](pack-ttk-after.csv), [single-target after](ttk-role-after.csv).

## Six-enemy clear times at maximum Fire Rate

All values below are seconds. `>120` means the pack survived the test limit.

### Alpha versus six Maces

| Weapon | Before | After |
| --- | ---: | ---: |
| Standard | 98.8 | 98.8 |
| Flamethrower | >120 | >120 |
| Lancer | 13.3 | 40.3 |
| Fuse | 29.6 | 42.8 |
| Railgun | 32.9 | 64.8 |
| Chain lightning | 26.6 | 26.6 |
| Homing missiles | 56.7 | 56.7 |

### Beta versus six Fortresss

| Weapon | Before | After |
| --- | ---: | ---: |
| Standard | >120 | >120 |
| Flamethrower | 16.1 | 16.1 |
| Lancer | 7.9 | 24.1 |
| Fuse | 7.6 | 16.4 |
| Railgun | 17.0 | 19.9 |
| Chain lightning | 19.8 | 19.8 |
| Homing missiles | 44.7 | 44.7 |

### Gamma versus six Scepters

| Weapon | Before | After |
| --- | ---: | ---: |
| Standard | >120 | >120 |
| Flamethrower | 46.5 | 46.5 |
| Lancer | 29.5 | 78.1 |
| Fuse | 24.0 | 71.2 |
| Railgun | 89.5 | 89.5 |
| Chain lightning | 106.7 | 106.7 |
| Homing missiles | >120 | >120 |

### Gamma versus six Reigns

| Weapon | Before | After |
| --- | ---: | ---: |
| Standard | >120 | >120 |
| Flamethrower | >120 | >120 |
| Lancer | 67.3 | >120 |
| Fuse | 63.6 | >120 |
| Railgun | >120 | >120 |
| Chain lightning | >120 | >120 |
| Homing missiles | >120 | >120 |

