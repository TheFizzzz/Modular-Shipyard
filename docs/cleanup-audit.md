# Pre-multiplayer cleanup — v0.27.1

Weapon numbers, costs and visuals, content registration order, save schemas and packet tokens are unchanged.

## Error boundaries

- Removed all five production `catch(Exception)` blocks and thirteen empty `ignored` catches.
- Apply, delete, docking and unit-view changes now run outside their packet parsing recovery blocks. Invalid wire fields are rejected; unexpected internal failures remain visible.
- Profile read/save/delete handle specific I/O failures and preserve transactional rollback. Corrupt saved fields produce contextual warnings without dumping entire packets or player identifiers.
- HEX typing uses non-throwing validation; incomplete input preserves the last valid draft and blocks Apply.
- Two small native I/O adapters remain: Arc Reads wraps IOException inside RuntimeException, and Arc file replacement wraps I/O in ArcRuntimeException. Each adapter checks the cause, handles only verified I/O failures, and rethrows unrelated failures. Portable java.io and native Arc file replacement avoid introducing a newer Android API requirement.
- Clone failures and inaccessible native fields still fail immediately with clear exceptions; these are compatibility checks, not silent fallback.

## Names and dead code

Renamed ambiguous loadout values, painted views, effect parameters, slider steps and utility/Eigen state. Native API members such as Bullet.data and IntMap.Entry.value retain their required names. Geometry coordinates and loop indices stay compact where clear.

Removed two unused request overloads, Eigen's unused charge/attacking helpers, an unused colour constant, the retired laser's unused palette method/reference, and ten obsolete bundle entries. Three duplicate native-field reflection implementations now share NativeFields.copy. Deprecated codec factories, a redundant cast and an unchecked test cast were removed. Routine builds now enable -Xlint:all.

Kept native callback overrides, reflection hooks exercised by integration tests, old profile codecs, the retired laser adapter, the Fuse token/projectile and legacy hull aliases. Native saves and in-flight content IDs still depend on these; they are not dead code.

## Validation

The full native suites pass on v160.2 and the installed v160.7 core (with headless backend support for the fixture). Main and test compilation report zero compiler warnings. New error-boundary tests cover malformed Apply/HEX/docking input, truncated native I/O, visible entity-map compatibility failures, failed-delete rollback and propagation of an injected internal Apply failure.

A hidden native v160.7 desktop check passed possession, painted ship preview, live HEX validation and the weapon picker. Captures were visually inspected and are retained under build/tooling/desktop-qa. Weapon/utility, colour, budget, profile, occupied-save, respawn, ownership and content-ID regression checks also pass.

Live multi-client gameplay remains a separate outstanding check. Save/packet fixtures do not substitute for a real network session.

A source backup is retained under build/cleanup-backup. Build tools and logs live under the ignored build directory so a cleared temporary directory does not erase unfinished work.
