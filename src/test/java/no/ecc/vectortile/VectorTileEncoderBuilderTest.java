package no.ecc.vectortile;

import junit.framework.Assert;
import junit.framework.TestCase;

public class VectorTileEncoderBuilderTest extends TestCase {

    public void testBuilderWithDefaults() {
        VectorTileEncoder vte = new VectorTileEncoderBuilder().build();
        Assert.assertEquals(EncoderDefaults.DEFAULT_EXTENT, vte.getExtent());
        Assert.assertEquals(TileEnvelopes.createTileEnvelope(EncoderDefaults.DEFAULT_CLIP_BUFFER, 256), vte.getClipGeometry());
        Assert.assertEquals(TileEnvelopes.createTileEnvelope(EncoderDefaults.DEFAULT_POLYGON_CLIP_BUFFER, 256), vte.getPolygonClipGeometry());
        Assert.assertTrue(vte.isAutoScale());
    }

    public void testBuilder() {
        VectorTileEncoder vte = new VectorTileEncoderBuilder().withExtent(2048).withClipBuffer(4).withPolygonClipBuffer(16).withAutoScale(false).build();
        Assert.assertEquals(2048, vte.getExtent());
        Assert.assertEquals(TileEnvelopes.createTileEnvelope(4, 2048), vte.getClipGeometry());
        Assert.assertEquals(TileEnvelopes.createTileEnvelope(16, 2048), vte.getPolygonClipGeometry());
        Assert.assertFalse(vte.isAutoScale());
    }
}
