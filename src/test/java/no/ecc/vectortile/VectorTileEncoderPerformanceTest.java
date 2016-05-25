package no.ecc.vectortile;

import java.util.Collections;
import java.util.Map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.util.Stopwatch;

import junit.framework.TestCase;

public class VectorTileEncoderPerformanceTest extends TestCase {

    /**
     * A utility to help benchmark the building performance of large Point based
     * vector tiles. This adds 512x512 "pixels" of data 100 times, allowing a
     * profiler to connect and determine where the bottlenecks are.
     */
    public void testManyPoints() {
        int tileSize = 512;
        Map<String, Object> empty = Collections.emptyMap();
        for (int i = 0; i < 100; i++) {
            VectorTileEncoder encoder = new VectorTileEncoder(tileSize, 0, false);
            GeometryFactory geometryFactory = new GeometryFactory();
            Stopwatch sw = new Stopwatch();
            int features = 0;
            for (int x = 0; x < tileSize; x++) {
                for (int y = 0; y < tileSize; y++) {
                    Geometry geom = geometryFactory.createPoint(new Coordinate(x, y));
                    encoder.addFeature("layer1", empty, geom);
                    features++;
                }
            }
            System.out.println("Added " + features + " in " + sw.getTime() + "msecs");
        }
    }

}
