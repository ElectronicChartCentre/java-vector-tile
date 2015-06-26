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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import vector_tile.VectorTile;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

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
        assertEquals(VectorTile.Tile.LINESTRING, VectorTileEncoder.toGeomType(geometry));
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
        
        List<Integer> commands = new VectorTileEncoder(512, 8, false).commands(cs.toArray(new Coordinate[cs.size()]), false);
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

}
