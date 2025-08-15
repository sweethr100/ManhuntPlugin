package seml.manhunt

import org.bukkit.plugin.java.JavaPlugin

class Manhunt : JavaPlugin() {

    companion object {
        lateinit var instance: Manhunt
            private set
    }

    lateinit var gameManager: GameManager
    lateinit var teamManager: TeamManager
    lateinit var compassManager: CompassManager

    override fun onEnable() {
        instance = this

        // config.yml 저장 및 로드
        saveDefaultConfig()
        reloadConfig()

        // 관리자 클래스들 초기화
        gameManager = GameManager(this)
        teamManager = TeamManager(this)
        compassManager = CompassManager(this)

        // 이벤트 리스너 등록
        server.pluginManager.registerEvents(ManhuntEventHandler(this), this)

        // 명령어 등록
        getCommand("manhunt")?.setExecutor(ManhuntCommand(this))
        getCommand("tracker")?.setExecutor(TrackerCommand(this))

        logger.info("맨헌트 플러그인 v0.1이 활성화되었습니다!")
    }

    override fun onDisable() {
        gameManager.stopGame()
        logger.info("맨헌트 플러그인 v0.1이 비활성화되었습니다!")
    }

    // config.yml 다시 로드하는 함수
    fun reloadPlugin(): Boolean {
        return try {
            // config.yml 다시 로드
            reloadConfig()

            // 관리자 클래스들 다시 초기화 (필요시)
            compassManager.clearAllTrackers()

            logger.info("config.yml이 성공적으로 다시 로드되었습니다!")
            true
        } catch (e: Exception) {
            logger.severe("config.yml 로드 중 오류 발생: ${e.message}")
            false
        }
    }

    // 설정값 가져오기 편의 함수들
    fun getRunnerHealth(): Double = config.getDouble("game.runner_health", 30.0)
    fun getHunterHealth(): Double = config.getDouble("game.hunter_health", 20.0)
    fun getMaxHunters(): Int = config.getInt("game.max_hunters", 4)

    fun getCompassDuration(): Int = config.getInt("compass.duration", 60)
    fun getCompassCooldown(): Int = config.getInt("compass.cooldown", 300)
    fun shouldShowDistance(): Boolean = config.getBoolean("compass.show_distance", false)
    fun shouldShowDimension(): Boolean = config.getBoolean("compass.show_dimension", false)

    fun getEndSpawnHeight(): Double = config.getDouble("end.spawn_height", 150.0)
    fun shouldShowEndEntry(): Boolean = config.getBoolean("end.show_entry_message", false)
    fun getSlowFallingDuration(): Int = config.getInt("end.slow_falling_duration", 30)

    fun shouldShowGameMessages(): Boolean = config.getBoolean("messages.show_game_messages", true)
    fun shouldShowRoleMessages(): Boolean = config.getBoolean("messages.show_role_messages", true)
    fun shouldShowPortalMessages(): Boolean = config.getBoolean("messages.show_portal_messages", true)

    fun isDebugEnabled(): Boolean = config.getBoolean("debug.enabled", false)
}
