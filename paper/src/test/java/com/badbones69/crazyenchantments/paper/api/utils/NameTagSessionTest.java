package com.badbones69.crazyenchantments.paper.api.utils;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session is the write guard: a confirmation may only succeed once, on the
 * same gear it was started on, and the preview must keep the Transmog count
 * suffix instead of dropping or doubling it.
 */
class NameTagSessionTest {

    private static final UUID TOKEN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String SUFFIX = " &7[&6&n%amount%&7]";

    @Test
    void submittedNameIsStoredForPreview() {
        final NameTagSession session = session();

        assertEquals(NameTagSession.Result.OK, session.submit(TOKEN, "&cBlade &7[&6&n3&7]"));

        assertEquals(NameTagSession.Phase.AWAITING_CONFIRM, session.getPhase());
        assertEquals("&cBlade &7[&6&n3&7]", session.getPendingName());
    }

    @Test
    void repeatConfirmationOnlyClaimsOnce() {
        final NameTagSession session = session();

        session.submit(TOKEN, "&cBlade");

        assertEquals(NameTagSession.Result.OK, session.claim(TOKEN, "gear"));
        assertEquals(NameTagSession.Result.ALREADY_CLAIMED, session.claim(TOKEN, "gear"));
        assertTrue(session.isClaimed());
    }

    @Test
    void staleGearCannotBeConfirmed() {
        final NameTagSession session = session();

        session.submit(TOKEN, "&cBlade");

        assertEquals(NameTagSession.Result.STALE, session.claim(TOKEN, "changed"));

        assertFalse(session.isClaimed());
        assertEquals(NameTagSession.Result.OK, session.claim(TOKEN, "gear"));
    }

    @Test
    void wrongTokenChangesNothing() {
        final NameTagSession session = session();

        assertEquals(NameTagSession.Result.BAD_TOKEN, session.submit(UUID.randomUUID(), "&cBlade"));
        assertNull(session.getPendingName());
        assertEquals(NameTagSession.Phase.AWAITING_NAME, session.getPhase());

        assertEquals(NameTagSession.Result.BAD_TOKEN, session.claim(UUID.randomUUID(), "gear"));
    }

    @Test
    void confirmationNeedsANameFirst() {
        assertEquals(NameTagSession.Result.NOT_READY, session().claim(TOKEN, "gear"));
    }

    @Test
    void blankOrColourOnlyInputIsRejected() {
        assertNull(NameTagSession.clean(null));
        assertNull(NameTagSession.clean("   "));
        assertNull(NameTagSession.clean("&c&l"));
        assertEquals("&cBlade", NameTagSession.clean("  &cBlade  "));
    }

    @Test
    void overlyLongNamesAreRejected() {
        final String allowed = "a".repeat(NameTagSession.MAX_NAME_LENGTH);
        final String tooLong = "a".repeat(NameTagSession.MAX_NAME_LENGTH + 1);

        assertFalse(NameTagSession.tooLong(allowed));
        assertTrue(NameTagSession.tooLong(tooLong));
    }

    @Test
    void suffixIsCarriedFromTheItem() {
        assertEquals("&cBlade &7[&6&n3&7]",
                NameTagSession.withSuffix("&cBlade", "&dEpic Sword &7[&6&n3&7]", SUFFIX, true));
    }

    @Test
    void enteredSuffixIsNotStacked() {
        assertEquals("&cBlade &7[&6&n3&7]",
                NameTagSession.withSuffix("&cBlade &7[&6&n3&7]", "&dEpic Sword &7[&6&n3&7]", SUFFIX, true));
    }

    @Test
    void suffixIsLeftOffWhenToggleIsOff() {
        assertEquals("&cBlade",
                NameTagSession.withSuffix("&cBlade", "&dEpic Sword &7[&6&n3&7]", SUFFIX, false));
    }

    @Test
    void itemWithoutASuffixKeepsTheEnteredName() {
        assertEquals("&cBlade",
                NameTagSession.withSuffix("&cBlade", "&dEpic Sword", SUFFIX, true));
    }

    private static NameTagSession session() {
        return new NameTagSession(TOKEN, 10, "gear");
    }
}
