package my_mod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class FirstBloodHandler {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {

            // Проверяем, что умер ИГРОК
            if (entity instanceof ServerPlayerEntity victim) {

                // ИСПРАВЛЕНИЕ: Получаем сервер через мир (самый надежный способ)
                if (!(victim.getEntityWorld() instanceof ServerWorld)) return;
                MinecraftServer server = ((ServerWorld) victim.getEntityWorld()).getServer();

                if (server == null) return;

                Scoreboard scoreboard = server.getScoreboard();
                ScoreboardObjective objective = scoreboard.getNullableObjective(Calm_or_death.SCOREBOARD_ID);
                if (objective == null) return;

                AbstractTeam victimTeam = victim.getScoreboardTeam();

                // === ЧАСТЬ 1: ШТРАФ ЗА СМЕРТЬ (-5 очков) ===
                if (victimTeam != null) {
                    String vTeamName = victimTeam.getName();
                    ScoreHolder vHolder = ScoreHolder.fromName(vTeamName);
                    ScoreAccess vScore = scoreboard.getOrCreateScore(vHolder, objective);

                    // Отнимаем 5 очков
                    vScore.setScore(vScore.getScore() - 5);

                    // Сообщение жертве (без жирного)
                    victim.sendMessage(Text.literal("Вы погибли. Ваша команда потеряла 5 очков.").formatted(Formatting.RED), false);
                }

                // === ЧАСТЬ 2: КТО УБИЛ? (КОНТРАКТЫ И FIRST BLOOD) ===
                if (source.getAttacker() instanceof ServerPlayerEntity killer) {

                    AbstractTeam killerTeam = killer.getScoreboardTeam();

                    if (killerTeam != null) {
                        // Огонь по своим
                        if (killerTeam.isEqual(victimTeam)) {
                            killer.sendMessage(Text.literal("Огонь по своим запрещен!").formatted(Formatting.RED), true);
                            return;
                        }

                        String kTeamName = killerTeam.getName();

                        // DEBUG
                        System.out.println("DEBUG: Killer: " + kTeamName + " -> Victim: " + victim.getName().getString());

                        // ПРОВЕРКА КОНТРАКТА
                        boolean isContractKill = ContractManager.isTarget(kTeamName, victim.getUuid());

                        if (isContractKill) {
                            // --- КОНТРАКТ ВЫПОЛНЕН ---
                            ScoreHolder kHolder = ScoreHolder.fromName(kTeamName);
                            ScoreAccess kScore = scoreboard.getOrCreateScore(kHolder, objective);

                            kScore.setScore(kScore.getScore() + 50);

                            // Звук
                            server.getPlayerManager().getPlayerList().forEach(p ->
                                    p.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
                            );

                            // Сообщение всем
                            server.getPlayerManager().broadcast(
                                    Text.literal("КОНТРАКТ ВЫПОЛНЕН! ").formatted(Formatting.GOLD)
                                            .append(Text.literal(kTeamName).formatted(Formatting.AQUA))
                                            .append(Text.literal(" устранила цель ").formatted(Formatting.GOLD))
                                            .append(Text.literal(victim.getName().getString()).formatted(Formatting.RED))
                                            .append(Text.literal(" (+50 очков)").formatted(Formatting.GREEN)),
                                    false
                            );

                            // Смена цели
                            ContractManager.completeContract(server, kTeamName);

                        } else {
                            // --- ОБЫЧНОЕ УБИЙСТВО / FIRST BLOOD ---
                            ScoreHolder fbHolder = ScoreHolder.fromName("#first_blood_taken");
                            ScoreAccess fbScore = scoreboard.getOrCreateScore(fbHolder, objective);
                            ScoreHolder kHolder = ScoreHolder.fromName(kTeamName);
                            ScoreAccess kScore = scoreboard.getOrCreateScore(kHolder, objective);

                            if (fbScore.getScore() == 0) {
                                // ПЕРВАЯ КРОВЬ
                                fbScore.setScore(1);
                                kScore.setScore(kScore.getScore() + 25);

                                server.getPlayerManager().getPlayerList().forEach(p ->
                                        p.playSound(SoundEvents.ENTITY_WITHER_SHOOT, 1.0f, 1.0f)
                                );

                                server.getPlayerManager().broadcast(
                                        Text.literal("ПЕРВАЯ КРОВЬ! ").formatted(Formatting.DARK_RED)
                                                .append(Text.literal(kTeamName).formatted(Formatting.RED))
                                                .append(Text.literal(" пролила первую кровь убив ").formatted(Formatting.RED))
                                                .append(Text.literal(victim.getName().getString()).formatted(Formatting.GOLD))
                                                .append(Text.literal(" (+25 баллов)").formatted(Formatting.RED)),
                                        false
                                );
                            } else {
                                // Просто убийство не цели
                                killer.sendMessage(Text.literal("Это не ваша цель.").formatted(Formatting.GRAY), true);
                            }
                        }
                    }
                }
            }
        });
    }
}