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
                ServerPlayerEntity killer = null;

                // === 1. ПРОВЕРКА УБИЙЦЫ И КУЛДАУНОВ ===
                // Мы проверяем это ДО того, как списать очки у жертвы.
                if (source.getAttacker() instanceof ServerPlayerEntity pKiller) {
                    killer = pKiller;
                    AbstractTeam killerTeam = killer.getScoreboardTeam();

                    // А) Проверка на огонь по своим
                    if (killerTeam != null && victimTeam != null && killerTeam.isEqual(victimTeam)) {
                        killer.sendMessage(Text.literal("Огонь по своим запрещен!").formatted(Formatting.RED), true);
                        return; // Выходим: никто не теряет и не получает очки
                    }

                    // Б) Проверка на Абуз (Кулдаун)
                    // Метод вернет false, если кулдаун активен.
                    // В этом случае мы выходим, и жертва НЕ теряет очки.
                    if (!AntiAbuseHandler.checkAndApplyCooldown(killer, victim)) {
                        return;
                    }
                }

                // Если мы дошли сюда -> Кулдауна нет (или смерть от моба).
                // Можно начислять штрафы и награды.

                // === 2. РАСЧЕТ СТОИМОСТИ ГОЛОВЫ ===
                int pointValue = 5;
                if (victimTeam != null) {
                    pointValue = calculatePointValue(scoreboard, objective, victimTeam.getName());
                }

                // === 3. ШТРАФ ЗА СМЕРТЬ (Жертва теряет очки) ===
                if (victimTeam != null) {
                    String vTeamName = victimTeam.getName();
                    ScoreHolder vHolder = ScoreHolder.fromName(vTeamName);
                    ScoreAccess vScore = scoreboard.getOrCreateScore(vHolder, objective);

                    int currentScore = vScore.getScore();
                    vScore.setScore(Math.max(0, currentScore - pointValue));

                    if (pointValue > 20) {
                        victim.sendMessage(Text.literal("Ваша команда в топе! Штраф за смерть повышен: -" + pointValue).formatted(Formatting.RED, Formatting.BOLD), false);
                    } else {
                        victim.sendMessage(Text.literal("Вы погибли. Штраф: -" + pointValue).formatted(Formatting.RED), false);
                    }
                }

                // === 4. НАГРАДА ЗА УБИЙСТВО (Убийца получает очки) ===
                if (killer != null) {
                    AbstractTeam killerTeam = killer.getScoreboardTeam();

                    if (killerTeam != null) {
                        String kTeamName = killerTeam.getName();
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
                            // Награда за контракт
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
                            // Обычное убийство
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

                    // Звук убийства
                    server.getPlayerManager().getPlayerList().forEach(p -> {
                        p.playSound(ModSounds.KILL_PLAYER_EVENT, 1.0f, 1.0f);
                    });
                }
            }
        });
    }

    // Вспомогательный метод (без изменений)
    private static int calculatePointValue(Scoreboard scoreboard, ScoreboardObjective objective, String targetTeamName) {
        List<Team> allTeams = new ArrayList<>(scoreboard.getTeams());
        allTeams.sort((t1, t2) -> {
            int score1 = scoreboard.getOrCreateScore(ScoreHolder.fromName(t1.getName()), objective).getScore();
            int score2 = scoreboard.getOrCreateScore(ScoreHolder.fromName(t2.getName()), objective).getScore();
            return Integer.compare(score2, score1);
        });

        int rank = -1;
        for (int i = 0; i < allTeams.size(); i++) {
            if (allTeams.get(i).getName().equals(targetTeamName)) {
                rank = i + 1;
                break;
            }
        }

        if (rank == 1) return 100;
        if (rank == 2) return 75;
        if (rank == 3) return 50;
        if (rank == 4) return 25;
        if (rank == 5) return 10;

        return 5;
    }
}