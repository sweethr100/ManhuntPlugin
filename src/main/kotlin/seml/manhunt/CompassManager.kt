package seml.manhunt

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.scheduler.BukkitRunnable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.*

class CompassManager(private val plugin: Manhunt) {

    private val compassCooldowns = mutableMapOf<UUID, Long>()
    private val activeTrackers = mutableMapOf<UUID, BukkitRunnable>()

    companion object {
        private const val UPDATE_INTERVAL = 20L // 1초마다 업데이트
    }

    fun giveTrackingCompass(hunter: Player): Boolean {
        if (!plugin.gameManager.isHunter(hunter)) {
            hunter.sendMessage("§c헌터만 나침반을 받을 수 있습니다!")
            return false
        }

        val now = System.currentTimeMillis()
        val lastUsed = compassCooldowns[hunter.uniqueId] ?: 0L
        val compassCooldownMs = plugin.getCompassCooldown() * 1000L
        val cooldownRemaining = compassCooldownMs - (now - lastUsed)

        if (cooldownRemaining > 0) {
            val seconds = (cooldownRemaining / 1000).toInt()
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60

            hunter.sendMessage("§c나침반 쿨다운: §f${minutes}분 ${remainingSeconds}초 남음")
            return false
        }

        val runner = plugin.gameManager.runner
        if (runner == null || !runner.isOnline) {
            hunter.sendMessage("§c러너가 온라인이 아닙니다!")
            return false
        }

        // 기존 추적 중단
        stopTracking(hunter)

        // 쿨다운 설정
        compassCooldowns[hunter.uniqueId] = now

        // 추적 나침반 생성 및 지급
        val compass = createTrackingCompass(runner)
        hunter.inventory.addItem(compass)

        // 추적 시작
        startTracking(hunter, runner)

        hunter.sendMessage("§a${runner.name}§f을(를) 추적하는 나침반을 받았습니다!")
        val duration = plugin.getCompassDuration()
        val cooldown = plugin.getCompassCooldown()
        hunter.sendMessage("§e${duration}초간 유효하며, ${cooldown}초 후 다시 사용할 수 있습니다.")

        return true
    }

    private fun createTrackingCompass(target: Player): ItemStack {
        val compass = ItemStack(Material.COMPASS)
        val meta = compass.itemMeta as CompassMeta

        meta.displayName(
            Component.text("추적 나침반", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
        )

        // 기본 설명만 표시 (거리와 차원 정보는 config에 따라)
        val lore = mutableListOf<Component>()
        lore.add(
            Component.text("${target.name}을(를) 추적 중", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        )
        lore.add(
            Component.text("${plugin.getCompassDuration()}초간 유효", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )

        meta.lore(lore)

        // 러너의 위치로 나침반 설정
        if (target.world.environment == org.bukkit.World.Environment.NORMAL) {
            meta.lodestone = target.location
            meta.isLodestoneTracked = false
        }

        compass.itemMeta = meta
        return compass
    }

    private fun startTracking(hunter: Player, runner: Player) {
        val compassDuration = plugin.getCompassDuration()
        val tracker = object : BukkitRunnable() {
            private var ticksRemaining = compassDuration * 20 // 초를 틱으로 변환

            override fun run() {
                if (ticksRemaining <= 0 || !hunter.isOnline || !runner.isOnline) {
                    stopTracking(hunter)
                    return
                }

                // 나침반 업데이트
                updateCompass(hunter, runner)

                // 시간 알림 (30초, 10초, 5초 남았을 때)
                val secondsRemaining = ticksRemaining / 20
                when (secondsRemaining) {
                    30 -> hunter.sendMessage("§e나침반 추적 30초 남음")
                    10 -> hunter.sendMessage("§e나침반 추적 10초 남음")
                    5 -> hunter.sendMessage("§c나침반 추적 5초 남음")
                    1 -> hunter.sendMessage("§c나침반 추적이 곧 종료됩니다!")
                }

                ticksRemaining -= UPDATE_INTERVAL.toInt()
            }
        }

        activeTrackers[hunter.uniqueId] = tracker
        tracker.runTaskTimer(plugin, 0L, UPDATE_INTERVAL)
    }

    private fun updateCompass(hunter: Player, runner: Player) {
        // 헌터 인벤토리에서 추적 나침반 찾기
        val compass = hunter.inventory.contents.find { item ->
            isTrackingCompass(item)
        } ?: return

        val meta = compass.itemMeta as CompassMeta

        // 러너와 헌터가 같은 차원에 있는지 확인
        val lore = mutableListOf<Component>()
        lore.add(
            Component.text("${runner.name}을(를) 추적 중", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        )

        if (hunter.world == runner.world) {
            // 같은 차원이면 러너 위치로 나침반 설정
            meta.lodestone = runner.location
            meta.isLodestoneTracked = false

            // 거리 정보 표시 (config 설정에 따라)
            if (plugin.shouldShowDistance()) {
                val distance = hunter.location.distance(runner.location).toInt()
                lore.add(
                    Component.text("거리: ${distance}블록", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
                )
            }

            // 차원 정보 표시 (config 설정에 따라)
            if (plugin.shouldShowDimension()) {
                lore.add(
                    Component.text("같은 차원", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                )
            }
        } else {
            // 다른 차원이면 차원 정보만 표시 (config 설정에 따라)
            if (plugin.shouldShowDimension()) {
                val runnerDimension = when (runner.world.environment) {
                    org.bukkit.World.Environment.NORMAL -> "오버월드"
                    org.bukkit.World.Environment.NETHER -> "네더"
                    org.bukkit.World.Environment.THE_END -> "엔드"
                    else -> "알 수 없는 차원"
                }

                lore.add(
                    Component.text("위치: $runnerDimension", NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)
                )
                lore.add(
                    Component.text("다른 차원에 있음", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
                )
            }
        }

        meta.lore(lore)
        compass.itemMeta = meta
    }

    fun stopTracking(hunter: Player) {
        val tracker = activeTrackers.remove(hunter.uniqueId)
        tracker?.cancel()

        // 인벤토리에서 추적 나침반 제거
        hunter.inventory.contents.forEachIndexed { index, item ->
            if (isTrackingCompass(item)) {
                hunter.inventory.setItem(index, null)
            }
        }

        hunter.sendMessage("§c나침반 추적이 종료되었습니다.")
    }

    fun clearAllTrackers() {
        activeTrackers.values.forEach { it.cancel() }
        activeTrackers.clear()
        compassCooldowns.clear()

        // 모든 플레이어의 추적 나침반 제거
        Bukkit.getOnlinePlayers().forEach { player ->
            player.inventory.contents.forEachIndexed { index, item ->
                if (isTrackingCompass(item)) {
                    player.inventory.setItem(index, null)
                }
            }
        }
    }

    fun isTrackingCompass(item: ItemStack?): Boolean {
        return item?.type == Material.COMPASS && 
               item.hasItemMeta() && 
               item.itemMeta.hasDisplayName() &&
               item.itemMeta.displayName() == Component.text("추적 나침반", NamedTextColor.GOLD)
                   .decoration(TextDecoration.ITALIC, false)
    }

    // 추적 나침반을 가진 헌터인지 확인
    fun hasTrackingCompass(player: Player): Boolean {
        return player.inventory.contents.any { isTrackingCompass(it) }
    }
}
