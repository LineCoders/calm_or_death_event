package my_mod;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Random;

public class AirdropManager {

    private static boolean isActive = false;
    private static int timer = 0;

    // Настройки времени
    private static final int MIN_TIME = 20 * 60 * 20;
    private static final int MAX_TIME = 30 * 60 * 20;

    // ID предмета
    private static final String ITEM_ID = "calm_or_death:rune_100";

    public static void tick(MinecraftServer server) {
        if (!isActive) return;

        timer--;

        // === ОТОБРАЖЕНИЕ ТАЙМЕРА (ACTION BAR) ===
        if (timer % 20 == 0) {
            int totalSeconds = timer / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;

            String timeString = String.format("%02d:%02d", minutes, seconds);
            Text actionBarText = Text.literal("✈ Аирдроп: " + timeString).formatted(Formatting.AQUA);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(actionBarText, true);
            }
        }

        // === АВТОМАТИЧЕСКИЙ СПАВН ===
        if (timer <= 0) {
            spawnAirdrop(server);
        }
    }

    public static void triggerOrResetTimer(MinecraftServer server) {
        int randomTime = new Random().nextInt(MAX_TIME - MIN_TIME) + MIN_TIME;

        timer = randomTime;
        isActive = true;

        int minutes = randomTime / 20 / 60;

        server.getPlayerManager().broadcast(
                Text.literal("✈ Таймер Аирдропа обновлен! Сброс груза через " + minutes + " мин.").formatted(Formatting.AQUA),
                false
        );
    }

    // ТЕПЕРЬ PUBLIC (ДЛЯ КОМАНДЫ)
    public static void spawnAirdrop(MinecraftServer server) {
        // Сбрасываем таймер, так как спавн произошел
        isActive = false;
        timer = 0;

        ServerWorld world = server.getOverworld();
        Random random = new Random();

        int x = random.nextInt(401) - 200;
        int z = random.nextInt(401) - 200;

        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

        BlockPos pos = new BlockPos(x, y, z);

        world.setBlockState(pos, Blocks.CHEST.getDefaultState());

        if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            var item = Registries.ITEM.get(Identifier.of(ITEM_ID.split(":")[0], ITEM_ID.split(":")[1]));
            if (item != null) {
                chest.setStack(13, new ItemStack(item));
            }
        }

        server.getPlayerManager().broadcast(
                Text.literal("📦 АИРДРОП СБРОШЕН! Координаты: " + x + ", " + y + ", " + z).formatted(Formatting.LIGHT_PURPLE),
                false
        );
    }
}