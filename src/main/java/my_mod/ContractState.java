package my_mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ContractState extends PersistentState {

    // Наши данные (Карты)
    public final Map<String, UUID> teamContracts = new HashMap<>();
    public final Map<String, Integer> teamTimers = new HashMap<>();
    public final Map<UUID, Long> offlineSince = new HashMap<>();

    // === 1. СОЗДАЕМ КОДЕК ДЛЯ UUID ===
    // Minecraft иногда капризничает с Uuids.CODEC в разных версиях,
    // поэтому сделаем свой простой кодек: Строка <-> UUID. Это работает везде.
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    // === 2. СОЗДАЕМ ОСНОВНОЙ КОДЕК ===
    // Это инструкция: "Возьми 3 карты и собери из них ContractState"
    public static final Codec<ContractState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Карта 1: Контракты (String -> UUID)
            Codec.unboundedMap(Codec.STRING, UUID_CODEC)
                    .optionalFieldOf("teamContracts", new HashMap<>()) // Если в файле нет поля, берем пустую карту
                    .forGetter(state -> state.teamContracts),

            // Карта 2: Таймеры (String -> Integer)
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("teamTimers", new HashMap<>())
                    .forGetter(state -> state.teamTimers),

            // Карта 3: Оффлайн (UUID -> Long)
            Codec.unboundedMap(UUID_CODEC, Codec.LONG)
                    .optionalFieldOf("offlineSince", new HashMap<>())
                    .forGetter(state -> state.offlineSince)

    ).apply(instance, (contracts, timers, offline) -> {
        // Функция сборки (вызывается при загрузке)
        ContractState state = new ContractState();
        state.teamContracts.putAll(contracts);
        state.teamTimers.putAll(timers);
        state.offlineSince.putAll(offline);
        return state;
    }));

    // === 3. СОЗДАЕМ ТИП СОХРАНЕНИЯ ===
    // Точно так же, как в AdvancementState
    private static final PersistentStateType<ContractState> TYPE = new PersistentStateType<>(
            "calm_or_death_contracts", // Имя файла: data/calm_or_death_contracts.dat
            ContractState::new,        // Фабрика (пустой)
            CODEC,                     // Кодек (логика)
            DataFixTypes.LEVEL         // Тип фиксера
    );

    // === 4. ПОЛУЧЕНИЕ СОСТОЯНИЯ ===
    public static ContractState getServerState(MinecraftServer server) {
        var stateManager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return stateManager.getOrCreate(TYPE);
    }
}