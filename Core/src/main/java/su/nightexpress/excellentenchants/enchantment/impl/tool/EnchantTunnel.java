package su.nightexpress.excellentenchants.enchantment.impl.tool;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.config.JOption;
import su.nexmedia.engine.utils.EntityUtil;
import su.nightexpress.excellentenchants.ExcellentEnchants;
import su.nightexpress.excellentenchants.api.enchantment.type.BlockBreakEnchant;
import su.nightexpress.excellentenchants.enchantment.impl.ExcellentEnchant;
import su.nightexpress.excellentenchants.enchantment.type.FitItemType;
import su.nightexpress.excellentenchants.enchantment.util.EnchantPriority;
import su.nightexpress.excellentenchants.enchantment.util.EnchantUtils;
import su.nightexpress.excellentenchants.hook.impl.NoCheatPlusHook;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class EnchantTunnel extends ExcellentEnchant implements BlockBreakEnchant {

    public static final String   ID                   = "tunnel";
    // X and Z offsets for each block AoE mined
    private static final int[][] MINING_COORD_OFFSETS = new int[][]{{0, 0}, {0, -1}, {-1, 0}, {0, 1}, {1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1},};
    private static final Set<Material> INTERACTABLE_BLOCKS = new HashSet<>();

    static {
        INTERACTABLE_BLOCKS.add(Material.REDSTONE_ORE);
        INTERACTABLE_BLOCKS.add(Material.DEEPSLATE_REDSTONE_ORE);
    }

    private final Set<UUID> activePlayers;
    private boolean disableOnSneak;

    public EnchantTunnel(@NotNull ExcellentEnchants plugin) {
        super(plugin, ID, EnchantPriority.HIGH);
        this.activePlayers = new HashSet<>();

        this.getDefaults().setDescription("Mines multiple blocks at once in a certain shape.");
        this.getDefaults().setLevelMax(3);
        this.getDefaults().setTier(1.0);
        this.getDefaults().setConflicts(EnchantVeinminer.ID, EnchantBlastMining.ID);
    }

    @Override
    public void loadSettings() {
        super.loadSettings();
        this.disableOnSneak = JOption.create("Settings.Ignore_When_Sneaking", true,
            "When 'true' the enchantment won't be triggered when sneaking.").read(cfg);
    }

    @Override
    @NotNull
    public FitItemType[] getFitItemTypes() {
        return new FitItemType[]{FitItemType.PICKAXE, FitItemType.SHOVEL};
    }

    @Override
    @NotNull
    public EnchantmentTarget getItemTarget() {
        return EnchantmentTarget.TOOL;
    }

    public boolean isTunneling(@NotNull Player player) {
        return this.activePlayers.contains(player.getUniqueId());
    }

    @Override
    public boolean onBreak(@NotNull BlockBreakEvent e, @NotNull Player player, @NotNull ItemStack item, int level) {
        if (this.isTunneling(player)) return false;
        if (!this.isAvailableToUse(player)) return false;
        if (this.disableOnSneak && player.isSneaking()) return false;
        if (EnchantUtils.contains(item, EnchantVeinminer.ID)) return false;
        if (EnchantUtils.contains(item, EnchantBlastMining.ID)) return false;

        Block block = e.getBlock();
        if (block.getType().isInteractable() && !INTERACTABLE_BLOCKS.contains(block.getType())) return false;
        if (block.getDrops(item).isEmpty()) return false;

        BlockFace dir = EntityUtil.getDirection(player);
        boolean isY = dir != null && block.getRelative(dir.getOppositeFace()).isEmpty();
        boolean isZ = dir == BlockFace.EAST || dir == BlockFace.WEST;

        // Mine + shape if Tunnel I, 3x3 if Tunnel II
        int blocksBroken = 1;
        if (level == 1) blocksBroken = 2;
        else if (level == 2) blocksBroken = 5;
        else if (level == 3) blocksBroken = 9;

        this.activePlayers.add(player.getUniqueId());
        NoCheatPlusHook.exemptBlocks(player);

        for (int i = 0; i < blocksBroken; i++) {
            if (item.getType().isAir()) break;

            int xAdd = MINING_COORD_OFFSETS[i][0];
            int zAdd = MINING_COORD_OFFSETS[i][1];

            Block blockAdd;
            if (isY) {
                blockAdd = block.getLocation().clone().add(isZ ? 0 : xAdd, zAdd, isZ ? xAdd : 0).getBlock();
            }
            else {
                blockAdd = block.getLocation().clone().add(xAdd, 0, zAdd).getBlock();
            }

            // Skip blocks that should not be mined
            if (blockAdd.equals(block)) continue;
            if (blockAdd.getDrops(item).isEmpty()) continue;
            if (blockAdd.isLiquid()) continue;

            Material addType = blockAdd.getType();

            // Some extra block checks.
            if (addType.isInteractable() && !INTERACTABLE_BLOCKS.contains(addType)) continue;
            if (addType == Material.BEDROCK || addType == Material.END_PORTAL || addType == Material.END_PORTAL_FRAME) continue;
            if (addType == Material.OBSIDIAN && addType != block.getType()) continue;

            player.breakBlock(blockAdd);
        }

        NoCheatPlusHook.unexemptBlocks(player);
        this.activePlayers.remove(player.getUniqueId());
        return true;
    }
}
