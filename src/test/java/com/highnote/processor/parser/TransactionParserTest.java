package com.highnote.processor.parser;

import com.highnote.processor.model.Transaction;
import com.highnote.processor.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class TransactionParserTest {

    private final TransactionParser parser = new TransactionParser();

    @Test
    void parsesValidLine() {
        Transaction txn = parser.parse("AUTH,card1,t1,30.00");

        assertThat(txn).isEqualTo(
                new Transaction(TransactionType.AUTH, "card1", "t1", new BigDecimal("30.00")));
    }

    @Test
    void trimsWhitespaceAndIgnoresTypeCase() {
        Transaction txn = parser.parse(" load , card1 , l1 , 5 ");

        assertThat(txn.type()).isEqualTo(TransactionType.LOAD);
        assertThat(txn.cardId()).isEqualTo("card1");
        assertThat(txn.amount()).isEqualByComparingTo("5");
    }

    @Test
    void acceptsWindowsLineEndingAndByteOrderMark() {
        assertThat(parser.parse("LOAD,card1,l1,5\r").amount()).isEqualByComparingTo("5");
        assertThat(parser.parse("\uFEFFLOAD,card1,l1,5").type()).isEqualTo(TransactionType.LOAD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.5", "10.05", "999999999999999.99"})
    void acceptsValidAmounts(String amount) {
        assertThat(parser.parse("LOAD,card1,l1," + amount).amount())
                .isEqualByComparingTo(amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AUTH,card1,t1",                // too few fields
            "AUTH,card1,t1,1,extra",        // too many fields
            "AUTH,card1,t1,1,",             // trailing comma
            "AUTH,card1,t1,",               // empty amount
            "AUTH,,t1,1",                   // missing card id
            "AUTH,card1,,1",                // missing txn id
            "AUTH,card1,t1,abc",            // non-numeric amount
            "AUTH,card1,t1,-5",             // negative amount
            "AUTH,card1,t1,+5",             // explicit sign
            "AUTH,card1,t1,1.234",          // sub-cent precision
            "AUTH,card1,t1,1e3",            // exponent notation
            "AUTH,card1,t1,.5",             // no leading digit
            "AUTH,card1,t1,5.",             // trailing dot
            "AUTH,card1,t1,NaN",
            "AUTH,card1,t1,1000000000000000",   // 16 integer digits: over the sanity bound
            "BOGUS,card1,t1,1",             // unknown type
            "   "                           // blank
    })
    void rejectsMalformedLines(String line) {
        assertThatThrownBy(() -> parser.parse(line))
                .isInstanceOf(InvalidTransactionException.class);
    }

    /**
     * Lightweight fuzz test: random garbage must either parse or fail with
     * InvalidTransactionException, never NPE, NumberFormatException, etc.
     * Fixed seed so any failure can be reproduced.
     */
    @Test
    void randomInputNeverThrowsUnexpectedException() {
        Random random = new Random(42);
        String alphabet = "AUTHLOADCLEARVSIM,,,,0123456789.-+e \t\uFEFF";

        for (int i = 0; i < 20_000; i++) {
            StringBuilder line = new StringBuilder();
            int length = random.nextInt(30);
            for (int j = 0; j < length; j++) {
                line.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            try {
                parser.parse(line.toString());
            } catch (InvalidTransactionException expected) {
                // fine
            } catch (RuntimeException e) {
                fail("Unexpected " + e + " for input [" + line + "]");
            }
        }
    }
}
