# Shipyard sprite sources

The approved design combines Precision Rig C with Reconstruction Dock B's paired pink side panels. These standalone SVGs are the editable source of the three 96×96 runtime layers:

- `shipyard.svg`: powered idle artwork, also used for icons.
- `shipyard-off.svg`: full unpowered/disabled artwork with gray panels.
- `shipyard-lights.svg`: transparent bright tips/panels pulsed while occupied.

Keep square outer corners, the overlapping upright emitters, the recessed square pad and the muted-pink accent. Serpulo machinery colours are `#4a4b53`, `#6e7080`, `#989aa4` and `#b0bac0`; pink is `#c7a4af`, with `#927080` shadows and `#e5cbd4` occupied highlights. Coordinates use an integer 96-pixel grid with crisp edges.

Regenerate runtime assets from the project root using librsvg:

```sh
rsvg-convert assets-raw/shipyard.svg -o assets/sprites/shipyard.png
rsvg-convert assets-raw/shipyard-off.svg -o assets/sprites/shipyard-off.png
rsvg-convert assets-raw/shipyard-lights.svg -o assets/sprites/shipyard-lights.png
```

The PNGs are bundled by Gradle. SVGs are development sources only; neither librsvg nor Python is needed to run the mod. Occupied ships and the pink scan are rendered by the game and are not baked into these images.
