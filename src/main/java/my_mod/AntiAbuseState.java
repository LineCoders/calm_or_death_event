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

public class AntiAbuseState extends PersistentState {

    // Данные: Команда -> (Игрок -> Время разблокировки)
    public final Map<String, Map<UUID, Long>> teamCooldowns = new HashMap<>();

    // 1. Кодек для UUID
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    // 2. Кодек для внутренней карты (Игрок -> Время)
    private static final Codec<Map<UUID, Long>> INNER_MAP_CODEC = Codec.unboundedMap(UUID_CODEC, Codec.LONG);

    // 3. Основной Кодек для всего состояния
    public static final Codec<AntiAbuseState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, INNER_MAP_CODEC)
                    .optionalFieldOf("teamCooldowns", new HashMap<>())
                    .forGetter(state -> state.teamCooldowns)
    ).apply(instance, (map) -> {
        AntiAbuseState state = new AntiAbuseState();
        state.teamCooldowns.putAll(map);
        return state;
    }));

    // 4. Тип сохранения
    private static final PersistentStateType<AntiAbuseState> TYPE = new PersistentStateType<>(
            "calm_or_death_cooldowns", // Имя файла
            AntiAbuseState::new,       // Фабрика
            CODEC,                     // Кодек
            DataFixTypes.LEVEL
    );

    // 5. Получение состояния
    public static AntiAbuseState getServerState(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE);
    }
}