package my_mod;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.util.Identifier;

public class LootTableModifier {

    // ID стандартных лут-тейблов сундуков, которые мы хотим изменить
    private static final Identifier VILLAGE_BLACKSMITH_ID = Identifier.of("minecraft", "chests/village_blacksmith");
    private static final Identifier SIMPLE_DUNGEON_ID = Identifier.of("minecraft", "chests/simple_dungeon");
    private static final Identifier BASTION_TREASURE_ID = Identifier.of("minecraft", "chests/bastion_treasure");
    private static final Identifier END_CITY_TREASURE_ID = Identifier.of("minecraft", "chests/end_city_treasure");
    // Добавьте другие ID по необходимости

    public static void registerModifications() {
        // Регистрируем обработчик события MODIFY
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
             {
                 // Правильный синтаксис для Fabric API V3
                 LootPool.Builder runePool = LootPool.builder()
                         .rolls(ConstantLootNumberProvider.create(1))
                         .conditionally(RandomChanceLootCondition.builder(0.65f))
                         .with(ItemEntry.builder(ModItems.RUNE_50)
                                 .weight(60)
                                 .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 1.0f))) // ВАЖНО: без .build() здесь
                         )
                         .with(ItemEntry.builder(ModItems.RUNE_75)
                                 .weight(30)
                                 .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 1.0f)))
                         )
                         .with(ItemEntry.builder(ModItems.RUNE_100)
                                 .weight(15)
                                 .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 1.0f)))
                         );
                // Добавляем созданный пул к существующей таблице добычи
                tableBuilder.pool(runePool.build());
            }
        });
    }
}