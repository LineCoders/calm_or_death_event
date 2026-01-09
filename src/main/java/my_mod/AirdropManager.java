package my_mod;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory; // <--- ВАЖНЫЙ ИМПОРТ
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

public class AirdropManager {

    // === ИСПРАВЛЕННЫЙ ЗВУК ===
    public static void playGlobalSound(ServerWorld world, BlockPos pos) {
        // null в первом аргументе означает "играть для всех" (никто не исключен)
        // SoundCategory.MASTER гарантирует, что звук зависит от "Общей громкости"
        // Громкость 10000.0f делает звук слышным в любой точке мира (как гром)
        world.playSound(
                null,
                pos,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.MASTER,
                10000.0f,
                1.0f
        );
    }

    private static boolean isActive = false;
    private static int timer = 0;

    private static final int MIN_TIME = 20 * 60 * 20;
    private static final int MAX_TIME = 30 * 60 * 20;

    public static void tick(MinecraftServer server) {
        // !!! ПРОВЕРКА: Если ты тестируешь в одиночке, убедись, что тут НЕТ server.isDedicated() !!!
        if (!isActive) return;

        timer--;

        if (timer % 20 == 0) {
            int totalSeconds = timer / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;

            String timeString = String.format("%02d:%02d", minutes, seconds);
            Text actionBarText = Text.literal("✈ Груз: " + timeString).formatted(Formatting.DARK_GRAY);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(actionBarText, true);
            }
        }

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
                Text.literal("✈ Сброс груза через " + minutes + " мин.").formatted(Formatting.AQUA),
                false
        );
    }

    public static void spawnAirdrop(MinecraftServer server) {
        isActive = false;
        timer = 0;

        ServerWorld world = server.getOverworld();
        Random random = new Random();

        int x = random.nextInt(1001) - 500;
        int z = random.nextInt(1001) - 500;

        int startY = 320;
        int minY = world.getBottomY();

        BlockPos.Mutable currentPos = new BlockPos.Mutable(x, startY, z);

        while (currentPos.getY() > minY && world.getBlockState(currentPos).isAir()) {
            currentPos.move(0, -1, 0);
        }

        if (currentPos.getY() <= minY + 5) {
            currentPos.setY(100);
        }

        BlockPos impactPos = currentPos.toImmutable();

        // === НАСТРОЙКА ШАНСОВ ===
        // 50% Обычный | 35% Редкий | 15% Легендарный
        int roll = random.nextInt(100); // Число от 0 до 99
        int tier;

        if (roll < 40) {
            tier = 1; // 0-49 (50%)
        } else if (roll < 70){
            tier = 2; // 50-84 (35%)
        } else {
            tier = 3; // 85-99 (15%)
        }

        // Взрыв (визуальный + разрушение)
        float explosionPower = 10.0f; //+ (tier * 0.8f);
        world.createExplosion(null, x, impactPos.getY(), z, explosionPower, ServerWorld.ExplosionSourceType.TNT);

        Block airdropBlock;
        switch (tier) {
            case 2: airdropBlock = Blocks.BARREL; break;
            case 3: airdropBlock = Blocks.YELLOW_SHULKER_BOX; break;
            case 1: default: airdropBlock = Blocks.CHEST;
        }

        BlockPos basePos = impactPos.down(1);
        BlockPos containerPos = impactPos;

        world.setBlockState(basePos, Blocks.CRYING_OBSIDIAN.getDefaultState());
        world.setBlockState(containerPos, airdropBlock.getDefaultState());

        if (world.getBlockEntity(containerPos) instanceof LootableContainerBlockEntity container) {
            fillContainer(world, container, tier);
        }

        Formatting tierColor = switch (tier) {
            case 1 -> Formatting.GREEN;
            case 2 -> Formatting.BLUE;
            case 3 -> Formatting.GOLD;
            default -> Formatting.WHITE;
        };
        String tierName = switch (tier) {
            case 1 -> "ОБЫЧНЫЙ";
            case 2 -> "РЕДКИЙ";
            case 3 -> "ЛЕГЕНДАРНЫЙ";
            default -> "???";
        };

        MutableText message = Text.literal("📦 Груз приземлился. Обнаружены повреждения территории на: " + x + ", " + containerPos.getY() +", " + z).formatted(Formatting.GRAY);


        // ВОСПРОИЗВОДИМ ГРОМКИЙ ЗВУК
        playGlobalSound(world, containerPos);

        server.getPlayerManager().broadcast(message, false);
    }

    private static void fillContainer(ServerWorld world, LootableContainerBlockEntity container, int tier) {
        Random rnd = new Random();

        // Безопасное получение предметов (если мод не загрузил предметы, не крашнется, а вернет воздух)
        var rune50 = net.minecraft.registry.Registries.ITEM.get(Identifier.of("calm_or_death", "rune_50"));
        var rune75 = net.minecraft.registry.Registries.ITEM.get(Identifier.of("calm_or_death", "rune_75"));
        var rune100 = net.minecraft.registry.Registries.ITEM.get(Identifier.of("calm_or_death", "rune_100"));

        container.clear();

        switch (tier) {
            case 1:
                container.setStack(5, new ItemStack(rune50, rnd.nextInt(2) + 1));
                container.setStack(6, new ItemStack(Items.OBSIDIAN, rnd.nextInt(8) + 4));
                container.setStack(14, new ItemStack(Items.WIND_CHARGE, rnd.nextInt(13) + 10));
                container.setStack(9, new ItemStack(Items.ENDER_PEARL, rnd.nextInt(10) + 8));
                container.setStack(15, new ItemStack(Items.GOLDEN_CARROT, rnd.nextInt(32) + 20));
                if (rnd.nextBoolean()) container.setStack(12, new ItemStack(Items.CHORUS_FRUIT, rnd.nextInt(10) + 5));
                if (rnd.nextBoolean()) container.setStack(19, new ItemStack(Items.COOKED_BEEF, rnd.nextInt(8) + 4));
                if (rnd.nextInt(100) < 30) container.setStack(11, new ItemStack(Items.ARROW, rnd.nextInt(10) + 5));
                if (rnd.nextInt(100) < 80) container.setStack(23, new ItemStack(Items.DIAMOND, rnd.nextInt(8) + 3));
                if (rnd.nextInt(100) < 90) container.setStack(2, new ItemStack(Items.IRON_INGOT, rnd.nextInt(22) + 10));

                if (rnd.nextInt(100) < 5) {
                    if (rnd.nextInt(100) < 50) {
                        // Вместо книги - Железная кирка на Эффективность 6
                        container.setStack(0, createEnchantedItem(world, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 6));
                    } else {
                        // Вместо книги - Лук на Силу 6
                        container.setStack(0, createEnchantedItem(world, Items.BOW, Enchantments.POWER, 6));
                    }
                }

                break;

            case 2:
                container.setStack(5, new ItemStack(rune75, rnd.nextInt(3) + 1));
                container.setStack(9, new ItemStack(Items.ENDER_PEARL, rnd.nextInt(16) + 10));
                container.setStack(15, new ItemStack(Items.GOLDEN_CARROT, rnd.nextInt(63) + 32));
                container.setStack(6, new ItemStack(Items.OBSIDIAN, rnd.nextInt(16) + 8));
                container.setStack(14, new ItemStack(Items.WIND_CHARGE, rnd.nextInt(30) + 24));
                if (rnd.nextBoolean()) container.setStack(12, new ItemStack(Items.TOTEM_OF_UNDYING, 1));
                if (rnd.nextInt(100) < 30) container.setStack(11, new ItemStack(Items.ARROW, rnd.nextInt(30) + 5));
                if (rnd.nextInt(100) < 95) container.setStack(23, new ItemStack(Items.DIAMOND, rnd.nextInt(16) + 10));
                if (rnd.nextInt(100) < 95) container.setStack(2, new ItemStack(Items.IRON_INGOT, rnd.nextInt(40) + 25));
                if (rnd.nextInt(100) < 25) container.setStack(2, new ItemStack(Items.NETHERITE_INGOT, 1));

                if (rnd.nextInt(100) < 50) {
                    container.setStack(20, createBook(world, net.minecraft.enchantment.Enchantments.PROTECTION, 4));
                }


                if (rnd.nextInt(100) < 50) {
                    container.setStack(20, createBook(world, Enchantments.EFFICIENCY, 5));
                }


                if (rnd.nextInt(100) < 50) {
                    container.setStack(20, createBook(world, Enchantments.LOOTING, 3));
                }

                if (rnd.nextInt(100) < 50) {
                    container.setStack(20, createBook(world, Enchantments.FORTUNE, 3));
                }

                if (rnd.nextInt(100) < 10) {
                    if (rnd.nextInt(100) < 50) {
                        // Вместо книги - Железная кирка на Эффективность 6
                        container.setStack(0, createEnchantedItem(world, Items.DIAMOND_PICKAXE, Enchantments.FORTUNE, 6));
                    } else {
                        // Вместо книги - Лук на Силу 6
                        container.setStack(0, createEnchantedItem(world, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 6));
                    }
                }

                break;



            case 3:
                container.setStack(13, new ItemStack(rune100, 1));
                if (rnd.nextInt(100) < 30) container.setStack(14, new ItemStack(rune100, 2));
                if (rnd.nextBoolean()) container.setStack(12, new ItemStack(Items.NETHERITE_INGOT, 3));
                container.setStack(6, new ItemStack(Items.OBSIDIAN,24));
                container.setStack(26, new ItemStack(Items.WIND_CHARGE, 35));
                container.setStack(21, new ItemStack(Items.MACE, 1));
                container.setStack(16, new ItemStack(Items.TOTEM_OF_UNDYING, 1));
                container.setStack(9, new ItemStack(Items.ENDER_PEARL, rnd.nextInt(16) + 10));
                container.setStack(15, new ItemStack(Items.GOLDEN_CARROT, rnd.nextInt(63) + 32));
                if (rnd.nextInt(100) < 25) container.setStack(11, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1));
                if (rnd.nextInt(100) < 95) container.setStack(23, new ItemStack(Items.DIAMOND, rnd.nextInt(16) + 10));
                if (rnd.nextInt(100) < 95) container.setStack(2, new ItemStack(Items.IRON_INGOT, rnd.nextInt(40) + 25));

                if (rnd.nextInt(100) < 50) {
                    container.setStack(20, createBook(world, net.minecraft.enchantment.Enchantments.PROTECTION, 4));
                }


                if (rnd.nextInt(100) < 50) {
                    container.setStack(25, createBook(world, Enchantments.EFFICIENCY, 5));
                }


                if (rnd.nextInt(100) < 50) {
                    container.setStack(20, createBook(world, Enchantments.LOOTING, 3));
                }

                if (rnd.nextInt(100) < 50) {
                    container.setStack(17, createBook(world, Enchantments.FORTUNE, 3));
                }

                if (rnd.nextInt(100) < 15) {
                    if (rnd.nextInt(100) < 50) {
                        // Вместо книги - Железная кирка на Эффективность 6
                        container.setStack(0, createEnchantedItem(world, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 6));
                    } else {
                        // Вместо книги - Лук на Силу 6
                        container.setStack(0, createEnchantedItem(world, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 6));
                    }
                }
        }
    }

    private static ItemStack createBook(ServerWorld world, RegistryKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);

        // 1.21: Используем getOptional, чтобы избежать ошибок и получить Reference
        var registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var entry = registry.getOptional(key).orElse(null);

        if (entry != null) {
            ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
            builder.add(entry, level);
            stack.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
        }

        return stack;
    }
    // Новый метод для создания зачарованных ПРЕДМЕТОВ (не книг)
    private static ItemStack createEnchantedItem(ServerWorld world, net.minecraft.item.Item item, RegistryKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);

        // Получаем реестр зачарований
        var registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var entry = registry.getOptional(key).orElse(null);

        // Если зачарование найдено, применяем его к предмету
        if (entry != null) {
            stack.addEnchantment(entry, level);
        }

        return stack;
    }
}