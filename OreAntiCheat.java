import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OreAntiCheat extends JavaPlugin implements Listener {

    private final ConcurrentHashMap<UUID, Integer> violations = new ConcurrentHashMap<>();
    private final Material[] monitoredOres = {
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE
    };

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        startDecayScheduler();
    }

    private void startDecayScheduler() {
        // 每10秒减少1VL（200 ticks = 10秒）
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> 
            violations.replaceAll((uuid, vl) -> vl > 0 ? vl - 1 : 0), 
            200L, 200L
        );
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isMonitoredOre(event.getBlock().getType())) {
            processViolation(event.getPlayer().getUniqueId());
        }
    }

    private boolean isMonitoredOre(Material material) {
        for (Material ore : monitoredOres) {
            if (material == ore) return true;
        }
        return false;
    }

    private void processViolation(UUID playerId) {
        int newVL = violations.merge(playerId, 1, Integer::sum);
        
        if (newVL > 15) {
            executeBan(playerId);
        }
    }

    @SuppressWarnings("deprecation") // 显式声明已知过时API的使用
    private void executeBan(UUID playerId) {
        violations.remove(playerId);
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        
        // 使用兼容性最好的封禁方式
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        banList.addBan(
            player.getName(), // 玩家名称
            "自动封禁：异常矿石挖掘模式检测", 
            new Date(System.currentTimeMillis() + 3600_000L), // 1小时后解封
            "OreAntiCheat"
        );

        // 安全踢出在线玩家
        if (player.isOnline()) {
            Bukkit.getScheduler().runTask(this, () -> 
                player.getPlayer().kickPlayer(
                    "§c封禁警告\n§f原因: §e矿物透视行为检测\n§6解封时间: §a" + 
                    banList.getBanEntry(player.getName()).getExpiration()
                )
            );
        }
    }
}