package my_mod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

    private static final int HOUR_IN_TICKS = 72000;
    private static final int TEN_MINUTES_IN_TICKS = 12000;

    private static int checkCycle = 0;

    public static void tick(MinecraftServer server) {
        checkCycle++;

        ContractState state = ContractState.getServerState(server);
        boolean dirty = false;

        // 1. УМЕНЬШАЕМ ТАЙМЕРЫ
        for (String teamName : new HashSet<>(state.teamTimers.keySet())) {
            int time = state.teamTimers.get(teamName);
            time--;

            if (time <= 0) {
                reRollContractForTeam(server, teamName, true);
            } else {
                state.teamTimers.put(teamName, time);
                dirty = true;
            }
        }

        // 2. ПРОВЕРКА "ВСЕГДА ДОЛЖЕН БЫТЬ КОНТРАКТ"
        if (checkCycle % 100 == 0) {
            for (Team team : server.getScoreboard().getTeams()) {
                String tName = team.getName();
                if (!state.teamContracts.containsKey(tName)) {
                    reRollContractForTeam(server, tName, false);
                }
            }
        }

        // 3. ПРОВЕРКА ОФФЛАЙНА
        if (checkCycle % 20 == 0) {
            checkOfflineTargets(server);
        }

        // 4. ВАЛИДАЦИЯ
        if (checkCycle % 60 == 0) {
            validateContracts(server);
        }

        if (dirty) state.markDirty();
    }

    private static void validateContracts(MinecraftServer server) {
        ContractState state = ContractState.getServerState(server);
        for (String teamName : new HashSet<>(state.teamContracts.keySet())) {
            Team team = server.getScoreboard().getTeam(teamName);
            if (team == null) continue;

            UUID targetUUID = state.teamContracts.get(teamName);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUUID);

            if (target != null) {
                if (team.getPlayerList().contains(target.getName().getString())) {
                    reRollContractForTeam(server, teamName, false);
                }
            }
        }
    }

    public static void reRollContractForTeam(MinecraftServer server, String teamName, boolean isTimeExpired) {
        ContractState state = ContractState.getServerState(server);
        Team team = server.getScoreboard().getTeam(teamName);
        if (team == null) return;

        List<ServerPlayerEntity> potentialTargets = new ArrayList<>();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (team.getPlayerList().contains(player.getName().getString())) {
                continue;
            }
            if (AntiAbuseHandler.isCoolingDown(server, teamName, player.getUuid())) {
                continue;
            }
            potentialTargets.add(player);
        }

        if (potentialTargets.isEmpty()) {
            if (state.teamContracts.containsKey(teamName)) {
                state.teamContracts.remove(teamName);
                state.teamTimers.remove(teamName);
                state.markDirty();
                broadcastToTeam(server, team, Text.literal("⚠ Нет доступных целей (все в кулдауне или оффлайн). Ожидание...").formatted(Formatting.YELLOW));
            }
            return;
        }

        ServerPlayerEntity newTarget = potentialTargets.get(new Random().nextInt(potentialTargets.size()));

        state.teamContracts.put(teamName, newTarget.getUuid());
        state.teamTimers.put(teamName, HOUR_IN_TICKS);
        state.offlineSince.remove(newTarget.getUuid());
        state.markDirty();

        if (isTimeExpired) {
            broadcastToTeam(server, team, Text.literal("⌛ Время контракта вышло! Новая цель:").formatted(Formatting.YELLOW));
        } else {
            broadcastToTeam(server, team, Text.literal("⚔ НОВЫЙ КОНТРАКТ!").formatted(Formatting.RED));
        }
        broadcastToTeam(server, team, Text.literal("Цель: ").formatted(Formatting.GRAY)
                .append(Text.literal(newTarget.getName().getString()).formatted(Formatting.GOLD)));

        if (newTarget != null) {
            newTarget.sendMessage(Text.literal("Кажется, за мной следят...").formatted(Formatting.GRAY), true);
            newTarget.getEntityWorld().playSound(null, newTarget.getX(), newTarget.getY(), newTarget.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            newTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0, false, false, false));

            giveTargetHeadToTeam(server, team, newTarget);
        }
    }

    private static void giveTargetHeadToTeam(MinecraftServer server, Team team, ServerPlayerEntity target) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        ProfileComponent profile = ProfileComponent.ofStatic(target.getGameProfile());
        head.set(DataComponentTypes.PROFILE, profile);

        head.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Голова цели: " + target.getName().getString())
                        .formatted(Formatting.RED));

        for (String memberName : team.getPlayerList()) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberName);
            if (member != null) {
                if (!member.getInventory().insertStack(head.copy())) {
                    member.dropItem(head.copy(), false);
                    member.sendMessage(Text.literal("⚠ Инвентарь полон! Голова упала рядом.").formatted(Formatting.YELLOW), false);
                }
                member.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            }
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

        // БЕЗОПАСНОЕ ПОЛУЧЕНИЕ СЕРВЕРА
        if (!(player.getEntityWorld() instanceof ServerWorld serverWorld)) return;
        MinecraftServer server = serverWorld.getServer();

        ContractState state = ContractState.getServerState(server);

        String teamName = team.getName();
        if (!state.teamContracts.containsKey(teamName)) {
            player.sendMessage(Text.literal("⚠ У вашей команды сейчас нет активной цели.").formatted(Formatting.YELLOW), false);
            return;
        }

        UUID targetUUID = state.teamContracts.get(teamName);
        int ticksLeft = state.teamTimers.getOrDefault(teamName, 0);

        String targetName = "Неизвестно";
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetUUID);

        if (targetPlayer != null) {
            targetName = targetPlayer.getName().getString();
        } else {
            // УБРАЛИ ПОЛУЧЕНИЕ ИЗ КЭША, ЧТОБЫ ИЗБЕЖАТЬ ОШИБОК
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
        ContractState state = ContractState.getServerState(server);

        for (String teamName : new HashSet<>(state.teamContracts.keySet())) {
            UUID targetUUID = state.teamContracts.get(teamName);

            if (server.getPlayerManager().getPlayer(targetUUID) != null) {
                state.offlineSince.remove(targetUUID);
                state.markDirty();
            } else {
                long currentTime = server.getOverworld().getTime();
                state.offlineSince.putIfAbsent(targetUUID, currentTime);
                state.markDirty();

                if (currentTime - state.offlineSince.get(targetUUID) > TEN_MINUTES_IN_TICKS) {
                    broadcastToTeam(server, server.getScoreboard().getTeam(teamName),
                            Text.literal("⚠ Цель долго отсутствует. Смена контракта...").formatted(Formatting.YELLOW));
                    reRollContractForTeam(server, teamName, false);
                }
            }
        }
    }

    public static boolean isTarget(MinecraftServer server, String killerTeamName, UUID victimUUID) {
        ContractState state = ContractState.getServerState(server);
        if (!state.teamContracts.containsKey(killerTeamName)) return false;
        return state.teamContracts.get(killerTeamName).equals(victimUUID);
    }

    public static void completeContract(MinecraftServer server, String teamName) {
        reRollContractForTeam(server, teamName, false);
        AirdropManager.triggerOrResetTimer(server);
    }
}