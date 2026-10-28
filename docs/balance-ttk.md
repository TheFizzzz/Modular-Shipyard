# First tier-based weapon balance pass

v0.10.0 gives each custom weapon its own Alpha/Beta/Gamma damage curve. Direct and splash damage scale together. Range, charge time, reload, homing and lightning jump behaviour are unchanged. Native Standard weapons retain their existing tier behaviour. These are provisional gameplay values, not a PvP balance guarantee.

## Method

The real Mindustry v160.2 engine runs 210 scenarios before and 210 after: seven weapon choices, three player ship tiers, and ten stationary enemies (Dagger/Mace/Fortress/Scepter/Reign and Flare/Horizon/Zenith/Antumbra/Eclipse). Ships aim at one target five tiles away. Sliders are neutral; rules use default damage multipliers. Native gun mounts, fire patterns, charge queues, projectile movement, homing, collisions, armor and status ticks run at a fixed 60 Hz. The clock starts with reload ready and no pre-charge. A result above 120 seconds means the target survived the test limit.

This favours full shotgun hits and short-range flame contact. Moving enemies, range, armor/status immunities, groups, defenses and upgraded sliders can change results. Single-target TTK omits lightning’s main benefit: extra jumps through a group. Mace is immune to Burning, which particularly affects low-damage Alpha flames. Weapon switching starts a cooldown in actual play; that extra post-switch wait is excluded here.

Reproduce with `SHIPYARD_TTK=1 ./gradlew smokeTest -PmindustryRuntimeJar=/absolute/path/server-release.jar`. The benchmark writes `build/smoke-test/config/ttk.csv`. Raw measurements: [before](ttk-before.csv), [after](ttk-after.csv).

## Damage factors

| Weapon | Alpha | Beta | Gamma |
| --- | ---: | ---: | ---: |
| Flamethrower | 0.25 | 0.85 | 2.5 |
| Lancer / Railgun | 0.25 | 0.65 | 2.0 |
| Fuse | 0.18 | 0.65 | 1.6 |
| Chain lightning / Missiles | 0.5 | 1.1 | 3.0 |

Factors multiply the v0.9 projectile values. They do not consume extra customization points. The editor shows the resulting direct damage for the current ship tier.

## Ground target results

### Alpha

| Weapon | Dagger T1 | Mace T2 | Fortress T3 | Scepter T4 | Reign T5 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Standard | 4.02 | 22.97 | >120 | >120 | >120 |
| Flamethrower | 1.67 | 64.87 | 48.75 | >120 | >120 |
| Lancer | 3.35 | 19.35 | >120 | >120 | >120 |
| Fuse | 1.77 | 9.93 | 45.52 | >120 | >120 |
| Railgun | 3.18 | 9.68 | 18.35 | >120 | >120 |
| Chain lightning | 2.68 | 12.68 | 32.68 | >120 | >120 |
| Homing missiles | 2.45 | 13.70 | 43.70 | >120 | >120 |

### Beta

| Weapon | Dagger T1 | Mace T2 | Fortress T3 | Scepter T4 | Reign T5 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Standard | 2.32 | 13.50 | 76.78 | >120 | >120 |
| Flamethrower | 0.67 | 2.77 | 7.65 | >120 | >120 |
| Lancer | 0.68 | 4.68 | 11.35 | >120 | >120 |
| Fuse | 0.02 | 1.77 | 3.52 | 67.68 | >120 |
| Railgun | 1.02 | 3.18 | 7.52 | 89.85 | >120 |
| Chain lightning | 0.68 | 4.68 | 9.35 | >120 | >120 |
| Homing missiles | 0.95 | 4.70 | 10.68 | >120 | >120 |

### Gamma

| Weapon | Dagger T1 | Mace T2 | Fortress T3 | Scepter T4 | Reign T5 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Standard | 1.77 | 10.23 | 58.07 | >120 | >120 |
| Flamethrower | 0.27 | 0.85 | 1.45 | 20.73 | 92.30 |
| Lancer | 0.68 | 2.02 | 2.02 | 32.68 | 99.35 |
| Fuse | 0.02 | 0.60 | 1.18 | 15.77 | 46.10 |
| Railgun | 1.02 | 1.02 | 1.02 | 22.68 | 57.35 |
| Chain lightning | 0.02 | 1.35 | 2.68 | 37.35 | 102.02 |
| Homing missiles | 0.20 | 1.70 | 2.43 | 42.15 | >120 |

All times are seconds. For example, Alpha Fuse against Dagger changes from 0.017 s to 1.767 s; Gamma Railgun against Scepter changes from 50.850 s to 22.683 s. Gamma is substantially more useful beyond T2, while T5 enemies remain meaningful threats. Further tuning should include moving-target and group fights rather than forcing equal single-target TTK across all weapon roles.
