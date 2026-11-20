# CosmicPvP enchant port (fork)

This branch (`sakura-custom`) adds a CosmicPvP-style enchant set on top of the
upstream CrazyEnchantments registry. Nothing upstream was removed or renamed;
every addition is additive and gated behind its own config block or provider.

Balance decisions, per-enchant numbers, provenance (DOC vs FORK) and the
cancelled Heroic/Soul/Mastery scope cut are recorded in
`plans/cosmicpvp-enchants.plan.md` (local, gitignored).

## Added enchants

- **Axe / any weapon**: Bleed, Deep Wounds, Devour, Pummel, Cleave, Arrow Break,
  Blacksmith, Anti Gank, Barbarian, Insanity, Rogue
- **Sword**: Double Strike, Inversion, Block, Assassin, Enrage, Disintegrate,
  Greatsword, Thundering Blow, Kill Aura
- **Bow**: Sniper, Unfocus, Arrow Lifesteal, Pacify, Longbow, Eagle Eye, Infernal,
  Explosive, Hellfire, Virus, Teleportation, Farcast
- **Tools**: Reforged, Fuse, Obsidian Destroyer, Auto Sell
- **Armor**: Armored, Heavy, Tank, Marksman, Deathbringer, Sticky, Aegis, Clarity,
  Arrow Deflect, Hardened, Ragdoll, Ender Walker, Creeper Armor, Metaphysical,
  Angelic, Spirits, Spirit Link, Blood Lust, Lifebloom, Curse, Destruction,
  Plague Carrier, Feign Death
- **mcMMO**: Skilling (gathering XP), Training (combat XP)

Each has an entry in `config.yml` under `CosmicEnchants` for its tunables, and a
`Chance-System` in `Enchantments.yml` unless it is always-on. Chance rolls follow
the upstream formula `base + increase * (level - 1)`.

## New enchantment types

`Enchantment-Types.yml` gained `Chestplate` (menu slot 12), `Leggings` (13),
`Weapons-and-Tools` (11) and `Weapons` (10) so enchants can be restricted to the
gear that can actually trigger them.

## Book levels

`CrazyManager.randomLevel` now rolls each enchant's own `1..MaxPower` range
instead of the category `LvlRange`. Category tiers still control drop rate,
price and presentation only. `LvlRange` is legacy and inert.

## Optional providers

| Provider | Use | Absent behaviour |
| --- | --- | --- |
| mcMMO | Skilling / Training XP bonus | enchants inert |
| WildStacker | Kill Aura | enchant inert |
| EconomyShopGUI(-Premium) | Auto Sell pricing | enchant inert |
| FactionsUUID / SaberFactions | territory and ally checks | friendly checks fall back |

All provider imports sit in one gated class each; the plugin still loads and
runs without them. `plugin.yml` lists them as `softdepend`.

## Known limitations

- Only Auto Sell's single-currency Vault price path is supported; multi-currency
  and non-Vault prices leave drops untouched, and sales bypass EcoShopGUI's
  transaction events.
- Territory "entry blocked" cannot be expressed by the current support layer, so
  Teleportation and Farcast skip that check.
- Curse never removes potion effects (ownership cannot be proven), so its buff
  can outlive the trigger by up to 2 seconds.
- Feign Death hides the wearer per viewer; another plugin's own hide of the same
  player is not reference-counted.
- No enchant in this port has been exercised on a live server yet.

## Upgrading an existing server

`config.yml`, `Enchantments.yml` and `Enchantment-Types.yml` are not merged
automatically. Back them up, copy the new `CosmicEnchants` block and the new
enchant/type entries into the live files, then run
`/crazyenchantments reload`.

To adopt the one-row enchanter on an existing server, merge the shop layout
and category settings from the shipped `config.yml` into your live config.
On servers already using `Rarity-Format: 2` in `Enchantments.yml`, keep the
`Simple` category key; its shop label is **Common**. On older servers, copy
the GUI settings and prices into the matching existing category sections
without renaming their keys. To switch to the new keys, migrate the
`Enchantments.yml` rarity and category entries and the `config.yml`
category keys together, then enable `Rarity-Format: 2` before reloading.

To enable the new Tinkerer book rewards on an existing server, copy
`Settings.Tier-Potency-Cap` from the shipped `Tinker.yml` into your live
`Tinker.yml`. Without it, the old per-book potency formula still applies.
