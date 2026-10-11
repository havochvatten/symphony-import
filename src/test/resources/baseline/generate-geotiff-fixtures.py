"""Generates the GeoTIFF edge-case fixtures used by GeoTiffTest.

Requires GDAL's Python bindings. Run from anywhere:

    python3 generate-geotiff-fixtures.py

The fixtures are written next to this script. They are a few pixels each, since the tests only
read their tags.
"""
import os

from osgeo import gdal, osr

gdal.UseExceptions()
# No .aux.xml files: the fixtures must hold everything themselves
gdal.SetConfigOption('GDAL_PAM_ENABLED', 'NO')
OUT = os.path.dirname(os.path.abspath(__file__))

srs = osr.SpatialReference()
srs.ImportFromEPSG(3035)
LAEA = srs.ExportToWkt()
GEOTRANSFORM = (4500000.0, 250.0, 0.0, 3800000.0, 0.0, -250.0)


def create(name, bands, options=(), geotransform=GEOTRANSFORM, crs=LAEA, size=4):
    path = os.path.join(OUT, name)
    ds = gdal.GetDriverByName('GTiff').Create(path, size, size, bands, gdal.GDT_Byte, list(options))
    if geotransform is not None:
        ds.SetGeoTransform(geotransform)
    if crs is not None:
        ds.SetProjection(crs)
    for band in range(1, bands + 1):
        ds.GetRasterBand(band).Fill(band)
    ds = None
    return path


create('geotiff-13-bands-tiled-deflate.tif', 13, ['TILED=YES', 'BLOCKXSIZE=16', 'BLOCKYSIZE=16', 'COMPRESS=DEFLATE'])
create('geotiff-5-bands-bigtiff.tif', 5, ['BIGTIFF=YES'])
create('geotiff-rotated-model-transformation.tif', 2,
       geotransform=(4500000.0, 250.0, 10.0, 3800000.0, 10.0, -250.0))
create('geotiff-georeferencing-without-crs.tif', 2, crs=None)
create('geotiff-crs-without-georeferencing.tif', 2, geotransform=None)

# A plain TIFF with its georeferencing in .tfw/.prj files next to it. Symphony could read it, but
# the tool rejects it: the rasters must carry their georeferencing themselves.
gdal.Translate(os.path.join(OUT, 'tiff-with-world-file.tif'),
               os.path.join(OUT, 'geotiff-5-bands-bigtiff.tif'), creationOptions=['PROFILE=BASELINE', 'TFW=YES'])
with open(os.path.join(OUT, 'tiff-with-world-file.prj'), 'w') as f:
    f.write(srs.ExportToWkt(['FORMAT=WKT1_ESRI']))

# An internal mask is stored as a second image with one sample per pixel
gdal.SetConfigOption('GDAL_TIFF_INTERNAL_MASK', 'YES')
ds = gdal.Open(create('geotiff-3-bands-internal-mask.tif', 3), gdal.GA_Update)
ds.CreateMaskBand(gdal.GMF_PER_DATASET)
ds.GetRasterBand(1).GetMaskBand().Fill(255)
ds = None
gdal.SetConfigOption('GDAL_TIFF_INTERNAL_MASK', None)

# Tags intact, pixel data cut off. GDAL writes the image directory before the pixel data.
complete = create('pixel-data-source.tmp.tif', 4, size=32)
ds = gdal.Open(complete)
first_block = min(int(ds.GetRasterBand(b).GetMetadataItem('BLOCK_OFFSET_0_0', 'TIFF')) for b in range(1, 5))
ds = None
with open(complete, 'rb') as f:
    head = f.read(first_block)
with open(os.path.join(OUT, 'geotiff-4-bands-pixel-data-missing.tif'), 'wb') as f:
    f.write(head)
os.remove(complete)
