package se.havochvatten.symphonyconfig.setup.model;

import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.havochvatten.symphonyconfig.TestBase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoTiffTest {

    private static final String BASELINE_RESOURCES = "src/test/resources/baseline/";

    private static GeoTiff fixture(String name) throws ParseException {
        return GeoTiff.read(BASELINE_RESOURCES + name);
    }

    @Test
    void countsTheBandsOfABaselineRaster() throws ParseException {
        assertEquals(4, GeoTiff.read(TestBase.TEST_TIFF_E_PATH).bandCount());
    }

    @Test
    void countsTheBandsOfATiledCompressedRaster() throws ParseException {
        assertEquals(13, fixture("geotiff-13-bands-tiled-deflate.tif").bandCount());
    }

    @Test
    void countsTheBandsOfABigTiff() throws ParseException {
        assertEquals(5, fixture("geotiff-5-bands-bigtiff.tif").bandCount());
    }

    @Test
    void countsTheBandsOfTheRasterRatherThanItsInternalMask() throws ParseException {
        assertEquals(3, fixture("geotiff-3-bands-internal-mask.tif").bandCount());
    }

    @Test
    void readsTheTagsWithoutReadingThePixelData() throws ParseException {
        // Baseline rasters can be gigabytes, so pixel data must not be loaded to count bands
        assertEquals(4, fixture("geotiff-4-bands-pixel-data-missing.tif").bandCount());
    }

    @Test
    void countsOneBandWhenSamplesPerPixelIsAbsent() throws ParseException {
        // TIFF 6.0: SamplesPerPixel defaults to 1
        assertEquals(1, fixture("no-samples-per-pixel.tiff").bandCount());
    }

    @Test
    void namesTheFileWhenItIsNotATiff() {
        ParseException e = assertThrows(ParseException.class, () -> fixture("not-a-tiff.png"));
        assertTrue(e.getMessage().contains("not-a-tiff.png"), e.getMessage());
    }

    @Test
    void namesTheFileWhenItIsEmpty(@TempDir Path dir) throws IOException {
        String empty = Files.createFile(dir.resolve("empty.tif")).toString();

        ParseException e = assertThrows(ParseException.class, () -> GeoTiff.read(empty));
        assertTrue(e.getMessage().contains("empty.tif"), e.getMessage());
    }

    @Test
    void acceptsARasterWithACrsAndGeoreferencing() {
        assertDoesNotThrow(() -> GeoTiff.read(TestBase.TEST_TIFF_E_PATH).validateGeoreferencing());
    }

    @Test
    void acceptsGeoreferencingByModelTransformation() {
        assertDoesNotThrow(() -> fixture("geotiff-rotated-model-transformation.tif").validateGeoreferencing());
    }

    @Test
    void rejectsARasterWithoutACrs() throws ParseException {
        // Symphony would read it as WGS84 and calculate on the wrong area
        GeoTiff raster = fixture("geotiff-georeferencing-without-crs.tif");

        ParseException e = assertThrows(ParseException.class, raster::validateGeoreferencing);
        assertTrue(e.getMessage().contains("geotiff-georeferencing-without-crs.tif"), e.getMessage());
        assertTrue(e.getMessage().contains("coordinate reference system"), e.getMessage());
    }

    @Test
    void rejectsARasterWithoutGeoreferencing() throws ParseException {
        // Symphony cannot read it at all
        GeoTiff raster = fixture("geotiff-crs-without-georeferencing.tif");

        ParseException e = assertThrows(ParseException.class, raster::validateGeoreferencing);
        assertTrue(e.getMessage().contains("geotiff-crs-without-georeferencing.tif"), e.getMessage());
        assertTrue(e.getMessage().contains("georeferencing"), e.getMessage());
    }

    @Test
    void rejectsGeoreferencingInFilesNextToTheRaster() throws ParseException {
        GeoTiff raster = fixture("tiff-with-world-file.tif");

        ParseException e = assertThrows(ParseException.class, raster::validateGeoreferencing);
        assertTrue(e.getMessage().contains("gdal_translate"), e.getMessage());
    }
}
