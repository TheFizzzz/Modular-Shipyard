# Cosmetic families (Modular Shipyard 0.30.0+)

The base mod owns the single **Ship cosmetics** menu. An addon implements `shipyard.api.CosmeticFamily` and calls `CosmeticsDialog.registerFamily(...)` from `Mod.init`, before `ClientLoadEvent`. Family IDs must be unique. Registration adds a page and the switch beneath the preview; the base mod remains usable without any addons.

A family supplies its name, hint, supported native ships, saved palette, save callback and private preview type. The shared editor uses `shipyard.model.ShipCosmetics` and provides HEX/HSV editing, randomizers and previews. A preview must preserve vanilla content and must not register types while drawing.

The editor keeps an independent draft and preview selection for every installed family. Switching retains valid or incomplete HEX input. **Save defaults** validates all pages and saves the edited families; **Close** discards drafts. Settings, gameplay application, packets, and future weapon/stat loadouts remain the responsibility of each family.

Declare `modular-shipyard` in the addon's `mod.hjson` dependencies. Compile against the base JAR using `compileOnly`; do not bundle its classes into the addon. Use the native Mindustry classloader to share the API and model types. The Erekir addon is a working example.


## Shared docks (0.31.0+)

Extend `shipyard.world.Shipyard` to reuse possession, power checks, construction visuals, serialization and safe release. Override the supported-ship predicate, editor entry and family loadout/colour restoration hooks. Set `fabricationTint` per block; the Serpulo tint stays independent.

A dock payload retains the existing native wire shape: unit ID, family loadout string, colour string, native UnitPayload. On reading, the decoded type selects a registered Shipyard block that supports that family. Never parse an addon loadout with the Serpulo model. Source pixel palette variants belong in `createIcons(MultiPacker)`, before Mindustry disposes source pixmaps.

Use `ShipyardSessions.suspend` when temporarily removing a payload carrier: native PayloadUnit.remove clears and removes its carried payloads. Suspension detaches that sequence during removal, then restores it.
