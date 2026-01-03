package my_mod;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class ContractManager {

    // Контракты: Команда -> UUID Жертвы
    private static final Map<String, UUID> teamContracts = new HashMap<>();

    // Таймеры: Команда -> Оставшиеся тики
    private static final Map<String, Integer> teamTimers = new HashMap<>();

    // Оффлайн трекер
    private static final Map<UUID, Long> offlineSince = new HashMap<>();

    // Настройки времени
    private static final int HOUR_IN_TICKS = 72000;
    private static final int TEN_MINUTES_IN_TICKS = 12000;

    private static int checkCycle = 0;

    public static void tick(MinecraftServer server) {
        checkCycle++;

        // 1. УМЕНЬШАЕМ ТАЙМЕРЫ
        for (String teamName : new HashSet<>(teamTimers.keySet())) {
            int time = teamTimers.get(teamName);
            time--;

            if (time <= 0) {
                reRollContractForTeam(server, teamName, true);
            } else {
                teamTimers.put(teamName, time);
            }
        }

        // 2. ПРОВЕРКА "ВСЕГДА ДОЛЖЕН БЫТЬ КОНТРАКТ"
        if (checkCycle % 100 == 0) {
            for (Team team : server.getScoreboard().getTeams()) {
                String tName = team.getName();
                if (!teamContracts.containsKey(tName)) {
                    reRollContractForTeam(server, tName, false);
                }
            }
        }

        // 3. ПРОВЕРКА ОФФЛАЙНА
        if (checkCycle % 20 == 0) {
            checkOfflineTargets(server);
        }
    }

    public static void reRollContractForTeam(MinecraftServer server, String teamName, boolean isTimeExpired) {
        Team team = server.getScoreboard().getTeam(teamName);
        if (team == null) return;

        List<ServerPlayerEntity> potentialTargets = new ArrayList<>();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            AbstractTeam otherTeam = player.getScoreboardTeam();
            if (otherTeam == null || !otherTeam.getName().equals(teamName)) {
                potentialTargets.add(player);
            }
        }

        if (potentialTargets.isEmpty()) {
            if (teamContracts.containsKey(teamName)) {
                teamContracts.remove(teamName);
                teamTimers.remove(teamName);
                broadcastToTeam(server, team, Text.literal("⚠ Нет доступных целей. Ожидание врагов...").formatted(Formatting.YELLOW));
            }
            return;
        }

        ServerPlayerEntity newTarget = potentialTargets.get(new Random().nextInt(potentialTargets.size()));

        // Сброс таймера на 1 час
        teamContracts.put(teamName, newTarget.getUuid());
        teamTimers.put(teamName, HOUR_IN_TICKS);
        offlineSince.remove(newTarget.getUuid());

        // Уведомление охотникам
        if (isTimeExpired) {
            broadcastToTeam(server, team, Text.literal("⌛ Время контракта вышло! Новая цель:").formatted(Formatting.YELLOW));
        } else {
            broadcastToTeam(server, team, Text.literal("⚔ НОВЫЙ КОНТРАКТ!").formatted(Formatting.RED));
        }
        broadcastToTeam(server, team, Text.literal("Цель: ").formatted(Formatting.GRAY)
                .append(Text.literal(newTarget.getName().getString()).formatted(Formatting.GOLD)));


        // === ХОРРОР ЭФФЕКТ ДЛЯ ЖЕРТВЫ ===
        if (newTarget != null) {
            // Текст над хотбаром
            newTarget.sendMessage(Text.literal("Кажется, за мной следят...").formatted(Formatting.GRAY), true);

            // ИСПРАВЛЕНИЕ: Убрали SoundCategory, оставили только Звук, Громкость, Тон
            newTarget.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);

            // Эффект Тьмы на 3 секунды
            newTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0, false, false, false));
        }
    }

    private static void broadcastToTeam(MinecraftServer server, Team team, Text message) {
        for (String memberName : team.getPlayerList()) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberName);
            if (member != null) {
                member.sendMessage(message, false);
            }
        }
    }

    public static void sendContractStatus(ServerPlayerEntity player) {
        AbstractTeam team = player.getScoreboardTeam();
        if (team == null) {
            player.sendMessage(Text.literal("❌ Вы не в команде!").formatted(Formatting.RED), false);
            return;
        }

        if (!(player.getEntityWorld() instanceof ServerWorld)) return;
        MinecraftServer server = ((ServerWorld) player.getEntityWorld()).getServer();

        String teamName = team.getName();
        if (!teamContracts.containsKey(teamName)) {
            player.sendMessage(Text.literal("⚠ У вашей команды сейчас нет активной цели.").formatted(Formatting.YELLOW), false);
            return;
        }

        UUID targetUUID = teamContracts.get(teamName);
        int ticksLeft = teamTimers.getOrDefault(teamName, 0);

        String targetName = "Неизвестно";
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetUUID);

        if (targetPlayer != null) {
            targetName = targetPlayer.getName().getString();
        } else {
            targetName = "Цель оффлайн";
        }

        int totalSeconds = ticksLeft / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);

        player.sendMessage(Text.literal("--- 📜 ВАШ ТЕКУЩИЙ КОНТРАКТ ---").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("🎯 Цель: ").formatted(Formatting.GRAY)
                .append(Text.literal(targetName).formatted(Formatting.RED)), false);
        player.sendMessage(Text.literal("⏳ Обновление через: ").formatted(Formatting.GRAY)
                .append(Text.literal(timeString).formatted(Formatting.YELLOW)), false);
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
                    broadcastToTeam(server, server.getScoreboard().getTeam(teamName),
                            Text.literal("⚠ Цель долго отсутствует. Смена контракта...").formatted(Formatting.YELLOW));
                    reRollContractForTeam(server, teamName, false);
                }
            }
        }
    }

    public static boolean isTarget(String killerTeamName, UUID victimUUID) {
        if (!teamContracts.containsKey(killerTeamName)) return false;
        return teamContracts.get(killerTeamName).equals(victimUUID);
    }

    public static void completeContract(MinecraftServer server, String teamName) {
        reRollContractForTeam(server, teamName, false);
        AirdropManager.triggerOrResetTimer(server);
    }
}