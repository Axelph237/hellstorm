package dev.candycanecazoo.packapunch.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.game.GameEvent;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.Refusal;
import dev.candycanecazoo.packapunch.game.Shop;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.pap.PunchDefs;
import dev.candycanecazoo.packapunch.pap.PunchOutcome;
import dev.candycanecazoo.packapunch.pap.PunchResolver;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import dev.candycanecazoo.packapunch.tacz.PapGunStats;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import dev.candycanecazoo.packapunch.util.Codecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Upgrades the item a player is holding. The machine takes it, works on it for {@code pap.process_ticks}, then offers the
 * result to that player alone for {@code pap.collect_ticks}. Uncollected results are lost, as in CoD. A refused item is
 * never taken and costs nothing.
 */
public final class PapMachineBlock extends Block implements EntityBlock, FeatureBlock {
    /** {@code cost} replaces {@code pap.default_cost} for this machine. A {@code PunchDef}'s own cost still wins. */
    public record Config(Optional<Integer> cost) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost").forGetter(Config::cost)
        ).apply(i, Config::new));
    }

    /** An item in the machine. {@code until} is the game time it finishes processing, or, once ready, expires. */
    record Job(UUID owner, ItemStack input, ItemStack result, boolean ready, long until, int collectTicks) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("owner", owner);
            tag.put("input", input.save(new CompoundTag()));
            tag.put("result", result.save(new CompoundTag()));
            tag.putBoolean("ready", ready);
            tag.putLong("until", until);
            tag.putInt("collect_ticks", collectTicks);
            return tag;
        }

        static Job load(CompoundTag tag) {
            return new Job(tag.getUUID("owner"), ItemStack.of(tag.getCompound("input")), ItemStack.of(tag.getCompound("result")),
                    tag.getBoolean("ready"), tag.getLong("until"), tag.getInt("collect_ticks"));
        }
    }

    public static final class Entity extends ConfigBlockEntity<Config> implements Prompted {
        private Optional<Job> job = Optional.empty();
        /** The machine's cost as the running game sees it, synced for the prompt. */
        private int shownCost;

        public Entity(BlockPos pos, BlockState state) {
            super(PapRegistries.PAP_MACHINE_ENTITY.get(), pos, state, Config.CODEC, new Config(Optional.empty()));
        }

        /** What to draw in the machine: the item being worked on, then the result. */
        public ItemStack displayed() {
            return job.map(j -> j.ready() ? j.result() : j.input()).orElse(ItemStack.EMPTY);
        }

        public boolean ready() {
            return job.map(Job::ready).orElse(false);
        }

        public boolean empty() {
            return job.isEmpty();
        }

        @Override
        public Optional<Prompt> prompt() {
            if (job.isEmpty()) {
                return Optional.of(new Prompt("Pack-A-Punch", shownCost, true));
            }
            return job.get().ready() ? Optional.of(new Prompt(job.get().result().getHoverName().getString(), 0, false)) : Optional.empty();
        }

        void use(ServerPlayer player, GameManager.Active game) {
            if (job.isPresent() && job.get().ready() && job.get().owner().equals(player.getUUID())) {
                Shop.buy(player, game, collect());
                return;
            }
            GameSettings settings = game.game().settings();
            ItemStack held = player.getMainHandItem();
            PunchOutcome outcome = PunchResolver.resolve(held.getTag(), TaczBridge.inspect(held).isPresent(), PunchDefs.all(),
                    config().cost().orElse(settings.pap().defaultCost()), settings.pap().maxTier());
            Optional<ItemStack> result = held.isEmpty() ? Optional.empty() : upgraded(held.copyWithCount(1), outcome, settings);
            Shop.buy(player, game, new Shop.Sale() {
                @Override
                public int cost() {
                    return outcome.cost();
                }

                @Override
                public boolean needsPower() {
                    return true;
                }

                @Override
                public Optional<Refusal> check(GameManager.Active g, ServerPlayer p) {
                    if (job.isPresent()) {
                        return Optional.of(new Refusal.Because(job.get().owner().equals(p.getUUID())
                                ? "Your item is still being upgraded" : "The machine is busy"));
                    }
                    if (held.isEmpty()) {
                        return Optional.of(new Refusal.Because("Hold the item you want to upgrade"));
                    }
                    if (outcome instanceof PunchOutcome.Rejected rejected) {
                        return Optional.of(new Refusal.Because(rejected.reason().message()));
                    }
                    return result.isEmpty() ? Optional.of(new Refusal.Because("This item's upgrade isn't installed")) : Optional.empty();
                }

                @Override
                public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                    ItemStack input = held.split(1);
                    long now = g.level().getGameTime();
                    job = Optional.of(new Job(p.getUUID(), input, result.get(), false, now + settings.pap().processTicks(),
                            settings.pap().collectTicks()));
                    changed();
                    g.level().playSound(null, worldPosition, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
                    g.game().events().emit(new GameEvent.PapUsed(p.getUUID(), describe(input)));
                    return Optional.empty();
                }
            });
        }

        private Shop.Sale collect() {
            return new Shop.Sale() {
                @Override
                public int cost() {
                    return 0;
                }

                @Override
                public boolean needsPower() {
                    return false;
                }

                @Override
                public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                    ItemStack give = job.get().result().copy();
                    if (p.getMainHandItem().isEmpty()) {
                        p.setItemInHand(InteractionHand.MAIN_HAND, give);
                    } else if (!p.getInventory().add(give)) {
                        return Optional.of(new Refusal.Because("Your inventory is full"));
                    }
                    job = Optional.empty();
                    changed();
                    return Optional.empty();
                }
            };
        }

        /** The finished item, or empty when it can't be made (a result item or gun that isn't installed). */
        private static Optional<ItemStack> upgraded(ItemStack input, PunchOutcome outcome, GameSettings settings) {
            if (outcome instanceof PunchOutcome.UpgradeTacz) {
                return Optional.of(PapGunStats.upgrade(input, settings.pap()));
            }
            if (outcome instanceof PunchOutcome.Replace replace) {
                return replace.def().result().build(settings.players().reserveMagazines()).map(stack -> {
                    replace.def().name().ifPresent(key -> stack.setHoverName(Component.translatable(key)));
                    PapGunStats.carryOver(input, stack, replace.def().carryOver().ammo(), replace.def().carryOver().attachments());
                    return stack;
                });
            }
            return Optional.empty();
        }

        /** The gun id for TacZ guns, the item id otherwise. */
        private static String describe(ItemStack stack) {
            return TaczBridge.inspect(stack).map(info -> info.gunId().toString())
                    .orElseGet(() -> String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));
        }

        void tick(ServerLevel level) {
            int cost = config().cost().orElseGet(() -> GameManager.get(level)
                    .map(a -> a.game().settings().pap().defaultCost())
                    .orElseGet(() -> ServerConfig.GAME.get().pap().defaultCost()));
            if (cost != shownCost) {
                shownCost = cost;
                changed();
            }
            if (job.isEmpty() || level.getGameTime() < job.get().until()) {
                return;
            }
            Job current = job.get();
            if (!current.ready()) {
                job = Optional.of(new Job(current.owner(), current.input(), current.result(), true,
                        level.getGameTime() + current.collectTicks(), current.collectTicks()));
                level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.2f);
            } else {
                job = Optional.empty();
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(current.owner());
                if (owner != null) {
                    dev.candycanecazoo.packapunch.game.Notices.show(owner, Component.literal("Your upgraded item was lost").withStyle(ChatFormatting.RED));
                }
            }
            changed();
        }

        void clear() {
            if (job.isPresent()) {
                job = Optional.empty();
                changed();
            }
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            job = tag.contains("job") ? Optional.of(Job.load(tag.getCompound("job"))) : Optional.empty();
            shownCost = tag.getInt("shown_cost");
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            job.ifPresent(j -> tag.put("job", j.save()));
            tag.putInt("shown_cost", shownCost);
        }
    }

    public PapMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != PapRegistries.PAP_MACHINE_ENTITY.get()
                ? null
                : (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, pos, s, machine) -> machine.tick((ServerLevel) l);
    }

    @Override
    public FeatureKind featureKind() {
        return FeatureKind.PAP_MACHINE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
            FeatureBlock.use(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void buy(ServerPlayer player, GameManager.Active game, BlockPos pos, BlockState state) {
        if (game.level().getBlockEntity(pos) instanceof Entity machine) {
            machine.use(player, game);
        }
    }

    /** Empties every machine. An item left in one is gone. */
    public static void resetAll(ServerLevel level, Collection<BlockPos> machines) {
        for (BlockPos pos : machines) {
            if (level.getBlockEntity(pos) instanceof Entity machine) {
                machine.clear();
            }
        }
    }

    @Override
    public Component describe(ServerLevel level, BlockPos pos) {
        Optional<Integer> cost = level.getBlockEntity(pos) instanceof Entity machine ? machine.config().cost() : Optional.empty();
        return Component.literal("Pack-a-Punch machine: " + cost.map(c -> c + " points").orElse("the default cost (pap.default_cost)")
                + ". Set with /pap pap cost <points|default>");
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        FeatureBlock.placed(level, pos, state, oldState, featureKind());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        FeatureBlock.removed(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
