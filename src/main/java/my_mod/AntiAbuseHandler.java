package my_mod;

import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AntiAbuseHandler {

    private static final long TEAM_COOLDOWN_MS = 60 * 60 * 1000;

    public static boolean checkAndApplyCooldown(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        Team team = killer.getScoreboardTeam();
        if (team == null) return true;

        // БЕЗОПАСНОЕ ПОЛУЧЕНИЕ СЕРВЕРА
        if (!(killer.getEntityWorld() instanceof ServerWorld serverWorld)) return true;
        MinecraftServer server = serverWorld.getServer();

        AntiAbuseState state = AntiAbuseState.getServerState(server);

        String teamName = team.getName();
        UUID victimId = victim.getUuid();
        long currentTime = System.currentTimeMillis();

        Map<UUID, Long> history = state.teamCooldowns.computeIfAbsent(teamName, k -> new HashMap<>());

        if (history.containsKey(victimId)) {
            long unlockTime = history.get(victimId);
            if (currentTime < unlockTime) {
                long minutesLeft = (unlockTime - currentTime) / 1000 / 60;
                killer.sendMessage(
                        Text.literal("⛔ Ваша команда уже получала очки за этого игрока. Кулдаун: " + minutesLeft + " мин.")
                                .formatted(Formatting.RED),
                        false
                );
                return false;
            }
        }

        history.put(victimId, currentTime + TEAM_COOLDOWN_MS);
        state.markDirty();
        return true;
    }

    public static boolean isCoolingDown(MinecraftServer server, String teamName, UUID victimId) {
        AntiAbuseState state = AntiAbuseState.getServerState(server);

        if (!state.teamCooldowns.containsKey(teamName)) return false;

        Map<UUID, Long> history = state.teamCooldowns.get(teamName);
        Long unlockTime = history.get(victimId);

        if (unlockTime == null) return false;

        if (System.currentTimeMillis() < unlockTime) {
            return true;
        } else {
            history.remove(victimId);
            state.markDirty();
            return false;
        }
    }

    public static void sendCooldownStatus(ServerPlayerEntity player) {
        Team team = player.getScoreboardTeam();
        if (team == null) {
            player.sendMessage(Text.literal("Вы не в команде.").formatted(Formatting.RED), false);
            return;
        }

        // БЕЗОПАСНОЕ ПОЛУЧЕНИЕ СЕРВЕРА
        if (!(player.getEntityWorld() instanceof ServerWorld serverWorld)) return;
        MinecraftServer server = serverWorld.getServer();

        AntiAbuseState state = AntiAbuseState.getServerState(server);

        String teamName = team.getName();
        Map<UUID, Long> history = state.teamCooldowns.get(teamName);
        long currentTime = System.currentTimeMillis();

        if (history == null || history.isEmpty()) {
            player.sendMessage(Text.literal("✅ У вашей команды нет активных ограничений.").formatted(Formatting.GREEN), false);
            return;
        }

        MutableText message = Text.literal("⏳ Активные кулдауны вашей команды:\n").formatted(Formatting.GOLD);
        boolean foundAny = false;

        for (Map.Entry<UUID, Long> entry : new HashMap<>(history).entrySet()) {
            if (currentTime < entry.getValue()) {
                foundAny = true;
                long secondsLeftTotal = (entry.getValue() - currentTime) / 1000;
                long minutes = secondsLeftTotal / 60;
                long seconds = secondsLeftTotal % 60;

                String victimName = "Оффлайн игрок";
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
                if (target != null) {
                    victimName = target.getName().getString();
                }
                // УБРАЛИ ПОЛУЧЕНИЕ ИЗ КЭША

                message.append(Text.literal(" - " + victimName + ": ").formatted(Formatting.GRAY));
                message.append(Text.literal(String.format("%02d:%02d", minutes, seconds)).formatted(Formatting.RED));
                message.append(Text.literal("\n"));
            }
        }

        if (!foundAny) {
            player.sendMessage(Text.literal("✅ У вашей команды нет активных ограничений.").formatted(Formatting.GREEN), false);
        } else {
            player.sendMessage(message, false);
        }
    }
}