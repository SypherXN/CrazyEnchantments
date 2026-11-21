# CosmicPvP enchant port (fork)

This branch (`sakura-custom`) adds a CosmicPvP-style enchant set on top of the
upstream CrazyEnchantments registry. No upstream enchant was removed. One
upstream identity was renamed: the headshot enchant owns the name **Sniper**
today and upstream's Sniper (poison) enchant is now **Poison Shot** — see
[Sniper and Poison Shot](#sniper-and-poison-shot).

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

**Sniper** in the lists above is the headshot enchant. Upstream's Sniper (the
poison shot) is **Poison Shot**; its block, tuning and stored item data follow
that swap, and a live `Enchantments.yml` written before it is still read
correctly (see [Sniper and Poison Shot](#sniper-and-poison-shot)).

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

Tinkered books grant Mystery Dust that rolls from 0% to the cap for the book's
rarity. To adopt the new default caps (1/2/5/10/50), copy
`Settings.Tier-Potency-Cap` from the shipped `Tinker.yml` into your live file.
Existing caps from 0 to 50 remain in effect. Missing or invalid caps
(including values above 50) scale by the tier's XP-point shop price. If there
is no eligible price, the legacy per-book formula applies, capped at 50%.

### Sniper and Poison Shot

This is the one upgrade the plugin reads for you. In a live `Enchantments.yml`
written before the rename, `Sniper:` is the poison block and `SniperHeadshot:`
the headshot block. Both are read under today's keys (`Poison Shot:` and
`Sniper:`) in memory, each keeping its own block's settings, and the file on
disk is never written. One warning names the two keys to rename when convenient.

A file that cannot be resolved is not guessed at:

- only a `Sniper:` block, with neither `SniperHeadshot:` nor `Poison Shot:` —
  it could be the old poison block or the new headshot block, so neither enchant
  is loaded (stored item data is untouched) and the warning says which block to
  add or restore.
- all three keys — the stray `Sniper:` block could be either reading, so it is
  ignored; `Poison Shot:` is read as poison and `SniperHeadshot:` as the
  headshot, and the warning says what to delete and rename.

Names in free-form settings are **not** migrated automatically, because the same
word can be a label or a kit entry. Update these by hand where they meant the
poison enchant:

- `GKitz.yml` item strings: a bare `Sniper:<level>` entry (for example
  `..., Overload:0-5, Sniper:2, ...`) now grants the headshot enchant. Write
  `PoisonShot:<level>` (or `Poison Shot:<level>`) for the poison one.
- `Tinker.yml` `Tinker.Crazy-Enchantments.<name>` keys: an entry named `Sniper`
  now prices the headshot enchant; rename it to `Poison Shot` if it was the
  poison's. The shipped file has no entry for either.
- Permissions: `crazyenchantments.Sniper.deny` now denies the headshot; the
  poison enchant's node is `crazyenchantments.Poison Shot.deny` (with the space).
- Commands: `/crazyenchantments add Sniper` adds the headshot, `PoisonShot`
  adds the poison.
- `config.yml` `CosmicEnchants.Sniper.*` is unchanged and still tunes the
  headshot (`Enchantments.Sniper.Tuning`).

`/crazyenchantments fix` skips both enchants while the file uses legacy or
ambiguous keys. It can still repair unrelated enchants.
