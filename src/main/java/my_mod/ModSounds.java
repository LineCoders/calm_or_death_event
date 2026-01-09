package my_mod;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final Identifier KILL_PLAYER_ID = Identifier.of("calm_or_death", "s.killplayer");
    public static final SoundEvent KILL_PLAYER_EVENT = SoundEvent.of(KILL_PLAYER_ID);

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, KILL_PLAYER_ID, KILL_PLAYER_EVENT);
    }
}