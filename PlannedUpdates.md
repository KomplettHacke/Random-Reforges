_Please note that this mainly for me, so most of the planned stuff will be in German. I dont want to put effort into translating it just to not add it. But stuff which is translated to English will be added unless I get too much negative feedback - So, lemme know what you think :)_
# Released Updates
## Version 2.0
- Reforge Cage experience system change (use xp levels instead of xp)
- ReforgeListGUI: New search function
- Removed random UUIDs (dont even ask me why I used random UUIDs in first place, was dumb)
- Scrollable attribute container
- Removed logic duplicates and moved "Edit Reforge" and "New Reforge" mode into one file
- Added default reforge Shield reforges

## Version 2.1
- Added configurable reforge costs
- Added "Chance per group" overlay in ReforgeListGUI

### Version 2.1.1
- Added item descriptions as ingame guide
- Feedback if you are missing items to reroll reforges

## Version 3.0
- Fixed GUI not working with GUI Scale 3 and 4
- **Scaled Reforges added**
- Reforges now get re-applied automatically to items after editing the reforge
- Fixed some formattings in HelpGUI

### Version 3.0.1
- Advancement "Gonna catch 'em all" (Obtain Soulless Reforge Core) added
- Advancement "Another 'ethical' villager prison..." (Create a Reforge Cage) added

## Version 3.1
- Add "Perfect" reforge mechanic
  - configurable in SettingsGUI
- Make all scrollbars the same (There was no feedback, so Imma choose one) -> Merge them into in file?
- Fix HelpGUI title
- Fix small differences between HelpGUI and ReforgeEditorGUI
- Remove "armor_perfect" reforge

## Version 3.2
- Add Reforge Blacklist
  - Should I remove the "Ignore Default Reforges" button?
- Button to delete single attributes from reforges -> QoL
- Command rework:
  - /reforge <player> apply <reforge-id> [slot, optional]
  - /reforge <player> clear [slot, optional]
  - /reforge <player> lock [slot, optional]
- Bug fixes:
  - Curio reforging not working correctly
  - infinite loops leading to crashs
  - Scaling reforges not working on Curios
  - Recalculating scaling reforges on ALL players on equip change -> Could have led to lags
  - Comment line too short for default reforges -> max length doubled to 256 signs.
  - Events triggering client- and serverside 
  - "ANY" selection in the reforge editor ignored the blacklisted items in the textfield
  
# Planned Updates

## Version 3.3
- Hardmode
  - Items become randomly unreforgable
  - Configurable in SettingsGUI (Activate/Deactivate, Chance)

## Version 4.0
- Server compat
   - I dont know how to test multiplayer stuff (anyone knows a free webhost except aternos?)
