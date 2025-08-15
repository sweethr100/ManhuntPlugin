package seml.manhunt

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import java.time.Duration

enum class GameState {
    WAITING, PLAYING, ENDED
}

class GameManager(private val plugin: Manhunt) {

    var gameState = GameState.WAITING
        private set

    var runner: Player? = null
        private set

    private val hunters = mutableSetOf<Player>()
    private var isDragonKilled = false // 선택적 정보용 (승리 조건 아님)

    fun startGame(): Boolean {
        if (gameState != GameState.WAITING) {
            return false
        }

        if (runner == null) {
            return false
        }

        // 5명 체크: 러너 1명 + 헌터 최소 1명, 최대 4명
        val availableHunters = Bukkit.getOnlinePlayers().filter { it != runner }
        if (availableHunters.isEmpty()) {
            return false
        }

        gameState = GameState.PLAYING
        isDragonKilled = false // 드래곤 처치 상태 초기화

        // 헌터 선정 (최대 4명)
        selectHunters()

        // 모든 플레이어 설정
        setupPlayers()

        // 게임 시작 메시지 (중복 제거 - 하나의 함수에서만 처리)
        if (plugin.shouldShowGameMessages()) {
            broadcastMessage("§a§l맨헌트 게임이 시작되었습니다!")
            // 팀 정보는 별도 메시지로
            broadcastMessage("§e러너: §f${runner?.name}")
            broadcastMessage("§c헌터 (${hunters.size}명): §f${hunters.joinToString(", ") { it.name }}")

            // 관전자 알림
            val spectators = Bukkit.getOnlinePlayers().filter { 
                it != runner && !hunters.contains(it) 
            }
            if (spectators.isNotEmpty()) {
                broadcastMessage("§7관전자: §8${spectators.joinToString(", ") { it.name }}")
            }
        }

        // 타이틀 메시지
        val title = Title.title(
            Component.text("맨헌트 시작!", NamedTextColor.GOLD),
            Component.text("엔드에서 포탈로 돌아오세요!", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        )

        Bukkit.getOnlinePlayers().forEach { player ->
            player.showTitle(title)
            player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
        }

        return true
    }

    private fun selectHunters() {
        hunters.clear()

        // 러너가 아닌 플레이어들을 헌터로 선정 (최대 config에서 설정한 수만큼)
        val availablePlayers = Bukkit.getOnlinePlayers().filter { it != runner }.toMutableList()

        // 랜덤하게 섞어서 공정하게 선정
        availablePlayers.shuffle()

        // 설정된 최대 헌터 수까지만 헌터로 설정
        val maxHunters = plugin.getMaxHunters()
        val hunterCount = minOf(availablePlayers.size, maxHunters)

        for (i in 0 until hunterCount) {
            hunters.add(availablePlayers[i])
        }
    }

    fun stopGame() {
        if (gameState == GameState.WAITING) return

        gameState = GameState.WAITING

        // 게임 상태 초기화
        runner = null
        hunters.clear()
        isDragonKilled = false
        plugin.compassManager.clearAllTrackers()

        // 모든 플레이어를 기본 상태로 복원
        Bukkit.getOnlinePlayers().forEach { player ->
            player.maxHealth = 20.0
            player.health = 20.0
            player.gameMode = GameMode.SURVIVAL
        }

        if (plugin.shouldShowGameMessages()) {
            broadcastMessage("§c§l맨헌트 게임이 종료되었습니다!")
        }
    }

    fun setRunner(player: Player): Boolean {
        if (gameState == GameState.PLAYING) {
            return false
        }

        runner = player

        // 메시지 중복 완전 제거 - 여기서만 메시지 발송
        if (plugin.shouldShowRoleMessages()) {
            // 전체 알림 (1개만)
            broadcastMessage("§e${player.name}§f이(가) 러너로 설정되었습니다!")
            // 개인 알림은 제거 (중복 방지)
        }

        return true
    }

    private fun setupPlayers() {
        // 모든 플레이어를 기본 상태로 초기화
        Bukkit.getOnlinePlayers().forEach { player ->
            player.maxHealth = 20.0
            player.health = 20.0
            player.gameMode = GameMode.SURVIVAL
        }

        // 러너 설정 - config에서 체력 가져오기 (기본 30)
        runner?.let { runner ->
            val runnerHealth = plugin.getRunnerHealth()
            runner.maxHealth = runnerHealth
            runner.health = runnerHealth
            runner.gameMode = GameMode.SURVIVAL
            plugin.teamManager.setPlayerTeam(runner, Team.RUNNER)
        }

        // 헌터 설정 - config에서 체력 가져오기 (기본 20)
        val hunterHealth = plugin.getHunterHealth()
        hunters.forEach { hunter ->
            hunter.maxHealth = hunterHealth
            hunter.health = hunterHealth
            hunter.gameMode = GameMode.SURVIVAL
            plugin.teamManager.setPlayerTeam(hunter, Team.HUNTER)
        }

        // 관전자 설정 (러너도 헌터도 아닌 플레이어들)
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player != runner && !hunters.contains(player)) {
                player.maxHealth = 20.0
                player.health = 20.0
                player.gameMode = GameMode.SPECTATOR
                if (plugin.shouldShowRoleMessages()) {
                    player.sendMessage("§7당신은 관전자입니다. 게임을 구경하세요!")
                }
            }
        }
    }

    fun onRunnerDeath() {
        if (gameState != GameState.PLAYING) return

        // 헌터 승리
        gameState = GameState.ENDED
        isDragonKilled = false // 게임 종료시 초기화

        val title = Title.title(
            Component.text("헌터 승리!", NamedTextColor.RED),
            Component.text("러너가 사망했습니다!", NamedTextColor.DARK_RED),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500))
        )

        Bukkit.getOnlinePlayers().forEach { player ->
            player.showTitle(title)
            player.playSound(player.location, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f)
        }

        if (plugin.shouldShowGameMessages()) {
            broadcastMessage("§c§l게임 종료! 헌터가 승리했습니다!")
        }

        // 5초 후 게임 리셋
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            stopGame()
        }, 100L)
    }

    fun onDragonKilled() {
        if (gameState != GameState.PLAYING) return

        isDragonKilled = true // 정보용으로만 기록 (승리 조건 아님)

        if (plugin.shouldShowGameMessages()) {
            broadcastMessage("§a§l엔더드래곤이 처치되었습니다!")
            broadcastMessage("§7드래곤을 처치했지만, 여전히 포탈로 돌아가야 승리합니다!")
        }
    }

    fun onRunnerWin() {
        if (gameState != GameState.PLAYING) return

        // 러너 승리 - 모든 플레이어에게 승리 표시
        gameState = GameState.ENDED

        // 화려한 승리 타이틀
        val title = Title.title(
            Component.text("러너 승리!", NamedTextColor.GREEN),
            Component.text("포탈을 통해 성공적으로 귀환했습니다!", NamedTextColor.DARK_GREEN),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500))
        )

        // 모든 플레이어에게 타이틀 및 사운드
        Bukkit.getOnlinePlayers().forEach { player ->
            player.showTitle(title)
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f)
        }

        // 승리 메시지
        if (plugin.shouldShowGameMessages()) {
            broadcastMessage("§a§l=== 게임 종료 ===")
            broadcastMessage("§a§l러너가 승리했습니다!")
            broadcastMessage("§e${runner?.name}§f이(가) 엔드에서 성공적으로 귀환했습니다!")
            if (isDragonKilled) {
                broadcastMessage("§7(보너스: 엔더드래곤도 처치했습니다!)")
            }
            broadcastMessage("§a§l===============")
        }

        // 5초 후 게임 리셋
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            stopGame()
        }, 100L)
    }

    fun isHunter(player: Player): Boolean = hunters.contains(player)
    fun isRunner(player: Player): Boolean = runner == player
    fun isSpectator(player: Player): Boolean = !isRunner(player) && !isHunter(player)
    fun isDragonDefeated(): Boolean = isDragonKilled

    private fun broadcastMessage(message: String) {
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(message)
        }
    }
}
