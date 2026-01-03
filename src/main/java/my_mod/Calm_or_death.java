package my_mod;

import net.minecraft.server.MinecraftServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.scoreboard.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Calm_or_death implements ModInitializer {
	public static final String SCOREBOARD_ID = "event_points";

	@Override
	public void onInitialize() {
		System.out.println("Мод Командного Ивента загружается!");
		ModItems.registerModItems();

		// 1. СОЗДАНИЕ ТАБЛИЦЫ
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Scoreboard scoreboard = server.getScoreboard();
			ScoreboardObjective objective = scoreboard.getNullableObjective(SCOREBOARD_ID);

			if (objective == null) {
				objective = scoreboard.addObjective(
						SCOREBOARD_ID,
						ScoreboardCriterion.DUMMY,
						Text.literal("Баллы Команд").formatted(Formatting.GOLD),
						ScoreboardCriterion.RenderType.INTEGER,
						true,
						null
				);
			}
			// Показываем сбоку, чтобы все видели счет команд
			scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
		});

		// 2. ЛОГИКА СМЕРТИ И УБИЙСТВ
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// 1. Проверяем, что умер именно ИГРОК
			if (entity instanceof ServerPlayerEntity victim) {

				MinecraftServer server = victim.getEntityWorld().getServer();
				if (server == null) return;

				Scoreboard scoreboard = server.getScoreboard();
				ScoreboardObjective objective = scoreboard.getNullableObjective(SCOREBOARD_ID);
				if (objective == null) return; // Если скорборда нет, ничего не делаем

				AbstractTeam victimTeam = victim.getScoreboardTeam();

				// === ЧАСТЬ 1: ШТРАФ ЗА СМЕРТЬ (Работает всегда: /kill, лава, мобы) ===
				if (victimTeam != null) {
					String vTeamName = victimTeam.getName();
					ScoreHolder vHolder = ScoreHolder.fromName(vTeamName);
					ScoreAccess vScore = scoreboard.getOrCreateScore(vHolder, objective);

					// Вычитаем 5 очков
					vScore.setScore(vScore.getScore() - 5);
					victim.sendMessage(Text.literal("Ваша команда потеряла 5 очков за смерть.").formatted(Formatting.RED), true);
				}

				// === ЧАСТЬ 2: НАГРАДА ЗА УБИЙСТВО (Только если убил Игрок) ===
				if (source.getAttacker() instanceof ServerPlayerEntity killer) {
					AbstractTeam killerTeam = killer.getScoreboardTeam();

					if (killerTeam != null) {
						// Проверка на огонь по своим
						if (killerTeam.isEqual(victimTeam)) {
							killer.sendMessage(Text.literal("За убийство союзника очки не даются!").formatted(Formatting.RED), true);
							return;
						}

						String kTeamName = killerTeam.getName();
						ScoreHolder kHolder = ScoreHolder.fromName(kTeamName);
						ScoreAccess kScore = scoreboard.getOrCreateScore(kHolder, objective);

						// Прибавляем 10 очков
						kScore.setScore(kScore.getScore() + 10);

						// Оповещение (опционально можно убрать broadcast, если спамит)
						server.getPlayerManager().broadcast(
								Text.literal("Команда " + kTeamName + " выбила игрока команды " + (victimTeam != null ? victimTeam.getName() : "Без команды") + "!").formatted(Formatting.GREEN),
								false
						);
					}
				}
			}
		});
	}
}