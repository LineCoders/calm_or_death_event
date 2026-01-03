package my_mod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.scoreboard.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult; // Обычный ActionResult
import net.minecraft.world.World;

import java.util.List;

public class PointRuneItem extends Item {

    private final int points;

    public PointRuneItem(Settings settings, int points) {
        super(settings);
        this.points = points;
    }

    // ИСПРАВЛЕНО: Возвращаем просто ActionResult
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        // Получаем предмет, но возвращать его в конце не нужно
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient()) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;
            AbstractTeam team = player.getScoreboardTeam();

            if (team == null) {
                player.sendMessage(Text.literal("Вы не в команде!").formatted(Formatting.RED), true);
                // ИСПРАВЛЕНО: Просто FAIL (без стака)
                return ActionResult.FAIL;
            }

            Scoreboard scoreboard = world.getServer().getScoreboard();
            ScoreboardObjective objective = scoreboard.getNullableObjective(Calm_or_death.SCOREBOARD_ID);

            if (objective != null) {
                String teamName = team.getName();
                ScoreHolder teamHolder = ScoreHolder.fromName(teamName);
                ScoreAccess teamScore = scoreboard.getOrCreateScore(teamHolder, objective);

                // Начисляем очки
                teamScore.setScore(teamScore.getScore() + this.points);

                player.sendMessage(Text.literal("Руна активирована! +" + this.points + " очков!").formatted(Formatting.GOLD), true);

                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);

                // Уменьшаем кол-во предметов в руке
                if (!player.isCreative()) {
                    stack.decrement(1);
                }

                // ИСПРАВЛЕНО: Просто SUCCESS (без стака)
                return ActionResult.SUCCESS;
            }
        }

        // Для клиента
        return ActionResult.SUCCESS;
    }
}