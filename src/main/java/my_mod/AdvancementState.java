package my_mod;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class AdvancementState extends PersistentState {

    // Хранилище ID выданных ачивок
    private final Set<String> announcedIds = new HashSet<>();

    // === 1. СОЗДАЕМ КОДЕК (СЕРИАЛИЗАТОР) ===
    // Это инструкция для игры: "Как превратить этот класс в список строк и обратно"
    // Мы используем Codec списка строк и "мапим" его на наш класс.
    public static final Codec<AdvancementState> CODEC = Codec.STRING.listOf().xmap(
            // 1. Как создать состояние из списка строк (при загрузке)
            list -> {
                AdvancementState state = new AdvancementState();
                state.announcedIds.addAll(list);
                return state;
            },
            // 2. Как получить список строк из состояния (при сохранении)
            state -> new ArrayList<>(state.announcedIds)
    );

    // === 2. СОЗДАЕМ ТИП СОХРАНЕНИЯ ===
    // Используем конструктор, который ты показал на скриншоте:
    // (String id, Supplier<T> constructor, Codec<T> codec, DataFixTypes dataFixType)
    private static final PersistentStateType<AdvancementState> TYPE = new PersistentStateType<>(
            "calm_or_death_advancements", // Имя файла
            AdvancementState::new,        // Фабрика (как создать пустой файл)
            CODEC,                        // Наш кодек для обработки данных
            DataFixTypes.LEVEL            // Тип фиксера (LEVEL подходит для данных мира)
    );

    // === 3. ПОЛУЧЕНИЕ СОСТОЯНИЯ ===
    public static AdvancementState getServerState(MinecraftServer server) {
        var stateManager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        // В этой версии getOrCreate принимает только наш TYPE
        return stateManager.getOrCreate(TYPE);
    }

    // === ЛОГИКА МОДА ===

    public boolean hasAnnounced(String id) {
        return announcedIds.contains(id);
    }

    public void addAnnounced(String id) {
        announcedIds.add(id);
        markDirty(); // Помечаем, что данные изменились и их надо сохранить
    }
}