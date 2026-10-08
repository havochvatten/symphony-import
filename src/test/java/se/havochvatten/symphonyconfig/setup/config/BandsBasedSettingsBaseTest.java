package se.havochvatten.symphonyconfig.setup.config;

import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.TestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static se.havochvatten.symphonyconfig.setup.config.BandsBasedSettingsBase.countBands;

class BandsBasedSettingsBaseTest {

    private static final String BASELINE_RESOURCES = "src/test/resources/baseline/";

    @Test
    void countsEcosystemBandsInBaselineE() throws ParseException {
        assertEquals(4, countBands(TestBase.TEST_TIFF_E_PATH));
    }

    @Test
    void throwsWhenFileIsNotATiff() {
        assertThrows(ParseException.class,
            () -> countBands(BASELINE_RESOURCES + "not-a-tiff.png"));
    }

    @Test
    void throwsWhenTiffHasNoSamplesPerPixelTag() {
        assertThrows(ParseException.class,
            () -> countBands(BASELINE_RESOURCES + "no-samples-per-pixel.tiff"));
    }
}
