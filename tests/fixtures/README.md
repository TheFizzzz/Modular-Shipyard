# Legacy save fixture

`shipyard-v0.3.1.msav` was generated with the previously built v0.3.1 mod jar and official Mindustry v160.2 server runtime. It contains a vanilla stone map/core and one Alpha using the old registered type `modular-shipyard-ship-alpha-s4-c5-b5`: Speed 0, Cargo +1, Construction +1. No player identifiers or real gameplay data are included.

The integration suite loads the old content header, migrates this ship to a private stat view on the stable Alpha hull, verifies capacity/construction and the new mining scaling, then saves and loads it again using the new custom chunk.

`shipyard-v0.4.0.msav` was generated with v0.4.0 and the same official v160.2 runtime. It has one Alpha with Cargo +1 / Construction +1 stored in the version-1 per-unit custom chunk, at 75/150 HP. The suite verifies migration to the expanded version-2 chunk preserves damage and defaults Durability/Fire Rate to zero, then verifies resaving. It contains no player identifiers or real gameplay data.

`shipyard-v0.5.0.msav` was generated with v0.5.0 and the official v160.2 runtime. It contains one Alpha with Durability +1 / Fire Rate +1 and 86.25/172.5 HP, stored in payload version 2. The colour suite checks that payload version 3 defaults all colour channels to native behaviour while preserving stats and damage, and verifies resaving. It contains no player identifiers or real gameplay data.
