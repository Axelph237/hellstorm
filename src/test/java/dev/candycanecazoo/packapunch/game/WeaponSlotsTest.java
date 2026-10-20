package dev.candycanecazoo.packapunch.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponSlotsTest {
    @Test
    void belowTheLimitAGunGoesIntoTheInventory() {
        assertEquals(new WeaponSlots.Plan.Add(), WeaponSlots.plan(1, 2, true, 0, true));
        assertEquals(new WeaponSlots.Plan.Add(), WeaponSlots.plan(0, 2, false, 3, true));
        assertEquals(new WeaponSlots.Plan.Refuse(WeaponSlots.FULL), WeaponSlots.plan(1, 2, false, 0, false), "no room even below the limit");
    }

    @Test
    void atTheLimitTheHeldGunIsSwappedOut() {
        assertEquals(new WeaponSlots.Plan.Replace(4), WeaponSlots.plan(2, 2, true, 4, false));
        assertEquals(new WeaponSlots.Plan.Replace(0), WeaponSlots.plan(3, 2, true, 0, true), "over the limit (Mule Kick lost) still swaps");
    }

    @Test
    void atTheLimitWithoutAGunInHandIsRefused() {
        assertEquals(new WeaponSlots.Plan.Refuse(WeaponSlots.SWAP), WeaponSlots.plan(2, 2, false, 0, true));
    }

    @Test
    void muleKickRaisesTheLimit() {
        assertEquals(new WeaponSlots.Plan.Add(), WeaponSlots.plan(2, 3, false, 0, true));
    }
}
