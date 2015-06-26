/*****************************************************************
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/
package no.ecc.vectortile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import junit.framework.TestCase;
import no.ecc.vectortile.VectorTileDecoder.Feature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;

public class VectorTileDecoderTest extends TestCase {

    private GeometryFactory gf = new GeometryFactory();

    public void testZigZagDecode() {
        assertEquals(0, VectorTileDecoder.zigZagDecode(0));
        assertEquals(-1, VectorTileDecoder.zigZagDecode(1));
        assertEquals(1, VectorTileDecoder.zigZagDecode(2));
        assertEquals(-2, VectorTileDecoder.zigZagDecode(3));
    }
    
    public void testWithoutScaling() throws IOException {
        Coordinate c = new Coordinate(2, 3);
        Geometry geometry = gf.createPoint(c);
        
        Coordinate c2 = new Coordinate(4, 6);
        Geometry geometry2 = gf.createPoint(c2);
        
        VectorTileEncoder e = new VectorTileEncoder(512);
        e.addFeature("layer", Collections.EMPTY_MAP, geometry);
        byte[] encoded = e.encode();

        VectorTileDecoder d = new VectorTileDecoder();
        d.setAutoScale(false);

        assertEquals(geometry2, d.decode(encoded, "layer").iterator().next().getGeometry());
    }

    public void testPoint() throws IOException {
        Coordinate c = new Coordinate(2, 3);
        Geometry geometry = gf.createPoint(c);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("hello", 123);
        String layerName = "layer";

        VectorTileEncoder e = new VectorTileEncoder(512);
        e.addFeature(layerName, attributes, geometry);
        byte[] encoded = e.encode();

        VectorTileDecoder d = new VectorTileDecoder();
        assertEquals(1, d.decode(encoded).getLayerNames().size());
        assertEquals(layerName, d.decode(encoded).getLayerNames().iterator().next());

        assertEquals(attributes, d.decode(encoded, layerName).iterator().next().getAttributes());
        assertEquals(geometry, d.decode(encoded, layerName).iterator().next().getGeometry());
    }

    public void testMultiPoint() throws IOException {
        Coordinate c1 = new Coordinate(2, 3);
        Coordinate c2 = new Coordinate(3, 4);
        Geometry geometry = gf.createMultiPoint(new Coordinate[] { c1, c2 });
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("hello", 123);
        String layerName = "layer";

        VectorTileEncoder e = new VectorTileEncoder(512);
        e.addFeature(layerName, attributes, geometry);
        byte[] encoded = e.encode();

        VectorTileDecoder d = new VectorTileDecoder();
        assertEquals(1, d.decode(encoded).getLayerNames().size());
        assertEquals(layerName, d.decode(encoded).getLayerNames().iterator().next());

        assertEquals(attributes, d.decode(encoded, layerName).asList().get(0).getAttributes());
        assertEquals(geometry, d.decode(encoded, layerName).asList().get(0).getGeometry());
    }

    public void testLineString() throws IOException {
        Coordinate c1 = new Coordinate(1, 2);
        Coordinate c2 = new Coordinate(10, 20);
        Coordinate c3 = new Coordinate(100, 200);
        Geometry geometry = gf.createLineString(new Coordinate[] { c1, c2, c3 });

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("aa", "bb");
        attributes.put("cc", "bb");

        String layerName = "layer";

        VectorTileEncoder e = new VectorTileEncoder(512);
        e.addFeature(layerName, attributes, geometry);
        byte[] encoded = e.encode();

        VectorTileDecoder d = new VectorTileDecoder();
        assertEquals(1, d.decode(encoded).getLayerNames().size());
        assertEquals(layerName, d.decode(encoded).getLayerNames().iterator().next());

        assertEquals(attributes, d.decode(encoded, layerName).asList().get(0).getAttributes());
        assertEquals(geometry, d.decode(encoded, layerName).asList().get(0).getGeometry());

    }

    public void testMultiLineString() throws IOException {
        Coordinate c1 = new Coordinate(1, 2);
        Coordinate c2 = new Coordinate(5, 6);
        Coordinate c3 = new Coordinate(7, 8);
        Coordinate c4 = new Coordinate(8, 10);
        Coordinate c5 = new Coordinate(11, 12);
        LineString ls1 = gf.createLineString(new Coordinate[] { c1, c2, c3 });
        LineString ls2 = gf.createLineString(new Coordinate[] { c4, c5 });
        Geometry geometry = gf.createMultiLineString(new LineString[] { ls1, ls2 });

        Map<String, Object> attributes = new HashMap<String, Object>();

        String layerName = "layer";

        VectorTileEncoder e = new VectorTileEncoder(512);
        e.addFeature(layerName, attributes, geometry);
        byte[] encoded = e.encode();

        VectorTileDecoder d = new VectorTileDecoder();
        assertEquals(1, d.decode(encoded).getLayerNames().size());
        assertEquals(layerName, d.decode(encoded).getLayerNames().iterator().next());

        assertEquals(attributes, d.decode(encoded, layerName).asList().get(0).getAttributes());
        assertEquals(geometry, d.decode(encoded, layerName).asList().get(0).getGeometry());

    }

    public void testPolygon() throws IOException {
        LinearRing shell = gf.createLinearRing(new Coordinate[] { new Coordinate(10, 10), new Coordinate(10, 20),
                new Coordinate(20, 20), new Coordinate(20, 10), new Coordinate(10, 10) });
        assertTrue(shell.isClosed());
        assertTrue(shell.isValid());

        Geometry geometry = gf.createPolygon(shell, new LinearRing[] {});
        assertTrue(geometry.isValid());

        Map<String, Object> attributes = new HashMap<String, Object>();

        String layerName = "layer";

        VectorTileEncoder e = new VectorTileEncoder(512);
        e.addFeature(layerName, attributes, geometry);
        byte[] encoded = e.encode();

        VectorTileDecoder d = new VectorTileDecoder();
        assertEquals(1, d.decode(encoded).getLayerNames().size());
        assertEquals(layerName, d.decode(encoded).getLayerNames().iterator().next());

        assertEquals(attributes, d.decode(encoded, layerName).asList().get(0).getAttributes());
        assertEquals(geometry, d.decode(encoded, layerName).asList().get(0).getGeometry());

    }

    public void testPolygonWithHole() throws IOException {
        LinearRing shell = gf.createLinearRing(new Coordinate[] { new Coordinate(10, 10), new Coordinate(10, 20),
                new Coordinate(20, 20), new Coordinate(20, 10), new Coordinate(10, 10) });
        assertTrue(shell.isClosed());
        assertTrue(shell.isValid());

        Geometry geometry = gf.createPolygon(shell, new LinearRing[] {});
        assertTrue(geometry.isValid());

        LinearRing hole = gf.createLinearRing(new Coordinate[] { new Coordinate(11, 11), new Coordinate(19, 11),
                new Coordinate(19, 19), new Coordinate(11, 19), new Coordinate(11, 11) });
        assertTrue(hole.isClosed());
        assertTrue(hole.isValid());

        assertTrue(geometry.contains(hole));

        geometry = gf.createPolygon(shell, new LinearRing[] { hole });
        assertTrue(geometry.isValid());

        Map<String, Object> attributes = new HashMap<String, Object>();

        String layerName = "layer";

        VectorTileEncoder e = new VectorTileEncoder(512);
        e.addFeature(layerName, attributes, geometry);
        byte[] encoded = e.encode();

        VectorTileDecoder d = new VectorTileDecoder();
        assertEquals(1, d.decode(encoded).getLayerNames().size());
        assertEquals(layerName, d.decode(encoded).getLayerNames().iterator().next());

        assertEquals(attributes, d.decode(encoded, layerName).asList().get(0).getAttributes());
        assertEquals(geometry.toText(), d.decode(encoded, layerName).asList().get(0).getGeometry().toText());
        assertEquals(geometry, d.decode(encoded, layerName).asList().get(0).getGeometry());

    }

    public void testExternal() throws IOException {
        // from
        // https://github.com/mapbox/vector-tile-js/tree/master/test/fixtures
        InputStream is = getClass().getResourceAsStream("/14-8801-5371.vector.pbf");
        assertNotNull(is);
        byte[] encoded = toBytes(is);

        VectorTileDecoder d = new VectorTileDecoder();

        d.decode(encoded)
                .getLayerNames()
                .equals(new HashSet<String>(Arrays.asList("landuse", "waterway", "water", "barrier_line", "building",
                        "landuse_overlay", "tunnel", "road", "bridge", "place_label", "water_label", "poi_label",
                        "road_label", "waterway_label")));

        assertEquals(558, d.decode(encoded, "poi_label").asList().size());

        Feature park = d.decode(encoded, "poi_label").asList().get(11);
        assertEquals("Mauerpark", park.getAttributes().get("name"));
        assertEquals("Park", park.getAttributes().get("type"));

        Geometry parkGeometry = park.getGeometry();
        assertTrue(parkGeometry instanceof Point);
        assertEquals(1, parkGeometry.getCoordinates().length);

        assertEquals(new Coordinate(3898.0, 1731.0), park.getExtent(), parkGeometry.getCoordinates()[0]);

        Feature building = d.decode(encoded, "building").asList().get(0);
        Geometry buildingGeometry = d.decode(encoded, "building").asList().get(0).getGeometry();
        assertNotNull(building);

        assertEquals(5, buildingGeometry.getCoordinates().length);
        assertEquals(new Coordinate(2039, -32), building.getExtent(), buildingGeometry.getCoordinates()[0]);
        assertEquals(new Coordinate(2035, -31), building.getExtent(), buildingGeometry.getCoordinates()[1]);
        assertEquals(new Coordinate(2032, -31), building.getExtent(), buildingGeometry.getCoordinates()[2]);
        assertEquals(new Coordinate(2032, -32), building.getExtent(), buildingGeometry.getCoordinates()[3]);
        assertEquals(new Coordinate(2039, -32), building.getExtent(), buildingGeometry.getCoordinates()[4]);

    }

    public void testBigTile() throws IOException {
        for (int i = 0; i < 10; i++) {
            System.gc();
            long memoryStart = Runtime.getRuntime().totalMemory();
            InputStream is = getClass().getResourceAsStream("/bigtile.vector.pbf");
            assertNotNull(is);
            VectorTileDecoder d = new VectorTileDecoder();
            for (Iterator<VectorTileDecoder.Feature> it = d.decode(toBytes(is)).iterator(); it.hasNext();) {
                it.next();
                if (!it.hasNext()) {
                    long memoryDiff = Runtime.getRuntime().totalMemory() - memoryStart;
                    System.out.println(memoryDiff / (1024 * 1024));
                }
            }
        }
    }

    private void assertEquals(Coordinate expected, int extent, Coordinate actual) {
        double scale = extent / 256.0;
        assertEquals(expected.x / scale, actual.x);
        assertEquals(expected.y / scale, actual.y);
    }

    private void assertEquals(Map<String, Object> expected, Map<String, Object> real) {
        assertEquals(expected.size(), real.size());
        for (Map.Entry<String, Object> e : expected.entrySet()) {
            String key = e.getKey();
            assertTrue(real.containsKey(key));
            Object expectedValue = e.getValue();
            Object realValue = real.get(key);

            if (expectedValue instanceof Number) {
                assertTrue(realValue instanceof Number);
                Number exp = (Number) expectedValue;
                Number rea = (Number) realValue;
                assertEquals(exp.intValue(), rea.intValue());
                assertEquals(exp.floatValue(), rea.floatValue(), 0.003);
                assertEquals(exp.doubleValue(), rea.doubleValue(), 0.003);
            } else {
                assertEquals(expectedValue.getClass(), realValue.getClass());
                assertEquals(expectedValue, realValue);
            }

        }
    }

    private static byte[] toBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int bytesRead = 0;
        while ((bytesRead = in.read(buf)) != -1) {
            baos.write(buf, 0, bytesRead);
        }
        return baos.toByteArray();
    }
    
}
