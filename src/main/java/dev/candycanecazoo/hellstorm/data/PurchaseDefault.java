package dev.candycanecazoo.hellstorm.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import dev.candycanecazoo.hellstorm.util.Codecs;

import java.util.Optional;

/**
 * What a wall-buy or vending machine starts with when it's set to sell one item: its price, what buying it again does,
 * and what refilling a plain and an upgraded copy cost. Left out, the ammo costs are half the price and
 * {@link WallBuy#DEFAULT_PAP_AMMO_COST}, as on a wall-buy.
 */
public record PurchaseDefault(int cost, WallBuy.Repurchase repurchase, int ammoCost, int papAmmoCost) {
    public static final Codec<PurchaseDefault> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("cost").forGetter(PurchaseDefault::cost),
            WallBuy.Repurchase.CODEC.fieldOf("repurchase").forGetter(PurchaseDefault::repurchase),
            Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "ammo_cost").forGetter(p -> Optional.of(p.ammoCost())),
            Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "pap_ammo_cost", WallBuy.DEFAULT_PAP_AMMO_COST).forGetter(PurchaseDefault::papAmmoCost)
    ).apply(i, (cost, repurchase, ammo, papAmmo) -> new PurchaseDefault(cost, repurchase, ammo.orElse(cost / 2), papAmmo)));
}
