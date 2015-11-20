package no.ecc.vectortile;

public class VectorTileEncoderBuilder {

    private int extent = EncoderDefaults.DEFAULT_EXTENT;
    private int clipBuffer = EncoderDefaults.DEFAULT_CLIP_BUFFER;
    private int polygonClipBuffer = EncoderDefaults.DEFAULT_POLYGON_CLIP_BUFFER;
    private boolean autoScale = EncoderDefaults.DEFAULT_AUTO_SCALE;

    public VectorTileEncoderBuilder withExtent(final int value) {
        this.extent = value;
        return this;
    }

    public VectorTileEncoderBuilder withClipBuffer(final int value) {
        this.clipBuffer = value;
        return this;
    }

    public VectorTileEncoderBuilder withPolygonClipBuffer(final int value) {
        this.polygonClipBuffer = value;
        return this;
    }

    public VectorTileEncoderBuilder withAutoScale(final boolean value) {
        this.autoScale = value;
        return this;
    }

    public VectorTileEncoder build() {
        return new VectorTileEncoder(extent, clipBuffer, polygonClipBuffer, autoScale);
    }
}
