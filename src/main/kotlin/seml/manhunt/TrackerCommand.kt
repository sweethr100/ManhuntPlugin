package seml.manhunt

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TrackerCommand(private val plugin: Manhunt) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        // 게임이 진행 중인지 확인
        if (plugin.gameManager.gameState != GameState.PLAYING) {
            sender.sendMessage("§c게임이 진행 중일 때만 사용할 수 있습니다.")
            return true
        }

        // 헌터인지 확인 (권한 체크 대신 게임 상태로 확인)
        if (!plugin.gameManager.isHunter(sender)) {
            sender.sendMessage("§c헌터만 이 명령어를 사용할 수 있습니다.")
            return true
        }

        // 나침반 지급 시도
        plugin.compassManager.giveTrackingCompass(sender)

        return true
    }
}
