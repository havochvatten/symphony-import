package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.cli.ParseException;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.util.factory.Hints;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class Baseline {

    public BaselineVersion version;
    public Map<SymphonyCategory, BaselineComponent> components;

    private boolean metaIncomplete = false;
    private static final Hints g2h = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);

    public Baseline(BaselineVersion version) throws Exception {
        for(String path : version.tiffFilePaths().values()) {
            if (!new File(path).isFile()) {
                throw new Exception("File not found at path: " + path);
            }
        }
        components =
            Map.of(SymphonyCategory.ECOSYSTEM, new BaselineComponent(),
                   SymphonyCategory.PRESSURE, new BaselineComponent());

        this.version = version;
    }

    public static GridCoverage2D readTiff(String path) throws IOException {
        File file = new File(path);
        return GridFormatFinder.findFormat(file)
                .getReader(file, g2h).read(null);
    }

    // WIP
    public void collectComponents(SymphonyBand[] ecoBands, SymphonyBand[] pressureBands) throws Exception {
        SymphonyBand[][] bands = new SymphonyBand[][]{ ecoBands, pressureBands };

        for(Map.Entry<SymphonyCategory, String> pathEntry : version.tiffFilePaths().entrySet()) {
            SymphonyCategory category = pathEntry.getKey();
            int cOrdinal = category.ordinal();

            GridCoverage2D coverage = readTiff(pathEntry.getValue());
            GridSampleDimension[] c_bands = coverage.getSampleDimensions();

            if (c_bands.length > coverage.getSampleDimensions().length) {
                throw new RuntimeException("Inconsistent data"); // TODO: Supply verbose instruction on how to fix.
            }

            metaIncomplete |= bands[cOrdinal].length < coverage.getSampleDimensions().length;

            for (int ci = 0; ci < bands[cOrdinal].length; ++ci) {
                SymphonyBand band = bands[cOrdinal][ci];

                band.setDefaultLanguage(this.version.getLocale());
                components.get(category).bands.put(band.getBandNumber(), band);
            }
        }
    }

    // WIP
    public void collectBandMeta(SymphonyCategory category, MetaValue[] componentMeta) throws Exception {
        if (components.get(category) == null) {
            throw new ParseException("Unrecognized category"); // kan vara redundant
        }

        for (SymphonyBand band : components.get(category).bands.values()) {
            MetaValue[] bandMeta = Arrays.stream(componentMeta).filter(mv -> mv.getBandId() == band.getId())
                                    .toArray(MetaValue[]::new);
            for (MetaValue mv : bandMeta) {
                band.setMetaValue(mv.getLanguage(), mv);
            }
        }
    }

    public boolean isMetaIncomplete() {
        return metaIncomplete;
    }

    public BaselineVersion getVersion() {
        return version;
    }

    public Map<SymphonyCategory, BaselineComponent> getComponents() {
        return components;
    }

    public Collection<SymphonyBand> bandsByCategory(SymphonyCategory category) {
        return components.get(category).bands.values();
    }
}
