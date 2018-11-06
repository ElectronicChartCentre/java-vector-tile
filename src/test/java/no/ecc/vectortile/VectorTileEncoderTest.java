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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import junit.framework.TestCase;
import no.ecc.vectortile.VectorTileDecoder.Feature;
import vector_tile.VectorTile;

public class VectorTileEncoderTest extends TestCase {

    private GeometryFactory gf = new GeometryFactory();

    public void testEncode() {
        VectorTileEncoder vtm = new VectorTileEncoder(256);

        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(8, 12));
        cs.add(new Coordinate(20, 34));
        Geometry geometry = gf.createLineString(cs.toArray(new Coordinate[cs.size()]));

        cs.add(new Coordinate(33, 72));
        Geometry geometry2 = gf.createLineString(cs.toArray(new Coordinate[cs.size()]));

        Map<String, Object> attributes = new HashMap<String, Object>();

        vtm.addFeature("DEPCNT", attributes, geometry);
        vtm.addFeature("DEPCNT", attributes, geometry2);

        byte[] encoded = vtm.encode();
        assertNotSame(0, encoded.length);

    }

    public void testToGeomType() {
        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(8, 12));
        cs.add(new Coordinate(20, 34));
        Geometry geometry = gf.createLineString(cs.toArray(new Coordinate[cs.size()]));
        assertEquals(VectorTile.Tile.GeomType.LINESTRING, VectorTileEncoder.toGeomType(geometry));
    }

    public void testCommands() {

        // Ex.: MoveTo(3, 6), LineTo(8, 12), LineTo(20, 34), ClosePath
        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(8, 12));
        cs.add(new Coordinate(20, 34));

        List<Integer> commands = new VectorTileEncoder(256).commands(cs.toArray(new Coordinate[cs.size()]), true);
        assertNotNull(commands);
        // Encoded as: [ 9 6 12 18 10 12 24 44 15 ]
        assertCommand(9, commands, 0);
        assertCommand(6, commands, 1);
        assertCommand(12, commands, 2);
        assertCommand(18, commands, 3);
        assertCommand(10, commands, 4);
        assertCommand(12, commands, 5);
        assertCommand(24, commands, 6);
        assertCommand(44, commands, 7);
        assertCommand(15, commands, 8);
        assertEquals(9, commands.size());

    }
    
    public void testPolygonCommands() {
        
        // https://github.com/mapbox/vector-tile-spec/blob/master/2.1/README.md

        // Ex.: MoveTo(3, 6), LineTo(8, 12), LineTo(20, 34), ClosePath
        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(8, 12));
        cs.add(new Coordinate(20, 34));
        cs.add(new Coordinate(3, 6));
        Polygon polygon = gf.createPolygon(cs.toArray(new Coordinate[cs.size()]));

        List<Integer> commands = new VectorTileEncoder(256).commands(polygon);
        assertNotNull(commands);
        // Encoded as: [ 9 6 12 18 10 12 24 44 15 ]
        assertCommand(9, commands, 0);
        assertCommand(6, commands, 1);
        assertCommand(12, commands, 2);
        assertCommand(18, commands, 3);
        assertCommand(10, commands, 4);
        assertCommand(12, commands, 5);
        assertCommand(24, commands, 6);
        assertCommand(44, commands, 7);
        assertCommand(15, commands, 8);
        assertEquals(9, commands.size());

    }


    public void testCommandsFilter() {

        // Ex.: MoveTo(3, 6), LineTo(8, 12), LineTo(20, 34), ClosePath
        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(8, 12));
        cs.add(new Coordinate(8, 12));
        cs.add(new Coordinate(20, 34));

        List<Integer> commands = new VectorTileEncoder(256).commands(cs.toArray(new Coordinate[cs.size()]), true);
        assertNotNull(commands);
        // Encoded as: [ 9 6 12 18 10 12 24 44 15 ]
        assertCommand(9, commands, 0);
        assertCommand(6, commands, 1);
        assertCommand(12, commands, 2);
        assertCommand(18, commands, 3);
        assertCommand(10, commands, 4);
        assertCommand(12, commands, 5);
        assertCommand(24, commands, 6);
        assertCommand(44, commands, 7);
        assertCommand(15, commands, 8);
        assertEquals(9, commands.size());

    }

    public void testPoint() {

        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));

        List<Integer> commands = new VectorTileEncoder(256).commands(cs.toArray(new Coordinate[cs.size()]), false);
        assertNotNull(commands);

        assertCommand(9, commands, 0);
        assertCommand(6, commands, 1);
        assertCommand(12, commands, 2);
        assertEquals(3, commands.size());

    }

    public void testMultiPoint() {

        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(5, 7));
        cs.add(new Coordinate(3, 2));

        List<Integer> commands = new VectorTileEncoder(256).commands(cs.toArray(new Coordinate[cs.size()]), false,
                true);
        assertNotNull(commands);

        assertCommand(17, commands, 0);
        assertCommand(10, commands, 1);
        assertCommand(14, commands, 2);
        assertCommand(3, commands, 3);
        assertCommand(9, commands, 4);
        assertEquals(5, commands.size());

    }

    public void testCCWPolygon() {

        // Exterior ring in counter-clockwise order.
        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(8, 12));
        cs.add(new Coordinate(20, 34));
        cs.add(new Coordinate(3, 6));

        Coordinate[] coordinates = cs.toArray(new Coordinate[cs.size()]);
        assertTrue(Orientation.isCCW(coordinates));

        Polygon polygon = gf.createPolygon(coordinates);

        List<Integer> commands = new VectorTileEncoder(256).commands(polygon);
        assertNotNull(commands);

        assertCommand(9, commands, 0);
        assertCommand(6, commands, 1);
        assertCommand(12, commands, 2);
        assertCommand(18, commands, 3);
        assertCommand(10, commands, 4);
        assertCommand(12, commands, 5);
        assertCommand(24, commands, 6);
        assertCommand(44, commands, 7);
        assertCommand(15, commands, 8);
        assertEquals(9, commands.size());
    }

    public void testCWPolygon() {

        // Exterior ring in clockwise order.
        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(20, 34));
        cs.add(new Coordinate(8, 12));
        cs.add(new Coordinate(3, 6));

        Coordinate[] coordinates = cs.toArray(new Coordinate[cs.size()]);
        assertFalse(Orientation.isCCW(coordinates));

        Polygon polygon = gf.createPolygon(coordinates);

        List<Integer> commands = new VectorTileEncoder(256).commands(polygon);
        assertNotNull(commands);

        assertCommand(9, commands, 0);
        assertCommand(6, commands, 1);
        assertCommand(12, commands, 2);
        assertCommand(18, commands, 3);
        assertCommand(10, commands, 4);
        assertCommand(12, commands, 5);
        assertCommand(24, commands, 6);
        assertCommand(44, commands, 7);
        assertCommand(15, commands, 8);
        assertEquals(9, commands.size());
    }

    public void testExtentWithScale() {

        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));

        List<Integer> commands = new VectorTileEncoder(512).commands(cs.toArray(new Coordinate[cs.size()]), false);
        assertNotNull(commands);

        assertCommand(9, commands, 0);
        assertCommand(12, commands, 1);
        assertCommand(24, commands, 2);
        assertEquals(3, commands.size());
    }

    public void testExtentWithoutScale() {

        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(6, 300));

        List<Integer> commands = new VectorTileEncoder(512, 8, false).commands(cs.toArray(new Coordinate[cs.size()]),
                false);
        assertNotNull(commands);

        assertCommand(9, commands, 0);
        assertCommand(12, commands, 1);
        assertCommand(600, commands, 2);
        assertEquals(3, commands.size());
    }

    public void testFourEqualPoints() {

        List<Coordinate> cs = new ArrayList<Coordinate>();
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(3, 6));
        cs.add(new Coordinate(3, 6));

        List<Integer> commands = new VectorTileEncoder(256).commands(cs.toArray(new Coordinate[cs.size()]), false);
        assertNotNull(commands);

        assertCommand(9, commands, 0);
        assertCommand(6, commands, 1);
        assertCommand(12, commands, 2);
        assertEquals(3, commands.size());

    }

    private void assertCommand(int expected, List<Integer> commands, int index) {
        assertEquals(expected, commands.get(index).intValue());
    }

    public void testCommandAndLength() {
        assertEquals(9, VectorTileEncoder.commandAndLength(Command.MoveTo, 1));
        assertEquals(18, VectorTileEncoder.commandAndLength(Command.LineTo, 2));
        assertEquals(15, VectorTileEncoder.commandAndLength(Command.ClosePath, 1));
    }

    public void testZigZagEncode() {
        // https://developers.google.com/protocol-buffers/docs/encoding#types
        assertEquals(0, VectorTileEncoder.zigZagEncode(0));
        assertEquals(1, VectorTileEncoder.zigZagEncode(-1));
        assertEquals(2, VectorTileEncoder.zigZagEncode(1));
        assertEquals(3, VectorTileEncoder.zigZagEncode(-2));
    }

    public void testNullAttributeValue() throws IOException {
        VectorTileEncoder vtm = new VectorTileEncoder(256);
        Geometry geometry = gf.createPoint(new Coordinate(3, 6));

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("key1", "value1");
        attributes.put("key2", null);
        attributes.put("key3", "value3");

        vtm.addFeature("DEPCNT", attributes, geometry);

        byte[] encoded = vtm.encode();
        assertNotSame(0, encoded.length);

        VectorTileDecoder decoder = new VectorTileDecoder();
        assertEquals(1, decoder.decode(encoded, "DEPCNT").asList().size());
        Map<String, Object> decodedAttributes = decoder.decode(encoded, "DEPCNT").asList().get(0).getAttributes();
        assertEquals("value1", decodedAttributes.get("key1"));
        assertEquals("value3", decodedAttributes.get("key3"));
        assertFalse(decodedAttributes.containsKey("key2"));

    }

    public void testAttributeTypes() throws IOException {
        VectorTileEncoder vtm = new VectorTileEncoder(256);
        Geometry geometry = gf.createPoint(new Coordinate(3, 6));

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("key1", "value1");
        attributes.put("key2", Integer.valueOf(123));
        attributes.put("key3", Float.valueOf(234.1f));
        attributes.put("key4", Double.valueOf(567.123d));
        attributes.put("key5", Long.valueOf(-123));
        attributes.put("key6", "value6");

        vtm.addFeature("DEPCNT", attributes, geometry);

        byte[] encoded = vtm.encode();
        assertNotSame(0, encoded.length);

        VectorTileDecoder decoder = new VectorTileDecoder();
        assertEquals(1, decoder.decode(encoded, "DEPCNT").asList().size());
        Map<String, Object> decodedAttributes = decoder.decode(encoded, "DEPCNT").asList().get(0).getAttributes();
        assertEquals("value1", decodedAttributes.get("key1"));
        assertEquals(Long.valueOf(123), decodedAttributes.get("key2"));
        assertEquals(Float.valueOf(234.1f), decodedAttributes.get("key3"));
        assertEquals(Double.valueOf(567.123d), decodedAttributes.get("key4"));
        assertEquals(Long.valueOf(-123), decodedAttributes.get("key5"));
        assertEquals("value6", decodedAttributes.get("key6"));
    }

    public void testProvidedIds() throws IOException {
        VectorTileEncoder vtm = new VectorTileEncoder(256);

        Geometry geometry = gf.createPoint(new Coordinate(3, 6));
        Map<String, String> attributes = Collections.singletonMap("key1", "value1");
        vtm.addFeature("DEPCNT", attributes, geometry, 50);

        List<Feature> features = encodeDecodeFeatures(vtm);
        assertEquals(1, features.size());
        assertEquals(50, features.get(0).getId());
    }

    public void testAutoincrementIds() throws IOException {
        VectorTileEncoder vtm = new VectorTileEncoder(256, 8, true, true);

        for (int i = 0; i < 10; i++) {
            Geometry geometry = gf.createPoint(new Coordinate(3 * i, 6 * i));
            Map<String, String> attributes = Collections.singletonMap("key" + i, "value" + i);
            vtm.addFeature("DEPCNT", attributes, geometry);
        }

        List<Feature> features = encodeDecodeFeatures(vtm);
        for (int i = 0; i < features.size(); i++) {
            assertEquals(i + 1, features.get(i).getId());
        }
    }

    public void testProvidedAndAutoincrementIds() throws IOException {
        VectorTileEncoder vtm = new VectorTileEncoder(256, 8, true, true);

        Geometry geometry = gf.createPoint(new Coordinate(3, 6));
        Map<String, String> attributes = Collections.singletonMap("key1", "value1");
        vtm.addFeature("DEPCNT", attributes, geometry, 50);

        geometry = gf.createPoint(new Coordinate(3, 6));
        attributes = Collections.singletonMap("key1", "value1");
        vtm.addFeature("DEPCNT", attributes, geometry);

        geometry = gf.createPoint(new Coordinate(3, 6));
        attributes = Collections.singletonMap("key1", "value1");
        vtm.addFeature("DEPCNT", attributes, geometry, 27);

        geometry = gf.createPoint(new Coordinate(3, 6));
        attributes = Collections.singletonMap("key1", "value1");
        vtm.addFeature("DEPCNT", attributes, geometry);

        List<Feature> features = encodeDecodeFeatures(vtm);
        assertEquals(4, features.size());
        assertEquals(50, features.get(0).getId());
        assertEquals(51, features.get(1).getId());
        assertEquals(27, features.get(2).getId());
        assertEquals(52, features.get(3).getId());
    }

    public void testNullIds() throws IOException {
        VectorTileEncoder vtm = new VectorTileEncoder(256);

        Geometry geometry = gf.createPoint(new Coordinate(3, 6));
        Map<String, String> attributes = Collections.singletonMap("key1", "value1");
        vtm.addFeature("DEPCNT", attributes, geometry, 50);

        geometry = gf.createPoint(new Coordinate(3, 6));
        attributes = Collections.singletonMap("key1", "value1");
        vtm.addFeature("DEPCNT", attributes, geometry);

        List<Feature> features = encodeDecodeFeatures(vtm);
        assertEquals(2, features.size());
        assertEquals(50, features.get(0).getId());
        assertEquals(0, features.get(1).getId());
    }

    public void testMultiPolygonCommands() throws IOException {
        // see https://github.com/mapbox/vector-tile-spec/blob/master/2.1/README.md

        Coordinate[] cs1 = new Coordinate[5];
        cs1[0] = new Coordinate(0, 0);
        cs1[1] = new Coordinate(10, 0);
        cs1[2] = new Coordinate(10, 10);
        cs1[3] = new Coordinate(0, 10);
        cs1[4] = new Coordinate(0, 0);

        Coordinate[] cs2 = new Coordinate[5];
        cs2[0] = new Coordinate(11, 11);
        cs2[1] = new Coordinate(20, 11);
        cs2[2] = new Coordinate(20, 20);
        cs2[3] = new Coordinate(11, 20);
        cs2[4] = new Coordinate(11, 11);

        Coordinate[] cs2i = new Coordinate[5];
        cs2i[0] = new Coordinate(13, 13);
        cs2i[1] = new Coordinate(13, 17);
        cs2i[2] = new Coordinate(17, 17);
        cs2i[3] = new Coordinate(17, 13);
        cs2i[4] = new Coordinate(13, 13);

        Polygon[] polygons = new Polygon[2];
        polygons[0] = gf.createPolygon(cs1);
        polygons[1] = gf.createPolygon(gf.createLinearRing(cs2), new LinearRing[] { gf.createLinearRing(cs2i) });
        MultiPolygon mp = gf.createMultiPolygon(polygons);

        VectorTileEncoder vte = new VectorTileEncoder(256);
        List<Integer> commands = vte.commands(mp);
        assertEquals(Arrays.asList(9, 0, 0, 26, 20, 0, 0, 20, 19, 0, 15, 9, 22, 2, 26, 18, 0, 0, 18, 17, 0, 15, 9, 4,
                13, 26, 0, 8, 8, 0, 0, 7, 15), commands);
        
        vte = new VectorTileEncoder(256);
        vte.addFeature("x", Collections.EMPTY_MAP, mp);
        
        VectorTileDecoder vtd = new VectorTileDecoder();
        List<Feature> features = vtd.decode(vte.encode()).asList();
        assertEquals(1, features.size());
        MultiPolygon mpe = (MultiPolygon) features.get(0).getGeometry();
        assertEquals(2, mpe.getNumGeometries());
    }

    public void testMultiPolygon() throws IOException {
        Polygon[] polygons = new Polygon[2];
        polygons[0] = (Polygon) gf.createPoint(new Coordinate(13, 16)).buffer(3);
        polygons[1] = (Polygon) gf.createPoint(new Coordinate(24, 25)).buffer(5)
                .symDifference(gf.createPoint(new Coordinate(24, 25)).buffer(1.0));
        MultiPolygon mp = gf.createMultiPolygon(polygons);
        assertTrue(mp.isValid());
        System.out.println(mp.toString());

        Map<String, String> attributes = Collections.singletonMap("key1", "value1");

        VectorTileEncoder vtm = new VectorTileEncoder(256);
        vtm.addFeature("mp", attributes, mp);

        byte[] encoded = vtm.encode();
        assertTrue(encoded.length > 0);

        VectorTileDecoder decoder = new VectorTileDecoder();
        List<Feature> features = decoder.decode(encoded).asList();
        assertEquals(1, features.size());
        MultiPolygon mp2 = (MultiPolygon) features.get(0).getGeometry();
        assertEquals(mp.getNumGeometries(), mp2.getNumGeometries());
    }

    private List<Feature> encodeDecodeFeatures(VectorTileEncoder vtm) throws IOException {
        byte[] encoded = vtm.encode();
        assertNotSame(0, encoded.length);

        VectorTileDecoder decoder = new VectorTileDecoder();
        return decoder.decode(encoded, "DEPCNT").asList();
    }
}
