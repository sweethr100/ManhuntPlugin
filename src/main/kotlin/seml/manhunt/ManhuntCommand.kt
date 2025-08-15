package seml.manhunt

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ManhuntCommand(private val plugin: Manhunt) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        when {
            args.isEmpty() -> {
                sendHelpMessage(sender)
                return true
            }

            args.size == 1 -> {
                when (args[0].lowercase()) {
                    "start" -> handleStart(sender)
                    "stop" -> handleStop(sender)
                    "reset" -> handleReset(sender)
                    "status" -> handleStatus(sender)
                    "reload" -> handleReload(sender)
                    "help" -> sendHelpMessage(sender)
                    else -> {
                        sender.sendMessage("§c알 수 없는 명령어입니다. /manhunt help를 사용하세요.")
                    }
                }
                return true
            }

            args.size == 2 -> {
                when (args[0].lowercase()) {
                    "setrunner" -> handleSetRunner(sender, args[1])
                    else -> {
                        sender.sendMessage("§c잘못된 명령어 형식입니다. /manhunt help를 사용하세요.")
                    }
                }
                return true
            }

            else -> {
                sender.sendMessage("§c잘못된 명령어 형식입니다. /manhunt help를 사용하세요.")
                return true
            }
        }
    }

    private fun handleStart(sender: Player) {
        when (plugin.gameManager.gameState) {
            GameState.PLAYING -> {
                sender.sendMessage("§c게임이 이미 진행 중입니다!")
            }
            GameState.ENDED -> {
                sender.sendMessage("§c게임이 종료 상태입니다. 먼저 리셋하세요.")
            }
            GameState.WAITING -> {
                val onlineCount = Bukkit.getOnlinePlayers().size

                if (onlineCount < 2) {
                    sender.sendMessage("§c최소 2명의 플레이어가 필요합니다.")
                    return
                }

                if (plugin.gameManager.startGame()) {
                    // 게임 시작 성공 메시지 중복 제거 - GameManager에서 이미 알림을 보내므로 여기서는 제거
                    // sender.sendMessage("§a맨헌트 게임을 시작했습니다!") <- 이 줄 제거
                } else {
                    sender.sendMessage("§c게임을 시작할 수 없습니다.")
                    sender.sendMessage("§e- 러너가 설정되어 있는지 확인하세요.")
                    sender.sendMessage("§e- 최소 2명의 플레이어가 필요합니다.")
                }
            }
        }
    }

    private fun handleStop(sender: Player) {
        if (plugin.gameManager.gameState == GameState.WAITING) {
            sender.sendMessage("§c진행 중인 게임이 없습니다.")
        } else {
            plugin.gameManager.stopGame()
            sender.sendMessage("§a맨헌트 게임을 중단했습니다!")
        }
    }

    private fun handleReset(sender: Player) {
        plugin.gameManager.stopGame()
        plugin.teamManager.clearAllTeams()

        // 모든 플레이어 완전 초기화
        Bukkit.getOnlinePlayers().forEach { player ->
            player.maxHealth = 20.0
            player.health = 20.0
            player.gameMode = org.bukkit.GameMode.SURVIVAL
            player.inventory.clear()

            // 모든 포션 효과 제거
            player.activePotionEffects.forEach { effect ->
                player.removePotionEffect(effect.type)
            }

            player.sendMessage("§e게임이 리셋되었습니다.")
        }

        sender.sendMessage("§a맨헌트 게임을 완전히 리셋했습니다!")
    }

    private fun handleReload(sender: Player) {
        sender.sendMessage("§econfig.yml을 다시 로드하는 중...")

        if (plugin.reloadPlugin()) {
            sender.sendMessage("§a§lconfig.yml이 성공적으로 다시 로드되었습니다!")
            sender.sendMessage("§7새로운 설정이 적용되었습니다.")

            // 현재 적용된 주요 설정값 표시
            sender.sendMessage("§e=== 현재 설정값 ===")
            sender.sendMessage("§f러너 체력: §a${plugin.getRunnerHealth()}")
            sender.sendMessage("§f헌터 체력: §c${plugin.getHunterHealth()}")
            sender.sendMessage("§f최대 헌터 수: §e${plugin.getMaxHunters()}명")
            sender.sendMessage("§f나침반 지속시간: §b${plugin.getCompassDuration()}초")
            sender.sendMessage("§f나침반 쿨다운: §b${plugin.getCompassCooldown()}초")
            sender.sendMessage("§f거리 표시: §${if(plugin.shouldShowDistance()) "a활성화" else "c비활성화"}")
            sender.sendMessage("§f차원 표시: §${if(plugin.shouldShowDimension()) "a활성화" else "c비활성화"}")

        } else {
            sender.sendMessage("§c§lconfig.yml 로드 중 오류가 발생했습니다!")
            sender.sendMessage("§c콘솔을 확인하여 오류 내용을 확인하세요.")
        }
    }

    private fun handleStatus(sender: Player) {
        sender.sendMessage("§e=== 맨헌트 게임 상태 ===")
        sender.sendMessage("§f상태: §${getStateColor(plugin.gameManager.gameState)}${getStateDisplay(plugin.gameManager.gameState)}")

        val runner = plugin.gameManager.runner
        if (runner != null) {
            val status = if (runner.isOnline) "§a온라인" else "§c오프라인"
            sender.sendMessage("§f러너: §a${runner.name} $status")
        } else {
            sender.sendMessage("§f러너: §c설정되지 않음")
        }

        val hunters = Bukkit.getOnlinePlayers().filter { plugin.gameManager.isHunter(it) }
        if (hunters.isNotEmpty()) {
            sender.sendMessage("§f헌터 (${hunters.size}/4명): §c${hunters.joinToString(", ") { it.name }}")
        } else {
            sender.sendMessage("§f헌터: §c없음")
        }

        val spectators = Bukkit.getOnlinePlayers().filter { plugin.gameManager.isSpectator(it) }
        if (spectators.isNotEmpty()) {
            sender.sendMessage("§f관전자 (${spectators.size}명): §7${spectators.joinToString(", ") { it.name }}")
        }

        sender.sendMessage("§f전체 플레이어: §e${Bukkit.getOnlinePlayers().size}명")

        // 드래곤 처치 상태 표시
        if (plugin.gameManager.gameState == GameState.PLAYING) {
            val dragonStatus = if (plugin.gameManager.isDragonDefeated()) "§a처치됨" else "§c생존 중"
            sender.sendMessage("§f엔더드래곤: $dragonStatus")
        }

        // 추가 정보
        if (plugin.gameManager.gameState == GameState.WAITING) {
            val availableHunters = Bukkit.getOnlinePlayers().size - (if (runner != null) 1 else 0)
            val maxHunters = minOf(availableHunters, 4)
            sender.sendMessage("§7게임 시작 시 헌터 수: §e${maxHunters}명")
        }

        sender.sendMessage("§7플러그인 버전: §ev0.1")
    }

    private fun handleSetRunner(sender: Player, playerName: String) {
        val target = Bukkit.getPlayer(playerName)

        if (target == null) {
            sender.sendMessage("§c플레이어 '${playerName}'을(를) 찾을 수 없습니다.")
            return
        }

        if (!target.isOnline) {
            sender.sendMessage("§c플레이어 '${playerName}'이(가) 오프라인입니다.")
            return
        }

        if (plugin.gameManager.setRunner(target)) {
            // 중복 메시지 완전 제거 - GameManager에서 이미 알림을 보내므로 여기서는 제거
            // sender.sendMessage("§a${target.name}을(를) 러너로 설정했습니다!") <- 이 줄 제거
            // target.sendMessage("§a당신이 러너로 설정되었습니다!") <- 이 줄도 제거

            // 헌터 수 예상치 알림만 유지
            val potentialHunters = Bukkit.getOnlinePlayers().filter { it != target }.size
            val actualHunters = minOf(potentialHunters, 4)
            sender.sendMessage("§7게임 시작 시 헌터는 ${actualHunters}명이 됩니다.")

        } else {
            sender.sendMessage("§c게임 진행 중에는 러너를 변경할 수 없습니다.")
        }
    }

    private fun sendHelpMessage(sender: Player) {
        sender.sendMessage("§e=== 맨헌트 명령어 도움말 v0.1 ===")
        sender.sendMessage("§f/manhunt start §7- 게임 시작")
        sender.sendMessage("§f/manhunt stop §7- 게임 중단")
        sender.sendMessage("§f/manhunt reset §7- 게임 리셋")
        sender.sendMessage("§f/manhunt status §7- 현재 상태 확인")
        sender.sendMessage("§f/manhunt setrunner <플레이어> §7- 러너 설정")
        sender.sendMessage("§f/manhunt reload §7- config.yml 다시 로드")
        sender.sendMessage("§f/manhunt help §7- 도움말 표시")
        sender.sendMessage("§e==========================")
        sender.sendMessage("§7• 헌터는 §f/tracker §7명령어로 나침반을 받을 수 있습니다.")
        sender.sendMessage("§7• 헌터는 최대 4명까지 자동 선정됩니다.")
        sender.sendMessage("§7• 5명 초과 시 나머지는 관전자가 됩니다.")
        sender.sendMessage("§7• 러너는 엔드에서 포털 귀환 시 승리합니다.")
    }

    private fun getStateColor(state: GameState): String {
        return when (state) {
            GameState.WAITING -> "e"
            GameState.PLAYING -> "a"
            GameState.ENDED -> "c"
        }
    }

    private fun getStateDisplay(state: GameState): String {
        return when (state) {
            GameState.WAITING -> "대기 중"
            GameState.PLAYING -> "진행 중"
            GameState.ENDED -> "종료됨"
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("manhunt.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> {
                val subCommands = listOf("start", "stop", "reset", "status", "setrunner", "reload", "help")
                subCommands.filter { it.startsWith(args[0].lowercase()) }
            }

            2 -> {
                if (args[0].lowercase() == "setrunner") {
                    Bukkit.getOnlinePlayers().map { it.name }.filter { 
                        it.lowercase().startsWith(args[1].lowercase()) 
                    }
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }
    }
}
