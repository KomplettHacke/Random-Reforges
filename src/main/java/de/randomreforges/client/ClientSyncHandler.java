package de.randomreforges.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.randomreforges.gui.ReforgeListGUI;
import de.randomreforges.network.ClientBoundPackets.LockStateSyncPacket;
import de.randomreforges.network.ClientBoundPackets.ReforgeDiffSyncPacket;
import de.randomreforges.network.ClientBoundPackets.SettingsOnlySyncPacket;
import de.randomreforges.network.ClientBoundPackets.SyncReforgesPacket;
import de.randomreforges.reforge.ReforgeManager;
import net.minecraft.client.Minecraft;

/*****************************************************************************************************************************************************************
client only, isolated to prevent loading on dedicated servers
*****************************************************************************************************************************************************************/
public class ClientSyncHandler {

    public static void handleSync(SyncReforgesPacket pkt) {
        ReforgeManager.applySyncedState(
                pkt.getReforgesJson(),
                pkt.getLockedReforges(),
                pkt.isIgnoreDefaultReforges(),
                pkt.getRerollCostLevels(),
                pkt.getRerollCostItem(),
                pkt.getRerollCostItemAmount(),
                pkt.getPerfectChancePercent(),
                pkt.getPerfectBonusPercent(),
                pkt.isConfirmDelete(),
                pkt.isHardmodeEnabled(),
                pkt.getHardmodeLockChance(),
                pkt.getHardmodeDebuffMinPercent(),
                pkt.getHardmodeDebuffMaxPercent(),
                pkt.isHardmodeShowPercentageChange(),
                pkt.isHardmodeShowRange()
        );

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ReforgeListGUI) {
            mc.setScreen(new ReforgeListGUI());
        }
    }

    /*****************************************************************************************************************************************************************
    now only reloads the modified reforge instead of relading everything -> reduces lags
    *****************************************************************************************************************************************************************/
    public static void handleLockUpdate(LockStateSyncPacket pkt) {
        ReforgeManager.setLockedState(pkt.getReforgeId(), pkt.isLocked());

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ReforgeListGUI rlgui) {
            rlgui.refreshList();
        }
    }

    /*****************************************************************************************************************************************************************
    - doesnt re-render the whole screen, only refreshes the list
    - only updates the modified entry
    *****************************************************************************************************************************************************************/
    public static void handleReforgeDiff(ReforgeDiffSyncPacket pkt) {
        if (pkt.isDeleted()) {
            ReforgeManager.removeReforgeFromMemory(pkt.getReforgeId());
        } else {
            try {
                JsonObject obj = JsonParser.parseString(pkt.getReforgeJson()).getAsJsonObject();
                ReforgeManager.applySingleReforgeUpdate(pkt.getReforgeId(), obj);
            } catch (Exception e) {
                de.randomreforges.RandomReforges.LOGGER.error(
                        "Failed to apply reforge diff for '{}'", pkt.getReforgeId(), e);
                return;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ReforgeListGUI rlgui) {
            rlgui.refreshList();
        }
    }

    /*****************************************************************************************************************************************************************
    stopped reloading everything upon changing settings -> no unnecessary reforge reload anymore
    *****************************************************************************************************************************************************************/
    public static void handleSettingsOnly(SettingsOnlySyncPacket pkt) {
        ReforgeManager.applySyncedSettingsOnly(
                pkt.isIgnoreDefaultReforges(),
                pkt.getRerollCostLevels(),
                pkt.getRerollCostItem(),
                pkt.getRerollCostItemAmount(),
                pkt.getPerfectChancePercent(),
                pkt.getPerfectBonusPercent(),
                pkt.isConfirmDelete(),
                pkt.isHardmodeEnabled(),
                pkt.getHardmodeLockChance(),
                pkt.getHardmodeDebuffMinPercent(),
                pkt.getHardmodeDebuffMaxPercent(),
                pkt.isHardmodeShowPercentageChange(),
                pkt.isHardmodeShowRange()
        );
    }
}