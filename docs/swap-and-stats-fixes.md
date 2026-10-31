# Involution swaps and Erekir stat previews

Base 0.32.29; Erekir addon 0.4.16.

## Involution

Weapon rebinding copied alternating side flags from unrelated guns. With incompatible flags, both native blade mounts waited for the other side indefinitely. Entering BladeGun from another gun now resets the side flag to its native initial value. Rebinding an existing BladeGun retains its firing phase and reload. The authoritative weapon-switch cooldown remains unchanged.

A regression test fails on the prior implementation after switching from standard Alpha guns with phase 1. The fixed implementation passes 60 cases across all three tiers, five source weapons with different mount counts, and four side states. Cosmetic reinstallation preserves phase and reload. Existing native alternating cadence, projectile flight, persistence and full smoke checks pass.

## Erekir statistics

Arc Element.update replaces rather than appends the existing updater. Attaching colour updates to provider-backed labels displaced their text updater, freezing both comparison values and the budget display. Text and colour now update together. Initial text remains populated for preferred-size calculations.

Determinant's field damage calculation is shared between gameplay and its new damage-per-second preview. Its equipment card shows the current draft anchor count and field damage, including potency and Construction penalties. Existing gameplay numbers are unchanged. Stat rows are slightly tighter to keep the added damage row visible.

Both smoke suites pass. The native 160.7/NVIDIA UI fixture verifies potency +3/+5 anchor thresholds and damage in the statistics and card, refreshed available points, and Construction's potency penalty. Final screenshot inspected with all stat rows visible. No live multiplayer session was run for this pass.
