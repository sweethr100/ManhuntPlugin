package seml.manhunt

import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.*
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable

class ManhuntEventHandler(private val plugin: Manhunt) : Listener {

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player

        // 추적 나침반 드랍 방지
        if (plugin.gameManager.isHunter(player) && plugin.compassManager.hasTrackingCompass(player)) {
            val drops = event.drops.iterator()
            while (drops.hasNext()) {
                val item = drops.next()
                if (plugin.compassManager.isTrackingCompass(item)) {
                    drops.remove() // 추적 나침반은 드랍하지 않음
                }
            }
        }

        when {
            plugin.gameManager.isRunner(player) -> {
                // 러너가 죽으면 헌터 승리
                event.deathMessage(net.kyori.adventure.text.Component.text("§c러너 ${player.name}이(가) 사망했습니다!"))
                plugin.gameManager.onRunnerDeath()
            }

            plugin.gameManager.isHunter(player) -> {
                // 헌터가 죽으면 부활 - 메시지 단순화 (부활 안내 제거)
                event.deathMessage(net.kyori.adventure.text.Component.text("§e헌터 ${player.name}이(가) 사망했습니다!"))

                object : BukkitRunnable() {
                    override fun run() {
                        if (player.isOnline && player.isDead) {
                            // 플레이어 부활
                            val spawnLocation = player.world.spawnLocation
                            player.spigot().respawn()
                            player.teleport(spawnLocation)
                            // 헌터는 config에서 설정한 체력으로 부활 (기본 20)
                            val hunterHealth = plugin.getHunterHealth()
                            player.maxHealth = hunterHealth
                            player.health = hunterHealth
                            player.sendMessage("§a자동으로 부활했습니다!")
                        }
                    }
                }.runTaskLater(plugin, 100L) // 5초 후
            }

            plugin.gameManager.isSpectator(player) -> {
                // 관전자가 죽으면 그냥 부활 (체력 20 유지)
                event.deathMessage(net.kyori.adventure.text.Component.text("§7관전자 ${player.name}이(가) 사망했습니다."))

                object : BukkitRunnable() {
                    override fun run() {
                        if (player.isOnline && player.isDead) {
                            val spawnLocation = player.world.spawnLocation
                            player.spigot().respawn()
                            player.teleport(spawnLocation)
                            player.gameMode = GameMode.SPECTATOR
                            // 관전자는 기본 체력 유지
                            player.maxHealth = 20.0
                            player.health = 20.0
                            player.sendMessage("§7관전자로 부활했습니다.")
                        }
                    }
                }.runTaskLater(plugin, 20L) // 1초 후 빠른 부활
            }
        }
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity is EnderDragon && plugin.gameManager.gameState == GameState.PLAYING) {
            plugin.gameManager.onDragonKilled()
        }
    }

    // 추적 나침반 던지기 방지
    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (plugin.compassManager.isTrackingCompass(event.itemDrop.itemStack)) {
            event.isCancelled = true
            event.player.sendMessage("§c추적 나침반은 버릴 수 없습니다!")
        }
    }

    // 추적 나침반 인벤토리 이동 제한
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val currentItem = event.currentItem
        val cursorItem = event.cursor

        // 추적 나침반을 다른 인벤토리로 옮기는 것 방지
        if (plugin.compassManager.isTrackingCompass(currentItem) || 
            plugin.compassManager.isTrackingCompass(cursorItem)) {

            if (event.clickedInventory != player.inventory) {
                event.isCancelled = true
                player.sendMessage("§c추적 나침반은 다른 인벤토리로 옮길 수 없습니다!")
            }
        }
    }

    @EventHandler
    fun onPlayerPortal(event: PlayerPortalEvent) {
        val player = event.player

        // 관전자는 포털 사용 제한
        if (plugin.gameManager.isSpectator(player) && plugin.gameManager.gameState == GameState.PLAYING) {
            event.isCancelled = true
            player.sendMessage("§c관전자는 포털을 사용할 수 없습니다.")
            return
        }

        // 엔드 포털 진입 감지
        if (event.to?.world?.environment == org.bukkit.World.Environment.THE_END) {
            if (plugin.gameManager.gameState == GameState.PLAYING) {
                // 엔드 진입 시 정중앙(0, 0)에서 config 설정 높이로 스폰
                val endWorld = event.to?.world
                if (endWorld != null) {
                    val spawnHeight = plugin.getEndSpawnHeight()
                    val centralSpawnLocation = Location(
                        endWorld,
                        0.5, // X: 정중앙
                        spawnHeight, // Y: config에서 설정한 높이
                        0.5, // Z: 정중앙
                        player.location.yaw,
                        player.location.pitch
                    )

                    event.to = centralSpawnLocation

                    // 엔드 진입 메시지 (config 설정에 따라)
                    if (plugin.shouldShowEndEntry()) {
                        val playerType = when {
                            plugin.gameManager.isRunner(player) -> "§a러너"
                            plugin.gameManager.isHunter(player) -> "§c헌터"
                            else -> "§7플레이어"
                        }

                        // 전체 플레이어에게 알림
                        org.bukkit.Bukkit.getOnlinePlayers().forEach { p ->
                            p.sendMessage("§e$playerType §f${player.name}§e이(가) 엔드에 진입했습니다!")
                        }
                    }

                    // 1틱 후 느린 낙하 효과 적용
                    object : BukkitRunnable() {
                        override fun run() {
                            if (player.isOnline && player.world.environment == org.bukkit.World.Environment.THE_END) {
                                // 느린 낙하 효과 적용 (config에서 지속시간 가져오기)
                                val duration = plugin.getSlowFallingDuration()
                                player.addPotionEffect(
                                    org.bukkit.potion.PotionEffect(
                                        org.bukkit.potion.PotionEffectType.SLOW_FALLING,
                                        duration * 20, // 초를 틱으로 변환
                                        0,
                                        false,
                                        false
                                    )
                                )

                                // 파티클 효과
                                player.world.spawnParticle(
                                    Particle.CLOUD,
                                    player.location,
                                    30,
                                    2.0,
                                    2.0,
                                    2.0,
                                    0.1
                                )

                                player.world.spawnParticle(
                                    Particle.PORTAL,
                                    player.location,
                                    20,
                                    1.0,
                                    1.0,
                                    1.0,
                                    0.5
                                )

                                // 개인에게만 메시지 (좌표 정보 제거됨)
                                player.sendMessage("§e엔드 정중앙에 도착했습니다! 느린 낙하 효과가 적용됩니다.")
                                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.7f, 1.2f)

                                // 전체 플레이어에게 사운드만
                                if (plugin.shouldShowEndEntry()) {
                                    org.bukkit.Bukkit.getOnlinePlayers().forEach { p ->
                                        if (p != player) {
                                            p.playSound(p.location, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.5f)
                                        }
                                    }
                                }
                            }
                        }
                    }.runTaskLater(plugin, 1L)
                }
            }
        }

        // 엔드에서 오버월드로 돌아오는 경우 처리 - 승리 조건 단순화
        if (event.from?.world?.environment == org.bukkit.World.Environment.THE_END &&
            event.to?.world?.environment == org.bukkit.World.Environment.NORMAL) {

            if (plugin.gameManager.gameState == GameState.PLAYING) {

                when {
                    plugin.gameManager.isRunner(player) -> {
                        // 러너가 오버월드로 돌아옴 = 즉시 승리! (드래곤 조건 없음)
                        if (plugin.shouldShowPortalMessages()) {
                            org.bukkit.Bukkit.getOnlinePlayers().forEach { p ->
                                p.sendMessage("§a§l러너 ${player.name}이(가) 엔드에서 포탈로 돌아왔습니다!")
                                p.sendMessage("§a§l러너 승리!")
                            }
                        }

                        // 즉시 승리 처리
                        object : BukkitRunnable() {
                            override fun run() {
                                plugin.gameManager.onRunnerWin()
                            }
                        }.runTaskLater(plugin, 10L) // 0.5초 후 승리 처리 (메시지 후)
                    }

                    plugin.gameManager.isHunter(player) -> {
                        // 헌터가 오버월드로 돌아옴
                        if (plugin.shouldShowPortalMessages()) {
                            val title = net.kyori.adventure.title.Title.title(
                                net.kyori.adventure.text.Component.text("포털 귀환", net.kyori.adventure.text.format.NamedTextColor.YELLOW),
                                net.kyori.adventure.text.Component.text("헌터가 오버월드로 돌아왔습니다", net.kyori.adventure.text.format.NamedTextColor.GOLD),
                                net.kyori.adventure.title.Title.Times.times(
                                    java.time.Duration.ofMillis(300), 
                                    java.time.Duration.ofSeconds(2), 
                                    java.time.Duration.ofMillis(300)
                                )
                            )

                            // 전체 플레이어에게 알림
                            org.bukkit.Bukkit.getOnlinePlayers().forEach { p ->
                                p.sendMessage("§c헌터 §f${player.name}§e이(가) 엔드에서 돌아왔습니다!")
                                p.showTitle(title)
                                p.playSound(p.location, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 0.8f)
                            }

                            // 헌터 본인에게 추가 메시지
                            player.sendMessage("§a오버월드로 성공적으로 돌아왔습니다!")
                            player.sendMessage("§e계속해서 러너를 추적하세요!")
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item

        // 추적 나침반 사용 방지 (우클릭으로 업데이트되는 것을 방지)
        if (plugin.compassManager.isTrackingCompass(item)) {
            if (event.action.isRightClick) {
                event.isCancelled = true
                player.sendMessage("§c이 나침반은 자동으로 업데이트됩니다.")
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        when (plugin.gameManager.gameState) {
            GameState.WAITING -> {
                // 게임 대기 중일 때는 기본 설정
                player.gameMode = GameMode.SURVIVAL
                player.maxHealth = 20.0
                player.health = 20.0
                player.sendMessage("§e맨헌트 게임 대기 중입니다.")
            }

            GameState.PLAYING -> {
                // 게임 진행 중 접속한 경우 관전자로 설정
                player.gameMode = GameMode.SPECTATOR
                player.maxHealth = 20.0
                player.health = 20.0
                player.sendMessage("§7게임이 진행 중입니다. 관전자 모드로 설정됩니다.")
            }

            GameState.ENDED -> {
                // 게임 종료 중일 때도 관전자
                player.gameMode = GameMode.SPECTATOR
                player.maxHealth = 20.0
                player.health = 20.0
                player.sendMessage("§c게임이 종료 중입니다.")
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // 추적 중단
        plugin.compassManager.stopTracking(player)

        // 러너가 나간 경우 게임 종료
        if (plugin.gameManager.isRunner(player) && plugin.gameManager.gameState == GameState.PLAYING) {
            plugin.gameManager.stopGame()
            org.bukkit.Bukkit.broadcastMessage("§c러너가 접속을 종료하여 게임이 종료됩니다.")
        }

        // 모든 헌터가 나간 경우도 게임 종료
        if (plugin.gameManager.isHunter(player) && plugin.gameManager.gameState == GameState.PLAYING) {
            val remainingHunters = org.bukkit.Bukkit.getOnlinePlayers().filter { 
                plugin.gameManager.isHunter(it) && it != player 
            }

            if (remainingHunters.isEmpty()) {
                plugin.gameManager.stopGame()
                org.bukkit.Bukkit.broadcastMessage("§c모든 헌터가 접속을 종료하여 게임이 종료됩니다.")
            }
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        // 게임 중 부활 시에만 특별 체력 설정
        if (plugin.gameManager.gameState == GameState.PLAYING) {
            object : BukkitRunnable() {
                override fun run() {
                    when {
                        plugin.gameManager.isRunner(player) -> {
                            val runnerHealth = plugin.getRunnerHealth()
                            player.maxHealth = runnerHealth
                            player.health = runnerHealth
                            player.gameMode = GameMode.SURVIVAL
                        }
                        plugin.gameManager.isHunter(player) -> {
                            val hunterHealth = plugin.getHunterHealth()
                            player.maxHealth = hunterHealth
                            player.health = hunterHealth
                            player.gameMode = GameMode.SURVIVAL
                        }
                        plugin.gameManager.isSpectator(player) -> {
                            player.maxHealth = 20.0
                            player.health = 20.0
                            player.gameMode = GameMode.SPECTATOR
                        }
                    }
                }
            }.runTaskLater(plugin, 1L)
        } else {
            // 게임 중이 아닐 때는 기본 설정
            object : BukkitRunnable() {
                override fun run() {
                    player.maxHealth = 20.0
                    player.health = 20.0
                    player.gameMode = GameMode.SURVIVAL
                }
            }.runTaskLater(plugin, 1L)
        }
    }
}
