package com.badbones69.crazyenchantments.paper.api.economy;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyPointsTest {

    @Test
    void spendsExactBalanceAndSynchronizesLegacyCounter() {
        PointsPlayer player = new PointsPlayer(351, 900);

        assertTrue(CurrencyAPI.changeExperiencePoints(player.api, -351));
        assertEquals(0, player.spendable);
        assertEquals(0, player.collected);
    }

    @Test
    void rejectsInsufficientSpendablePointsEvenWhenLifetimeCounterIsLarger() {
        PointsPlayer player = new PointsPlayer(351, 900);

        assertFalse(CurrencyAPI.changeExperiencePoints(player.api, -352));
        assertEquals(351, player.spendable);
        assertEquals(900, player.collected);
    }

    @Test
    void passesExactLevelBoundaryToPaperOnWithdrawalAndDeposit() {
        // 352 points is the start of level 16; Paper owns the level/progress reconstruction.
        PointsPlayer player = new PointsPlayer(353, 900);

        assertTrue(CurrencyAPI.changeExperiencePoints(player.api, -1));
        assertEquals(352, player.spendable);
        assertEquals(352, player.collected);
        assertTrue(CurrencyAPI.changeExperiencePoints(player.api, -1));
        assertEquals(351, player.spendable);
        assertTrue(CurrencyAPI.changeExperiencePoints(player.api, 1));
        assertEquals(352, player.spendable);
        assertEquals(352, player.collected);
    }

    @Test
    void rejectsOverflowWithoutChangingEitherCounter() {
        PointsPlayer player = new PointsPlayer(1, 900);

        assertFalse(CurrencyAPI.changeExperiencePoints(player.api, Integer.MAX_VALUE));
        assertEquals(1, player.spendable);
        assertEquals(900, player.collected);
    }

    @Test
    void transfersOnlySpendablePointsEvenWhenLegacyCountersDisagree() {
        PointsPlayer from = new PointsPlayer(3, 0);
        PointsPlayer to = new PointsPlayer(11, 900);

        assertEquals(3, CurrencyAPI.transferExperiencePoints(from.api, to.api, 9));
        assertEquals(0, from.spendable);
        assertEquals(14, to.spendable);
        assertEquals(0, from.collected);
        assertEquals(14, to.collected);
        assertEquals(0, CurrencyAPI.transferExperiencePoints(from.api, to.api, 9));
        assertEquals(14, to.spendable);
    }

    @Test
    void capsTransferAtReceiverCapacityWithoutLosingPoints() {
        PointsPlayer from = new PointsPlayer(8, 8);
        PointsPlayer to = new PointsPlayer(Integer.MAX_VALUE - 2, 0);

        assertEquals(2, CurrencyAPI.transferExperiencePoints(from.api, to.api, 9));
        assertEquals(0, CurrencyAPI.transferExperiencePoints(from.api, to.api, 9));
        assertEquals(6, from.spendable);
        assertEquals(Integer.MAX_VALUE, to.spendable);
    }

    @Test
    void removesPartialCreditBeforeRestoringDebitWhenRecipientSetterThrows() {
        PointsPlayer from = new PointsPlayer(8, 900);
        PointsPlayer to = new PointsPlayer(11, 700);
        to.failAfterNextProgressWrite = true;

        assertThrows(IllegalStateException.class, () -> CurrencyAPI.transferExperiencePoints(from.api, to.api, 5));
        assertEquals(8, from.spendable);
        assertEquals(11, to.spendable);
        assertEquals(900, from.collected);
        assertEquals(700, to.collected);
    }

    private static final class PointsPlayer {
        private int spendable;
        private int collected;
        private boolean failAfterNextProgressWrite;
        private final Player api = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "calculateTotalExperiencePoints" -> this.spendable;
                    case "getTotalExperience" -> this.collected;
                    case "setExperienceLevelAndProgress" -> {
                        this.spendable = (int) args[0];
                        if (this.failAfterNextProgressWrite) {
                            this.failAfterNextProgressWrite = false;
                            throw new IllegalStateException("XP setter failed after mutation");
                        }
                        yield null;
                    }
                    case "setTotalExperience" -> {
                        this.collected = (int) args[0];
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected Player call: " + method.getName());
                });

        private PointsPlayer(int spendable, int collected) {
            this.spendable = spendable;
            this.collected = collected;
        }
    }
}
