package my_mod;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class ModItems {

    // 1. Руна на 50 очков
    public static final Item RUNE_50 = registerItem("rune_50", settings ->
            new PointRuneItem(settings, 50));

    // 2. Руна на 75 очков
    public static final Item RUNE_75 = registerItem("rune_75", settings ->
            new PointRuneItem(settings, 75));

    // 3. Руна на 100 очков
    public static final Item RUNE_100 = registerItem("rune_100", settings ->
            new PointRuneItem(settings, 100));


    // Вспомогательный метод, который делает всю грязную работу за нас
    private static Item registerItem(String name, Function<Item.Settings, Item> itemFactory) {
        // 1. Создаем ID (Идентификатор)
        Identifier id = Identifier.of("calm_or_death", name);

        // 2. Создаем Ключ Реестра (Новое требование 1.21.2+)
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);

        // 3. Создаем настройки и СРАЗУ передаем туда ключ
        Item.Settings settings = new Item.Settings()
                .registryKey(key)
                .maxCount(16);

        // 4. Создаем сам предмет
        Item item = itemFactory.apply(settings);

        // 5. Регистрируем предмет в игре
        // В некоторых версиях register принимает key, в других id.
        // Используем старый добрый id, так как он работает чаще, а ключ мы уже передали в settings.
        return Registry.register(Registries.ITEM, id, item);
    }

    public static void registerModItems() {
        System.out.println("Регистрация рун завершена!");
    }
}