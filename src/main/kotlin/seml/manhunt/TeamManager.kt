package seml.manhunt

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

enum class Team(val displayName: String, val color: NamedTextColor) {
    RUNNER("러너", NamedTextColor.GREEN),
    HUNTER("헌터", NamedTextColor.RED)
}

class TeamManager(private val plugin: Manhunt) {

    private val scoreboard: Scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    private val runnerTeam: org.bukkit.scoreboard.Team
    private val hunterTeam: org.bukkit.scoreboard.Team

    init {
        // 스코어보드 팀 초기화
        runnerTeam = scoreboard.getTeam("manhunt_runner") ?: scoreboard.registerNewTeam("manhunt_runner")
        hunterTeam = scoreboard.getTeam("manhunt_hunter") ?: scoreboard.registerNewTeam("manhunt_hunter")

        setupTeams()
    }

    private fun setupTeams() {
        // 러너 팀 설정
        runnerTeam.displayName(Component.text("러너", NamedTextColor.GREEN))
        runnerTeam.color(NamedTextColor.GREEN)
        runnerTeam.setAllowFriendlyFire(false)
        runnerTeam.setCanSeeFriendlyInvisibles(true)

        // 헌터 팀 설정
        hunterTeam.displayName(Component.text("헌터", NamedTextColor.RED))
        hunterTeam.color(NamedTextColor.RED)
        hunterTeam.setAllowFriendlyFire(false)
        hunterTeam.setCanSeeFriendlyInvisibles(true)
    }

    fun setPlayerTeam(player: Player, team: Team) {
        // 기존 팀에서 제거
        removePlayerFromAllTeams(player)

        // 새 팀에 추가
        when (team) {
            Team.RUNNER -> {
                runnerTeam.addEntry(player.name)
                player.sendMessage("§a당신은 러너입니다! 엔더드래곤을 처치하고 오버월드로 돌아오세요!")
            }
            Team.HUNTER -> {
                hunterTeam.addEntry(player.name)
                player.sendMessage("§c당신은 헌터입니다! 러너를 잡으세요!")
                player.sendMessage("§e/tracker 명령어로 나침반을 받을 수 있습니다!")
            }
        }

        // 플레이어에게 스코어보드 적용
        player.scoreboard = scoreboard
    }

    private fun removePlayerFromAllTeams(player: Player) {
        runnerTeam.removeEntry(player.name)
        hunterTeam.removeEntry(player.name)
    }

    fun clearAllTeams() {
        runnerTeam.entries.toList().forEach { runnerTeam.removeEntry(it) }
        hunterTeam.entries.toList().forEach { hunterTeam.removeEntry(it) }
    }

    fun getPlayerTeam(player: Player): Team? {
        return when {
            runnerTeam.hasEntry(player.name) -> Team.RUNNER
            hunterTeam.hasEntry(player.name) -> Team.HUNTER
            else -> null
        }
    }
}
