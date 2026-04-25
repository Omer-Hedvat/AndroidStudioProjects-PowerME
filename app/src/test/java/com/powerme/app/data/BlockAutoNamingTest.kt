package com.powerme.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockAutoNamingTest {

    // STRENGTH
    @Test fun strength_returnsStrength() {
        assertEquals("Strength", autoBlockName(BlockType.STRENGTH))
    }

    // AMRAP
    @Test fun amrap_defaultMinutes_returnsDefault() {
        assertEquals("AMRAP 12min", autoBlockName(BlockType.AMRAP, durationMinutes = 12))
    }

    @Test fun amrap_oneMinute() {
        assertEquals("AMRAP 1min", autoBlockName(BlockType.AMRAP, durationMinutes = 1))
    }

    @Test fun amrap_thirtyMinutes() {
        assertEquals("AMRAP 30min", autoBlockName(BlockType.AMRAP, durationMinutes = 30))
    }

    // RFT
    @Test fun rft_noCapReturnsRoundsOnly() {
        assertEquals("5 RFT", autoBlockName(BlockType.RFT, rounds = 5))
    }

    @Test fun rft_withCapIncludesCap() {
        assertEquals("5 RFT / 25min cap", autoBlockName(BlockType.RFT, rounds = 5, capMinutes = 25))
    }

    @Test fun rft_capZeroIsNoCap() {
        assertEquals("3 RFT", autoBlockName(BlockType.RFT, rounds = 3, capMinutes = 0))
    }

    @Test fun rft_singleRound() {
        assertEquals("1 RFT", autoBlockName(BlockType.RFT, rounds = 1))
    }

    // EMOM
    @Test fun emom_60sIntervalIsEMOM() {
        assertEquals("EMOM 10min", autoBlockName(BlockType.EMOM, durationMinutes = 10, emomIntervalSec = 60))
    }

    @Test fun emom_120sIntervalIsE2MOM() {
        assertEquals("E2MOM 10min", autoBlockName(BlockType.EMOM, durationMinutes = 10, emomIntervalSec = 120))
    }

    @Test fun emom_180sIntervalIsE3MOM() {
        assertEquals("E3MOM 15min", autoBlockName(BlockType.EMOM, durationMinutes = 15, emomIntervalSec = 180))
    }

    @Test fun emom_300sIntervalIsE5MOM() {
        assertEquals("E5MOM 20min", autoBlockName(BlockType.EMOM, durationMinutes = 20, emomIntervalSec = 300))
    }

    @Test fun emom_90sIntervalIsE90sMOM() {
        assertEquals("E90sMOM 10min", autoBlockName(BlockType.EMOM, durationMinutes = 10, emomIntervalSec = 90))
    }

    // TABATA
    @Test fun tabata_defaultRoundsInName() {
        assertEquals("Tabata 8rds", autoBlockName(BlockType.TABATA, rounds = 8))
    }

    @Test fun tabata_customRounds() {
        assertEquals("Tabata 4rds", autoBlockName(BlockType.TABATA, rounds = 4))
    }

    @Test fun tabata_nullRoundsUsesDefault() {
        assertEquals("Tabata 8rds", autoBlockName(BlockType.TABATA))
    }
}
