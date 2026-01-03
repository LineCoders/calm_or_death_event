package my_mod;

import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class ContractManager {

    // Храним контракты: Название Команды -> UUID Жертвы
    private static final Map<String, UUID> teamContracts = new HashMap<>();

    // Храним время оффлайна
    private static final Map<UUID, Long> offlineSince = new HashMap<>();

    private static int globalTimer = 0;

    // 1 час = 72000 тиков
    private static final int HOUR_IN_TICKS = 72000;
    private static final int TEN_MINUTES_IN_TICKS = 12000;

    public static void tick(MinecraftServer server) {
        globalTimer++;

        // 1. АККУРАТНЫЙ ТАЙМЕР (Action Bar)
        // Обновляем раз в секунду
        if (globalTimer % 20 == 0) {
            int ticksLeft = HOUR_IN_TICKS - globalTimer;
            int totalSeconds = ticksLeft / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;

            String timeString = String.format("%02d:%02d", minutes, seconds);

            // Только время, без лишнего текста, чтобы не мешать
            Text timerText = Text.literal("⏳ " + timeString).formatted(Formatting.YELLOW);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(timerText, true);
            }
        }

        // 2. СМЕНА РАЗ В ЧАС
        if (globalTimer >= HOUR_IN_TICKS) {
            globalTimer = 0;
            reRollAllContracts(server);
            server.getPlayerManager().broadcast(Text.literal("⚠ Время вышло! Цели обновлены.").formatted(Formatting.GOLD), false);
        }

        // 3. ПРОВЕРКА ОФФЛАЙНА
        if (globalTimer % 20 == 0) {
            checkOfflineTargets(server);
        }
    }

    private static void checkOfflineTargets(MinecraftServer server) {
        for (String teamName : new HashSet<>(teamContracts.keySet())) {
            UUID targetUUID = teamContracts.get(teamName);

            if (server.getPlayerManager().getPlayer(targetUUID) != null) {
                offlineSince.remove(targetUUID);
            } else {
                long currentTime = server.getOverworld().getTime();
                offlineSince.putIfAbsent(targetUUID, currentTime);

                if (currentTime - offlineSince.get(targetUUID) > TEN_MINUTES_IN_TICKS) {
                    reRollContractForTeam(server, teamName);
                }
            }
        }
    }

    public static void reRollContractForTeam(MinecraftServer server, String teamName) {
        Team team = server.getScoreboard().getTeam(teamName);
        if (team == null) return;

        List<ServerPlayerEntity> potentialTargets = new ArrayList<>();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            AbstractTeam otherTeam = player.getScoreboardTeam();
            // Враги = все, кто не в моей команде
            if (otherTeam == null || !otherTeam.getName().equals(teamName)) {
                potentialTargets.add(player);
            }
        }

        if (potentialTargets.isEmpty()) {
            // Если врагов нет, очищаем контракт
            teamContracts.remove(teamName);
            System.out.println("DEBUG: Для команды " + teamName + " нет доступных целей (все оффлайн или союзники).");
            return;
        }

        ServerPlayerEntity newTarget = potentialTargets.get(new Random().nextInt(potentialTargets.size()));
        teamContracts.put(teamName, newTarget.getUuid());
        offlineSince.remove(newTarget.getUuid());

        // Пишем в чат игрокам команды
        for (String memberName : team.getPlayerList()) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberName);
            if (member != null) {
                member.sendMessage(Text.literal("⚔ НОВЫЙ КОНТРАКТ! Цель: ").formatted(Formatting.RED)
                        .append(Text.literal(newTarget.getName().getString()).formatted(Formatting.GOLD)), false);
            }
        }

        System.out.println("DEBUG: Команде " + teamName + " назначена цель: " + newTarget.getName().getString() + " (" + newTarget.getUuid() + ")");
    }

    public static void reRollAllContracts(MinecraftServer server) {
        for (Team team : server.getScoreboard().getTeams()) {
            reRollContractForTeam(server, team.getName());
        }
    }

    // ГЛАВНАЯ ПРОВЕРКА
    public static boolean isTarget(String killerTeamName, UUID victimUUID) {
        if (!teamContracts.containsKey(killerTeamName)) {
            System.out.println("DEBUG FAIL: У команды " + killerTeamName + " вообще нет активного контракта!");
            return false;
        }

        UUID requiredUUID = teamContracts.get(killerTeamName);
        boolean isMatch = requiredUUID.equals(victimUUID);

        if (isMatch) {
            System.out.println("DEBUG SUCCESS: Цель совпала!");
        } else {
            System.out.println("DEBUG FAIL: Убит " + victimUUID + ", а нужен был " + requiredUUID);
        }

        return isMatch;
    }

    public static void completeContract(MinecraftServer server, String teamName) {
        reRollContractForTeam(server, teamName);
    }
}