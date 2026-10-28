# Weapon pair comparison — v0.20.2

The pairs have distinct roles already. The clearest next balance issue is Caustic's performance against heavy armour, rather than duplicate weapon mechanics. Converge and Asymptote separate reasonably as pursuit versus predictive area damage. No damage, point cost, status or reload changes were made in this pass; Scalar and Superfactorial are untouched.

Converge's requested timing change is included: main flight **55 → 40 ticks** (0.92 → 0.67 seconds, 14-tile straight flight), bomblets **40 → 60 ticks** (0.67 → 1 second, up to 25.5 additional tiles before homing turns). All other Converge properties stay unchanged. These distances describe separate stages, not a guaranteed effective range.

## Flux / Caustic

| Native test | Flux | Caustic | Implication |
| --- | --- | --- | --- |
| Alpha, one Dagger, 5 tiles | Kill in 1.67s | Kill in 2.75s | Flux rewards immediate close contact. |
| Alpha, six tightly grouped Daggers, 5 tiles | Clear in 5.13s | Clear in 16.72s | Flux has a clear close-crowd advantage. |
| Alpha, one Dagger, 30 ticks on / 30 off | Kill in 2.57s | Kill in 9.50s | Interruptions punish Caustic's buildup. |
| Alpha, one Mace, 5 tiles | 169 HP lost in 20s; survives | Kill in 10s | Mace is immune to Burning; Caustic offers a different status matchup. |
| Beta, one Fortress, 5 tiles | Kill in 7.65s | Kill in 19.75s | Caustic pays a substantial price for reach and buildup. |
| Beta, one Fortress, 12 tiles | Cannot reach | Kill in 19.75s | Caustic's longer reach is a real advantage. |
| Gamma, one Reign, 5 tiles, 20 seconds | 5,201 HP lost | 484 HP lost | Caustic struggles badly against heavy armour. |

**Recommended next discussion:** preserve Flux as the immediate short-range sweeper and improve Caustic's sustained anti-armour/support payoff. Increasing Caustic's ordinary damage alone risks making it simply a longer-range Flux. Native Corroded is periodic ordinary damage; it does **not** reduce armour. Both the beam's small hits and the status ticks are reduced by armour. Scepter's native shield-regeneration field makes the weakness particularly severe: the Gamma/Caustic focus case removes only about 17 HP in 20 seconds despite maintaining Corroded. The CSV reports HP lost, so damage absorbed by shields is not counted as health damage.

## Converge / Asymptote

| Native test | Converge | Asymptote | Implication |
| --- | --- | --- | --- |
| Alpha, one moving Flare, 18 tiles | Kill in 3.08s | Survives 20s; 39 HP lost | Homing has a strong mobile-target niche. |
| Alpha, six tight Daggers, 18 tiles | 3 kills in 20s | All six in 17.30s | Asymptote's larger blast rewards density. |
| Beta, moving Zenith, 18 tiles, current-position aim | 585 HP lost in 20s | 107 HP lost | Converge forgives tracking error. |
| Same, aim 30 ticks ahead | 593 HP lost | 159 HP lost | A simple lead helps Asymptote, but is not perfect prediction. |
| Beta, one Fortress, 14 tiles | Kill in 5.87s | 491 HP lost in 20s | Direct main-missile connections are much stronger than fallback bomblets. |
| Gamma, one Reign, 26 tiles, 20 seconds | 374 HP lost | 1,872 HP lost | Charged Asymptote has a stronger distant-armoured matchup. |

The lifetime change makes bomblets take over earlier; it is **not** a neutral power transfer. For example, Beta against the moving Fortress at 18 tiles changes from a 6.07-second kill with the previous main flight to only 99 HP lost in 20 seconds using the shorter main flight. Bomblets have much lower per-hit damage and are consequently much more affected by armour. The prior profile is included alongside the new one in both CSVs. A sensible next Converge adjustment would address bomblet usefulness against armour without enlarging their splash into Asymptote's area-damage role. That change has not been implemented here.

Asymptote also struggles against large enemies in some pack layouts. Hull interception, distance charge and blast placement warrant a separate geometry check before choosing a buff. These pack results alone do not establish the cause.

## Method and limits

270 native-engine cases per runtime; **v160.2 and v160.7 produce identical CSV rows**. Each run includes the normal regression suite. Player tiers face relevant ground and flying enemies: Alpha versus Dagger/Mace/Flare; Beta versus Fortress/Zenith; Gamma versus Scepter/Reign/Antumbra/Eclipse. Four weapons use default stat allocations and their actual point costs. Cases stop at a kill/pack clear or 20 seconds.

Beam scenarios cover single enemies at 5 and 12 tiles, interrupted fire, lateral motion, tightly grouped enemies and rapid target switching across spread enemies. Blast scenarios cover single enemies at 14 and 26 tiles, lateral motion at 18 tiles with current-position or fixed half-second lead aiming, and tight/spread packs at 18 tiles. The previous Converge lifetime profile is also tested for every blast scenario.

The simulation uses native 60-Hz weapon updates, projectile travel/homing, collision, armour, status damage, corrosion buildup and unit abilities. Six-enemy spacing is at least the native hull size plus three world units to avoid overlapping giant units. Lateral paths use the enemy type's native speed. Nominal pack distances describe the front-row centreline; spread or deep pack members can be outside a weapon’s reach. The player never repositions to compensate. The player remains stationary, focuses the first live target or deliberately switches every half-second, and does not use native auto-aim. No stat boosts, enemy retaliation, obstacles, friendly defenses or enemy firing are simulated. This is a role/exposure comparison, **not a campaign-wave or survival test**. Beyond-range scenarios intentionally show the range advantage and are not equal-range DPS comparisons. Do not read the capped 20-second entries as exact TTKs or damage totals as damage per second.

Reproduce:

```sh
SHIPYARD_PAIRS=1 ./gradlew smokeTest -PmindustryRuntimeJar=/absolute/path/mindustry-runtime.jar
```

Raw data: [v160.7](weapon-pairs-1607.csv), [v160.2](weapon-pairs-1602.csv). Internal tokens: flamethrower = Flux, missiles = Converge.
