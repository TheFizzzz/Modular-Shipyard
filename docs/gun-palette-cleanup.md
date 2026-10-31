# Custom gun palette cleanup — base 0.32.28 / addon 0.4.15

Generated vanilla-derived gun textures contained interpolated RGB values and partial alpha already present in their source artwork. Resizing was nearest-neighbour; the intermediate pixels were inherited rather than created by resizing.

`SpritePalette.snap` runs during atlas packing. It snaps RGB to an explicit local palette, clears alpha below 128, and makes retained pixels fully opaque. All nine custom Serpulo designs use their native grey/gold shades; Converge retains its darker muzzle lip. Eigen retains a separate three-tone neutral engine-colour insert. The Erekir gun uses its own accepted casing shades and separate muzzle palette; its white emitter mask was already crisp. Runtime projectile trails, shields, glows, hull art and shared vanilla textures are unchanged.

Both complete smoke suites pass. Native Mindustry 160.7/NVIDIA previews exercise all 27 Serpulo design/tier mounts and render the shared Erekir gun on Evoke, Incite and Emanate. Exported-pixel checks pass for all 60 gun/heat/colour-mask textures: approved RGB palettes and binary alpha. Idle screenshots were inspected. No multiplayer session was run for this sprite-only pass.

Install/update both mods together: addon 0.4.15 uses the palette helper provided by base 0.32.28. Earlier exploratory Erekir previews allowed unpossessed core-spawned NPCs to disappear; the final fixture uses ordinary NPC spawn flags, an explicit unit cap and stationary controllers, and verifies all three ships remain present.
