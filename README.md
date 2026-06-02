# Random-Reforges
Minecraft mod for Forge 1.20.1 - Applies random reforges to your gear - works with all modded items and attributes.


## In-game help MD
If you prefer MD instead of the ingame GUI. :)

### Reforge ID

An unique internal identifier for the reforge.  
Only lowercase letters, numbers and underscores are allowed.

Example: `armor_corrupted`, `weapon_heavy`

Used by the `/reforge apply <id>` command to force-apply a specific reforge.

### Display Name

The name shown in-game on the item and in the reforge list.

Example: `Corrupted`, `Legendary`

### Weight (Chance)

Controls how often this reforge is rolled compared to others.

- `0` → never appears
- `100 000` → medium (appears at roughly average frequency)
- `1 000 000` → very common
- `1` → extremely rare

The chance is relative: if all reforges have weight `100 000`,
each has an equal probability.

### Reforge List

The Reforge List shows all loaded reforges.

Each entry displays:

- Display Name (top left)
- Reforge ID (top right)
- Group(s) (bottom left)
- Chance (bottom right)

The chance shows how likely this reforge is to be
rolled compared to all others in the same group.

Hover over the green chance percentage to see
the exact chance for every group this reforge
can appear in.

### Comment

Optional text shown below the item name in the tooltip.

Leave empty for no comment.

### Applies To

Defines which items can receive this reforge.

- WEAPON
- ARMOR
- TOOL
- BOOTS
- HELMETS
- CHESTPLATES
- LEGGINGS
- SHIELD
- SPELLBOOKS
- CURIO
- ANY

Custom input field — comma-separated entries:

- `mod_id:item_id` → Single specific item
- `#mod_id:item_tag` → All items in a tag
- `!mod_id:item_id` → Blacklist a specific item
- `!#mod_id:item_tag` → Blacklist all items in a tag

Note: "Applies To" does not restrict the `/reforge apply` command.

### Attributes

Stat modifiers granted by the reforge when the item is equipped.

Each attribute entry has three fields:

#### Attribute ID

The registry name of the attribute

Examples:

- `minecraft:generic.attack_damage`
- `irons_spellbooks:max_mana`

The attribute probably does not exist if it has
not been translated correctly in the tooltip.

#### Value

The numeric modifier amount (can be negative)

#### Operator

How the value is applied:

##### ADD

Adds a flat value to the base stat.

##### MULTIPLY_BASE

Multiplies the base stat (before other bonuses).

##### MULTIPLY_TOTAL

Multiplies the final stat (after all bonuses).

##### SCALED

Adds a flat value (optional) and
gives a bonus depending on another attribute.
