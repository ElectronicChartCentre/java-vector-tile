package no.ecc.vectortile;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.util.Stopwatch;

/**
 * A utility to help benchmark the building performance of large Point based vector tiles.
 * This adds 512x512 "pixels" of data 100 times, allowing a profiler to connect and determine where the bottlenecks
 * are.
 */
public class VectorTileEncoderPerf {

  public static void main(String[] args) {
    for (int i=0; i<100; i++) {
      run();
    }
  }

  private static void run() {
    int tileSize = 512;
    VectorTileEncoder encoder = new VectorTileEncoder(tileSize, 0, false);
    GeometryFactory geometryFactory = new GeometryFactory();
    Stopwatch sw = new Stopwatch();
    int features = 0;
    for (int x=0; x<tileSize; x++) {
      for (int y=0; y<tileSize; y++) {
        Geometry geom = geometryFactory.createPoint(new Coordinate(x, y));
        encoder.addFeature("layer1", new HashMap<String, Object>(),geom);
        features++;
      }
    }
    System.out.println("Added " + features + " in " +  sw.getTime() + "msecs");
  }
}
