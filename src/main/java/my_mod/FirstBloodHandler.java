package my_mod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class FirstBloodHandler {

    private static boolean firstBloodAnnounced = false;

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {

            if (entity instanceof ServerPlayerEntity victim) {
                // Безопасное получение сервера
                if (!(victim.getEntityWorld() instanceof ServerWorld)) return;
                MinecraftServer server = ((ServerWorld) victim.getEntityWorld()).getServer();
                if (server == null) return;

                Scoreboard scoreboard = server.getScoreboard();
                ScoreboardObjective objective = scoreboard.getNullableObjective(Calm_or_death.SCOREBOARD_ID);
                if (objective == null) return;

                AbstractTeam victimTeam = victim.getScoreboardTeam();

                // === 0. РАСЧЕТ СТОИМОСТИ ГОЛОВЫ ===
                // Определяем, сколько очков стоит смерть игрока из этой команды
                int pointValue = 5; // По умолчанию (для команд ниже 5 места)
                if (victimTeam != null) {
                    pointValue = calculatePointValue(scoreboard, objective, victimTeam.getName());
                }

                // === ЧАСТЬ 1: ШТРАФ ЗА СМЕРТЬ (Зависит от топа) ===
                if (victimTeam != null) {
                    String vTeamName = victimTeam.getName();
                    ScoreHolder vHolder = ScoreHolder.fromName(vTeamName);
                    ScoreAccess vScore = scoreboard.getOrCreateScore(vHolder, objective);

                    int currentScore = vScore.getScore();
                    // Вычитаем рассчитанное значение, но не уходим ниже нуля
                    vScore.setScore(Math.max(0, currentScore - pointValue));

                    // Сообщение зависит от размера штрафа
                    if (pointValue > 20) {
                        victim.sendMessage(Text.literal("Ваша команда в топе! Штраф за смерть повышен: -" + pointValue).formatted(Formatting.RED, Formatting.BOLD), false);
                    } else {
                        victim.sendMessage(Text.literal("Вы погибли. Штраф: -" + pointValue).formatted(Formatting.RED), false);
                    }
                }

                // === ЧАСТЬ 2: НАГРАДА ЗА УБИЙСТВО ===
                if (source.getAttacker() instanceof ServerPlayerEntity killer) {
                    AbstractTeam killerTeam = killer.getScoreboardTeam();

                    if (killerTeam != null) {
                        if (killerTeam.isEqual(victimTeam)) {
                            killer.sendMessage(Text.literal("Огонь по своим запрещен!").formatted(Formatting.RED), true);
                            return;
                        }

                        // === ЗАЩИТА ОТ АБУЗА (Если включена) ===
                        // if (!AntiAbuseHandler.checkAndApplyCooldown(killer, victim)) {
                        //    return;
                        // }

                        String kTeamName = killerTeam.getName();
                        // Передаем server, как договаривались
                        boolean isContractKill = ContractManager.isTarget(server, kTeamName, victim.getUuid());
                        ScoreHolder kHolder = ScoreHolder.fromName(kTeamName);
                        ScoreAccess kScore = scoreboard.getOrCreateScore(kHolder, objective);

                        // Первая кровь
                        if (!firstBloodAnnounced) {
                            firstBloodAnnounced = true;
                            server.getPlayerManager().broadcast(
                                    Text.literal("🩸 ПЕРВАЯ КРОВЬ! ").formatted(Formatting.DARK_RED, Formatting.BOLD)
                                            .append(Text.literal(kTeamName).formatted(Formatting.RED))
                                            .append(Text.literal(" открывает счет!").formatted(Formatting.GRAY)),
                                    false
                            );
                        }

                        if (isContractKill) {
                            // === КОНТРАКТ (Не меняем, фиксированно 200) ===
                            kScore.setScore(kScore.getScore() + 200);

                            server.getPlayerManager().broadcast(
                                    Text.literal("☠ Контракт выполнен. ").formatted(Formatting.GOLD)
                                            .append(Text.literal(kTeamName).formatted(Formatting.AQUA))
                                            .append(Text.literal(" устранила цель ").formatted(Formatting.GOLD))
                                            .append(Text.literal(victim.getName().getString()).formatted(Formatting.RED)),
                                    false
                            );
                            ContractManager.completeContract(server, kTeamName);

                        } else {
                            // === ОБЫЧНОЕ УБИЙСТВО (Динамическая награда) ===
                            // Награда равна тому, сколько потеряла жертва (pointValue)
                            kScore.setScore(kScore.getScore() + pointValue);

                            server.getPlayerManager().broadcast(
                                    Text.literal("☠ ").formatted(Formatting.GRAY)
                                            .append(Text.literal(killer.getName().getString()).formatted(Formatting.GOLD))
                                            .append(Text.literal(" убил ").formatted(Formatting.GRAY))
                                            .append(Text.literal(victim.getName().getString()).formatted(Formatting.RED))
                                            .append(Text.literal(" (+" + pointValue + " очков)").formatted(Formatting.GREEN)),
                                    false
                            );
                        }
                    }

                    // Звук
                    server.getPlayerManager().getPlayerList().forEach(p -> {
                        p.playSound(ModSounds.KILL_PLAYER_EVENT, 1.0f, 1.0f);
                    });
                }
            }
        });
    }

    // === ВСПОМОГАТЕЛЬНЫЙ МЕТОД: РАСЧЕТ ОЧКОВ ПО РАНГУ ===
    private static int calculatePointValue(Scoreboard scoreboard, ScoreboardObjective objective, String targetTeamName) {
        // 1. Берем все команды
        List<Team> allTeams = new ArrayList<>(scoreboard.getTeams());

        // 2. Сортируем их по очкам (от большего к меньшему)
        allTeams.sort((t1, t2) -> {
            int score1 = scoreboard.getOrCreateScore(ScoreHolder.fromName(t1.getName()), objective).getScore();
            int score2 = scoreboard.getOrCreateScore(ScoreHolder.fromName(t2.getName()), objective).getScore();
            return Integer.compare(score2, score1); // reverse sort
        });

        // 3. Ищем, на каком месте наша жертва
        int rank = -1;
        for (int i = 0; i < allTeams.size(); i++) {
            if (allTeams.get(i).getName().equals(targetTeamName)) {
                rank = i + 1; // +1, так как индекс начинается с 0
                break;
            }
        }

        // 4. Выдаем очки в зависимости от места
        if (rank == 1) return 100; // Топ 1
        if (rank == 2) return 75;  // Топ 2
        if (rank == 3) return 50;  // Топ 3
        if (rank == 4) return 25;  // Топ 4
        if (rank == 5) return 10;  // Топ 5

        return 5; // Топ 6, 7, 8 и все остальные
    }
}