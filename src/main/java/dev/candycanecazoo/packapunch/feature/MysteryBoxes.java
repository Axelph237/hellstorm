package dev.candycanecazoo.packapunch.feature;

import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.data.BoxPool;
import dev.candycanecazoo.packapunch.data.BoxPools;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.game.Game;
import dev.candycanecazoo.packapunch.game.GameEvent;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.PlayerState;
import dev.candycanecazoo.packapunch.game.Refusal;
import dev.candycanecazoo.packapunch.game.Shop;
import dev.candycanecazoo.packapunch.map.FeatureKind;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The Mystery Box across a game: which location it is at, how many rolls since it last moved, each pool built for the
 * game, and Fire Sale. The box is placed when a game is registered and put away by {@code MapReset}.
 */
public final class MysteryBoxes {
    private static final int REEL_SIZE = 12;
    private static final Map<Game, State> STATES = new WeakHashMap<>();

    private static final class State {
        Optional<BlockPos> home = Optional.empty();
        int usesSinceMove;
        boolean fireSale;
        final Map<ResourceLocation, List<BoxPool.Weighted>> pools = new HashMap<>();
    }

    private MysteryBoxes() {}

    private static State state(GameManager.Active game) {
        return STATES.computeIfAbsent(game.game(), g -> new State());
    }

    /** Puts the box at a random location for a new game. */
    public static void start(GameManager.Active game) {
        State state = state(game);
        List<BlockPos> locations = locations(game);
        locations.forEach(pos -> setActive(game.level(), pos, false));
        state.home = locations.isEmpty() ? Optional.empty() : Optional.of(locations.get(game.level().getRandom().nextInt(locations.size())));
        state.home.ifPresent(pos -> activate(game, pos));
    }

    public static Optional<BlockPos> home(GameManager.Active game) {
        return state(game).home;
    }

    public static int usesSinceMove(GameManager.Active game) {
        return state(game).usesSinceMove;
    }

    /** Puts every box away: idle and inactive. */
    public static void resetAll(ServerLevel level, Collection<BlockPos> boxes) {
        for (BlockPos pos : boxes) {
            MysteryBoxBlock.entity(level, pos).ifPresent(MysteryBoxBlock.Entity::idle);
            setActive(level, pos, false);
        }
    }

    public static boolean fireSale(GameManager.Active game) {
        return state(game).fireSale;
    }

    /**
     * Fire Sale (from the power-up, later): every location opens at {@code box.fire_sale_cost}. When it ends, locations
     * other than the box's own close as soon as they are idle.
     */
    public static void setFireSale(GameManager.Active game, boolean on) {
        State state = state(game);
        state.fireSale = on;
        for (BlockPos pos : locations(game)) {
            boolean home = state.home.equals(Optional.of(pos));
            if (on || home) {
                activate(game, pos);
            } else {
                MysteryBoxBlock.entity(game.level(), pos).filter(e -> e.phase() == MysteryBoxBlock.Phase.IDLE)
                        .ifPresent(e -> setActive(game.level(), pos, false));
            }
        }
    }

    /** What a roll at this box costs now. */
    public static int price(GameManager.Active game, MysteryBoxBlock.Entity box) {
        GameSettings.Box settings = game.game().settings().box();
        return state(game).fireSale ? settings.fireSaleCost() : box.config().cost().orElse(settings.cost());
    }

    public static void refreshPrice(GameManager.Active game, MysteryBoxBlock.Entity box) {
        box.setPrice(price(game, box));
    }

