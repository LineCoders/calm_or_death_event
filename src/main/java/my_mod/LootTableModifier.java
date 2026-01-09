package my_mod;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class LootTableModifier {
    private static final float POINT_MODIFIER = 1f;

    // 1. Конфигурация для лута (одинаковая для сундуков и мобов)
    public static class RunePoolConfig {
        public final float chance;          // Общий шанс выпадения пула
        public final int weight10;          // Вес руны 50 очков
        public final int weight25;          // Вес руны 75 очков
        public final int weight50;         // Вес руны 100 очков
        public final int rollsMin;          // Минимальное количество применений пула
        public final int rollsMax;          // Максимальное количество применений пула

        public RunePoolConfig(float chance, int weight50, int weight75, int weight100) {
            this(chance, weight50, weight75, weight100, 1, 1);
        }

        public RunePoolConfig(float chance, int weight50, int weight75, int weight100, int rollsMin, int rollsMax) {
            this.chance = chance;
            this.weight10 = weight50;
            this.weight25 = weight75;
            this.weight50 = weight100;
            this.rollsMin = rollsMin;
            this.rollsMax = rollsMax;
        }
    }

    // 2. Отдельные карты для разных типов лута
    private static final Map<Identifier, RunePoolConfig> CHEST_CONFIGS = new HashMap<>();
    private static final Map<Identifier, RunePoolConfig> ENTITY_CONFIGS = new HashMap<>();
    // ДОБАВИТЬ ЭТУ СТРОКУ:
    private static final Map<Identifier, RunePoolConfig> GAMEPLAY_CONFIGS = new HashMap<>();

    // 3. Методы добавления конфигураций (можно вызывать из registerModifications)
// 3. Методы добавления конфигураций
    public static void addRunePool(Identifier Id, RunePoolConfig config) {
        String path = Id.getPath();

        if (path.startsWith("chests/")) {
            CHEST_CONFIGS.put(Id, config);
        }
        else if (path.startsWith("entities/")) {
            ENTITY_CONFIGS.put(Id, config);
        }
        else {
            // Сюда попадет рыбалка, "Герой деревни", "Подарок от кошки" и т.д.
            GAMEPLAY_CONFIGS.put(Id, config);
        }
    }

    // 4. Вспомогательный метод создания пула
    private static LootPool.Builder createRunePool(RunePoolConfig config) {
        LootPool.Builder pool = LootPool.builder()
                .conditionally(RandomChanceLootCondition.builder(POINT_MODIFIER*config.chance));

        // Добавляем руны только если их вес > 0
        if (config.weight50 > 0) {
            pool.with(ItemEntry.builder(ModItems.RUNE_50)
                    .weight(config.weight50)
                    .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 1.0f)))
            );
        }

        if (config.weight10 > 0) {
            pool.with(ItemEntry.builder(ModItems.RUNE_75)
                    .weight(config.weight25)
                    .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 1.0f)))
            );
        }

        if (config.weight50 > 0) {
            pool.with(ItemEntry.builder(ModItems.RUNE_100)
                    .weight(config.weight50)
                    .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 1.0f)))
            );
        }

        return pool;
    }

    // 5. Главный метод регистрации
    public static void registerModifications() {
        // ========== КОНФИГУРАЦИЯ СУНДУКОВ ==========
        // Деревня
        addRunePool(Identifier.of("minecraft", "chests/village/village_armorer"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_butcher"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_cartographer"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_desert_house"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_fisher"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_fletcher"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_mason"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_plains_house"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_savanna_house"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_shepherd"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_snowy_house"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_taiga_house"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_tannery"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_temple"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_toolsmith"), new RunePoolConfig(0.10f, 40, 40, 0));
        addRunePool(Identifier.of("minecraft", "chests/village/village_weaponsmith"), new RunePoolConfig(0.10f, 40, 40, 0));

        // Заброшенная шахта
        addRunePool(Identifier.of("minecraft", "chests/abandoned_mineshaft"), new RunePoolConfig(0.30f, 50, 30, 20));

        // Крепость (Форт)
        addRunePool(Identifier.of("minecraft", "chests/fortress/fortress_junction"), new RunePoolConfig(0.15f, 30, 40, 30));
        addRunePool(Identifier.of("minecraft", "chests/fortress/fortress_treasure"), new RunePoolConfig(0.10f, 20, 30, 50));

        // Подземелье (Dungeon)
        addRunePool(Identifier.of("minecraft", "chests/simple_dungeon"), new RunePoolConfig(1.0f, 60, 0, 0));

        // Храм в джунглях
        addRunePool(Identifier.of("minecraft", "chests/jungle_temple"), new RunePoolConfig(0.35f, 40, 35, 25));
        addRunePool(Identifier.of("minecraft", "chests/jungle_temple_dispenser"), new RunePoolConfig(0.5f, 70, 20, 10)); // Раздатчик

        // Храм в пустыне (Пирамида)
        addRunePool(Identifier.of("minecraft", "chests/desert_pyramid"), new RunePoolConfig(0.25f, 35, 40, 5));

        // Иглу
        addRunePool(Identifier.of("minecraft", "chests/igloo_chest"), new RunePoolConfig(0.40f, 50, 30, 120));

        // Кораблекрушение
        addRunePool(Identifier.of("minecraft", "chests/shipwreck_map"), new RunePoolConfig(0.15f, 45, 35, 20)); // С картой
        addRunePool(Identifier.of("minecraft", "chests/shipwreck_supply"), new RunePoolConfig(0.25f, 55, 30, 15)); // Припасы
        addRunePool(Identifier.of("minecraft", "chests/shipwreck_treasure"), new RunePoolConfig(0.30f, 30, 40, 30)); // Сокровище

        // Океанские руины
        addRunePool(Identifier.of("minecraft", "chests/underwater_ruin_big"), new RunePoolConfig(0.0f, 40, 35, 25)); // Большие
        addRunePool(Identifier.of("minecraft", "chests/underwater_ruin_small"), new RunePoolConfig(0.0f, 50, 30, 20)); // Маленькие

        // Древний город (Ancient City)
        addRunePool(Identifier.of("minecraft", "chests/ancient_city"), new RunePoolConfig(0.40f, 10, 20, 70)); // Высокий шанс, в основном ценные руны
        addRunePool(Identifier.of("minecraft", "chests/ancient_city_ice_box"), new RunePoolConfig(0.30f, 30, 40, 30)); // Ледяной ящик

        // Конец (End City)
        addRunePool(Identifier.of("minecraft", "chests/end_city_treasure"), new RunePoolConfig(0.10f, 0, 10, 90)); // 100% шанс, почти только руны 100

        // Бонусный сундук при старте
        addRunePool(Identifier.of("minecraft", "chests/spawn_bonus_chest"), new RunePoolConfig(0.0f, 80, 15, 5)); // 100% шанс, но простые руны

        // Убежище (Stronghold)
        addRunePool(Identifier.of("minecraft", "chests/stronghold_corridor"), new RunePoolConfig(0.20f, 45, 35, 20));
        addRunePool(Identifier.of("minecraft", "chests/stronghold_crossing"), new RunePoolConfig(0.20f, 40, 35, 25));
        addRunePool(Identifier.of("minecraft", "chests/stronghold_library"), new RunePoolConfig(0.20f, 30, 40, 30)); // Может выпасть 1-2 руны

        // Бастион
        addRunePool(Identifier.of("minecraft", "chests/bastion_treasure"), new RunePoolConfig(0.20f, 30, 40, 50));
        addRunePool(Identifier.of("minecraft", "chests/bastion_bridge"), new RunePoolConfig(0.15f, 50, 30, 20));
        addRunePool(Identifier.of("minecraft", "chests/bastion_hoglin_stable"), new RunePoolConfig(0.20f, 45, 35, 20));
        addRunePool(Identifier.of("minecraft", "chests/bastion_other"), new RunePoolConfig(0.10f, 55, 30, 15));

        // ========== КОНФИГУРАЦИЯ МОБОВ ==========
        // Зомби (часто спавнятся, низкий шанс)
        addRunePool(Identifier.of("minecraft", "entities/warden"), new RunePoolConfig(0.50f, 20, 20, 60)); //
        addRunePool(Identifier.of("minecraft", "entities/ravager"), new RunePoolConfig(0.30f, 70, 20, 5)); //
        addRunePool(Identifier.of("minecraft", "entities/baby_zombie"), new RunePoolConfig(0.05f, 70, 0, 0)); //
        addRunePool(Identifier.of("minecraft", "entities/wither"), new RunePoolConfig(1.0f, 0, 50, 50)); //
        addRunePool(Identifier.of("minecraft", "entities/wandering_trader"), new RunePoolConfig(0.10f, 70, 0, 0)); //
        addRunePool(Identifier.of("minecraft", "entities/vex"), new RunePoolConfig(0.10f, 70, 20, 0)); //
        addRunePool(Identifier.of("minecraft", "entities/elder_guardian"), new RunePoolConfig(0.50f, 70, 10, 15)); //
        addRunePool(Identifier.of("minecraft", "entities/piglin_brute"), new RunePoolConfig(1.0f, 90, 5, 5));

        addRunePool(Identifier.of("minecraft", "gameplay/fishing/treasure"), new RunePoolConfig(0.5f, 50, 30, 20));

        // ========== РЕГИСТРАЦИЯ ОБРАБОТЧИКА ==========
        // ========== РЕГИСТРАЦИЯ ОБРАБОТЧИКА ==========
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            Identifier id = key.getValue();

            if (CHEST_CONFIGS.containsKey(id)) {
                // ... код для сундуков ...
                RunePoolConfig config = CHEST_CONFIGS.get(id);
                tableBuilder.pool(createRunePool(config).build());
            }
            else if (ENTITY_CONFIGS.containsKey(id)) {
                // ... код для мобов ...
                RunePoolConfig config = ENTITY_CONFIGS.get(id);
                tableBuilder.pool(createRunePool(config).build());
            }
            // ДОБАВИТЬ ЭТОТ БЛОК:
            else if (GAMEPLAY_CONFIGS.containsKey(id)) {
                System.out.println("[Calm or Death] Модифицирую геймплей лут (рыбалка): " + key);
                RunePoolConfig config = GAMEPLAY_CONFIGS.get(id);
                tableBuilder.pool(createRunePool(config).build());
            }
        });
    }
}