    static void use(ServerPlayer player, GameManager.Active game, MysteryBoxBlock.Entity box) {
        if (!box.active()) {
            Shop.refuse(player, new Refusal.Because("The Mystery Box isn't here"));
            return;
        }
        if (box.phase() == MysteryBoxBlock.Phase.OFFERING) {
            if (box.roller().equals(Optional.of(player.getUUID()))) {
                collect(player, game, box);
            } else {
                Shop.refuse(player, new Refusal.Because("That's someone else's"));
            }
            return;
        }
        if (box.phase() == MysteryBoxBlock.Phase.ROLLING) {
            Shop.refuse(player, new Refusal.Because("The Mystery Box is in use"));
            return;
        }
        int cost = price(game, box);
        Shop.buy(player, game, new Shop.Sale() {
            @Override
            public int cost() {
                return cost;
            }

            @Override
            public boolean needsPower() {
                return false;
            }

            @Override
            public Optional<Refusal> check(GameManager.Active g, ServerPlayer p) {
                if (BoxPools.get(box.config().pool()).isEmpty()) {
                    return Optional.of(new Refusal.Because("Unknown box pool " + box.config().pool()));
                }
                return pool(g, box.config().pool()).isEmpty() ? Optional.of(new Refusal.Because("The Mystery Box is empty")) : Optional.empty();
            }

            @Override
            public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                box.startRoll(p, cost, reel(pool(g, box.config().pool()), g.level().getRandom()), g.game().settings().box().rollTicks());
                return Optional.empty();
            }
        });
    }

    /** At the end of a roll: the box moves, or the roller gets something. */
    static void finishRoll(GameManager.Active game, MysteryBoxBlock.Entity box) {
        State state = state(game);
        GameSettings.Box settings = game.game().settings().box();
        boolean canMove = !state.fireSale && state.home.equals(Optional.of(box.getBlockPos())) && locations(game).size() > 1;
        finishRoll(game, box, canMove && shouldMove(state.usesSinceMove, settings.minUsesBeforeMove(), settings.moveChance(),
                game.level().getRandom().nextDouble()));
    }

    /** After {@code minUses} rolls at a location, each roll moves the box with {@code chance}. */
    public static boolean shouldMove(int usesSinceMove, int minUses, double chance, double roll) {
        return usesSinceMove >= minUses && roll < chance;
    }

    public static void finishRoll(GameManager.Active game, MysteryBoxBlock.Entity box, boolean move) {
        if (box.roller().isEmpty()) {
            box.idle();
            return;
        }
        if (move) {
            move(game, box);
            return;
        }
        Optional<ServerPlayer> player = box.rollerPlayer();
        Optional<ItemSpec> picked = BoxPool.pick(pool(game, box.config().pool()),
                spec -> player.map(p -> WallBuyBlock.find(p.getInventory(), spec).isPresent()).orElse(false),
                game.level().getRandom());
        Optional<ItemStack> stack = picked.flatMap(spec -> spec.build(game.game().settings().players().reserveMagazines()));
        if (stack.isEmpty()) {
            game.game().refund(box.roller().get(), box.paid());
            player.ifPresent(p -> Shop.refuse(p, new Refusal.Because("The Mystery Box has nothing new for you")));
            box.idle();
            return;
        }
        state(game).usesSinceMove++;
        box.offer(stack.get(), picked.get().describe(), game.game().settings().box().collectTicks());
        game.game().events().emit(new GameEvent.MysteryBoxOpened(box.roller().get(), picked.get().describe(), box.getBlockPos()));
    }

    private static void collect(ServerPlayer player, GameManager.Active game, MysteryBoxBlock.Entity box) {
        Optional<PlayerState> state = game.game().player(player.getUUID());
        if (state.isEmpty() || !state.get().alive()) {
            Shop.refuse(player, new Refusal.NotPlaying());
            return;
        }
        Optional<Refusal> refused = WallBuyBlock.give(player, game.game().settings(), box.result().copy());
        if (refused.isPresent()) {
            Shop.refuse(player, refused.get());
            return;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.0f);
        box.idle();
        afterUse(game, box);
    }

    /** A Fire Sale location closes once it's done if the Fire Sale is over. */
    static void afterUse(GameManager.Active game, MysteryBoxBlock.Entity box) {
        State state = state(game);
        if (!state.fireSale && !state.home.equals(Optional.of(box.getBlockPos()))) {
            setActive(game.level(), box.getBlockPos(), false);
        }
    }

    /** The box leaves: the roller gets their points back and the box opens somewhere else. */
    private static void move(GameManager.Active game, MysteryBoxBlock.Entity box) {
        State state = state(game);
        BlockPos from = box.getBlockPos();
        box.roller().ifPresent(id -> game.game().refund(id, box.paid()));
        box.idle();
        setActive(game.level(), from, false);
        List<BlockPos> others = new ArrayList<>(locations(game));
        others.remove(from);
        if (others.isEmpty()) {
            state.home = Optional.of(from);
            activate(game, from);
            return;
        }
        BlockPos to = others.get(game.level().getRandom().nextInt(others.size()));
        state.home = Optional.of(to);
        state.usesSinceMove = 0;
        activate(game, to);
        game.world().announce("The Mystery Box moved");
        game.game().events().emit(new GameEvent.MysteryBoxMoved(from, to));
    }

    /** The box locations in the game's map that still have a box block. */
    public static List<BlockPos> locations(GameManager.Active game) {
        return game.game().map().map().features(FeatureKind.MYSTERY_BOX).stream()
                .filter(pos -> game.level().getBlockState(pos).getBlock() instanceof MysteryBoxBlock)
                .toList();
    }

    private static void activate(GameManager.Active game, BlockPos pos) {
        setActive(game.level(), pos, true);
        MysteryBoxBlock.entity(game.level(), pos).ifPresent(box -> refreshPrice(game, box));
    }

    private static void setActive(ServerLevel level, BlockPos pos, boolean active) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MysteryBoxBlock && state.getValue(MysteryBoxBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(MysteryBoxBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    /** The pool as built for this game: built on first use, so it sees the map's wall-buys as they are when play starts. */
    static List<BoxPool.Weighted> pool(GameManager.Active game, ResourceLocation id) {
        return state(game).pools.computeIfAbsent(id, key -> BoxPools.get(key)
                .map(pool -> pool.build(TaczBridge.allGuns(), wallBuyItems(game)))
                .orElse(List.of()));
    }

    private static List<ItemSpec> wallBuyItems(GameManager.Active game) {
        return game.game().map().map().features(FeatureKind.WALL_BUY).stream()
                .flatMap(pos -> WallBuyBlock.entity(game.level(), pos).stream())
                .map(entity -> entity.config().item())
                .toList();
    }

    /** What the box flicks through while rolling: a sample of the pool, for show. */
    private static List<ItemStack> reel(List<BoxPool.Weighted> pool, RandomSource random) {
        List<ItemStack> reel = new ArrayList<>();
        for (int i = 0; i < Math.min(REEL_SIZE, pool.size() * 2); i++) {
            pool.get(random.nextInt(pool.size())).item().build(0).ifPresent(reel::add);
        }
        return reel;
    }
}
