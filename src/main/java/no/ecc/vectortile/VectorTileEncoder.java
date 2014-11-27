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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vector_tile.VectorTile;
import vector_tile.VectorTile.Tile.GeomType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.TopologyException;

public class VectorTileEncoder {

    private final Map<String, Layer> layers = new HashMap<String, Layer>();

    private final int extent;

    private final Geometry clipGeometry;

    private final Geometry polygonClipGeometry;

    /**
     * Create a {@link VectorTileEncoder} with the default extent of 4096 and
     * clip buffer of 8.
     */
    public VectorTileEncoder() {
        this(4096, 8);
    }

    /**
     * Create a {@link VectorTileEncoder} with the given extent and a polygon
     * clip buffer of 8.
     */
    public VectorTileEncoder(int extent) {
        this(extent, 8);
    }

    /**
     * Create a {@link VectorTileEncoder} with the given extent value.
     * <p>
     * The extent value control how detailed the coordinates are encoded in the
     * vector tile. 4096 is a good default, 256 can be used to reduce density.
     * <p>
     * The polygon clip buffer value control how large the clipping area is
     * outside of the tile for polygons. 0 means that the clipping is done at
     * the tile border. 8 is a good default.
     * 
     * @param extent
     *            a int with extent value. 4096 is a good value.
     * @param polygonClipBuffer
     *            a int with clip buffer size for polygons. 8 is a good value.
     */
    public VectorTileEncoder(int extent, int polygonClipBuffer) {
        this.extent = extent;

        clipGeometry = createTileEnvelope(0);
        polygonClipGeometry = createTileEnvelope(polygonClipBuffer);
    }

    private static Geometry createTileEnvelope(int buffer) {
        Coordinate[] coords = new Coordinate[5];
        coords[0] = new Coordinate(0 - buffer, 256 + buffer);
        coords[1] = new Coordinate(256 + buffer, 256 + buffer);
        coords[2] = new Coordinate(256 + buffer, 0);
        coords[3] = new Coordinate(0 - buffer, 0 - buffer);
        coords[4] = coords[0];
        return new GeometryFactory().createPolygon(coords);
    }

    /**
     * Add a feature with layer name (typically feature type name), some
     * attributes and a Geometry. The Geometry must be in "pixel" space 0,0
     * lower left and 256,256 upper right.
     * <p>
     * For optimization, geometries will be clipped, geometries will simplified
     * and features with geometries outside of the tile will be skipped.
     * 
     * @param layerName
     * @param attributes
     * @param geometry
     */
    public void addFeature(String layerName, Map<String, ?> attributes, Geometry geometry) {

        // split up MultiPolygon and GeometryCollection (without subclasses)
        if (geometry instanceof MultiPolygon || geometry.getClass().equals(GeometryCollection.class)) {
            splitAndAddFeatures(layerName, attributes, (GeometryCollection) geometry);
            return;
        }
        
        // skip small Polygon/LineString.
        if (geometry instanceof Polygon && geometry.getArea() < 1.0d) {
            return;
        }
        if (geometry instanceof LineString && geometry.getLength() < 1.0d) {
            return;
        }

        // clip geometry. polygons right outside. other geometries at tile
        // border.
        try {
            if (geometry instanceof Polygon) {
                geometry = polygonClipGeometry.intersection(geometry);
            } else {
                geometry = clipGeometry.intersection(geometry);
            }
        } catch (TopologyException e) {
            // ignore topology exceptions. sorry.
        }

        // if clipping result in MultiPolygon, then split once more
        if (geometry instanceof MultiPolygon) {
            splitAndAddFeatures(layerName, attributes, (GeometryCollection) geometry);
            return;
        }

        // no need to add empty geometry
        if (geometry.isEmpty()) {
            return;
        }

        Layer layer = layers.get(layerName);
        if (layer == null) {
            layer = new Layer();
            layers.put(layerName, layer);
        }

        Feature feature = new Feature();
        feature.geometry = geometry;

        for (Map.Entry<String, ?> e : attributes.entrySet()) {
            // skip attribute without value
            if (e.getValue() == null) {
                continue;
            }
            feature.tags.add(layer.key(e.getKey()));
            feature.tags.add(layer.value(e.getValue()));
        }

        layer.features.add(feature);
    }

    private void splitAndAddFeatures(String layerName, Map<String, ?> attributes, GeometryCollection geometry) {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry subGeometry = geometry.getGeometryN(i);
            addFeature(layerName, attributes, subGeometry);
        }
    }

    /**
     * @return a byte array with the vector tile
     */
    public byte[] encode() {

        VectorTile.Tile.Builder tileBuilder = VectorTile.Tile.newBuilder();

        for (Map.Entry<String, Layer> e : layers.entrySet()) {
            String layerName = e.getKey();
            Layer layer = e.getValue();

            VectorTile.Tile.Layer.Builder layerBuilder = VectorTile.Tile.Layer.newBuilder();
            layerBuilder.setVersion(1);
            layerBuilder.setName(layerName);

            layerBuilder.addAllKeys(layer.keys());

            for (Object value : layer.values()) {
                VectorTile.Tile.Value.Builder valueBuilder = VectorTile.Tile.Value.newBuilder();
                if (value instanceof String) {
                    valueBuilder.setStringValue((String) value);
                } else if (value instanceof Integer) {
                    valueBuilder.setSintValue(((Integer) value).intValue());
                } else if (value instanceof Long) {
                    valueBuilder.setSintValue(((Long) value).longValue());
                } else if (value instanceof Float) {
                    valueBuilder.setFloatValue(((Float) value).floatValue());
                } else if (value instanceof Double) {
                    valueBuilder.setDoubleValue(((Double) value).doubleValue());
                } else {
                    valueBuilder.setStringValue(value.toString());
                }
                layerBuilder.addValues(valueBuilder.build());
            }

            layerBuilder.setExtent(extent);

            for (Feature feature : layer.features) {

                Geometry geometry = feature.geometry;

                VectorTile.Tile.Feature.Builder featureBuilder = VectorTile.Tile.Feature.newBuilder();

                featureBuilder.addAllTags(feature.tags);
                featureBuilder.setType(toGeomType(geometry));
                featureBuilder.addAllGeometry(commands(geometry));

                layerBuilder.addFeatures(featureBuilder.build());
            }

            tileBuilder.addLayers(layerBuilder.build());

        }

        return tileBuilder.build().toByteArray();
    }

    static GeomType toGeomType(Geometry geometry) {
        if (geometry instanceof com.vividsolutions.jts.geom.Point) {
            return GeomType.POINT;
        }
        if (geometry instanceof com.vividsolutions.jts.geom.MultiPoint) {
            return GeomType.POINT;
        }
        if (geometry instanceof com.vividsolutions.jts.geom.LineString) {
            return GeomType.LINESTRING;
        }
        if (geometry instanceof com.vividsolutions.jts.geom.MultiLineString) {
            return GeomType.LINESTRING;
        }
        if (geometry instanceof com.vividsolutions.jts.geom.Polygon) {
            return GeomType.POLYGON;
        }
        return GeomType.UNKNOWN;
    }

    static boolean shouldClosePath(Geometry geometry) {
        return (geometry instanceof Polygon) || (geometry instanceof LinearRing);
    }

    List<Integer> commands(Geometry geometry) {

        x = 0;
        y = 0;

        if (geometry instanceof Polygon) {
            Polygon polygon = (Polygon) geometry;
            if (polygon.getNumInteriorRing() > 0) {
                List<Integer> commands = new ArrayList<Integer>();
                commands.addAll(commands(polygon.getExteriorRing().getCoordinates(), true));
                for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                    commands.addAll(commands(polygon.getInteriorRingN(i).getCoordinates(), true));
                }
                return commands;
            }
        }

        if (geometry instanceof MultiLineString || geometry instanceof MultiPoint) {
            List<Integer> commands = new ArrayList<Integer>();
            GeometryCollection gc = (GeometryCollection) geometry;
            for (int i = 0; i < gc.getNumGeometries(); i++) {
                commands.addAll(commands(gc.getGeometryN(i).getCoordinates(), false));
            }
            return commands;
        }

        return commands(geometry.getCoordinates(), shouldClosePath(geometry));
    }

    private int x = 0;
    private int y = 0;

    /**
     * // // // Ex.: MoveTo(3, 6), LineTo(8, 12), LineTo(20, 34), ClosePath //
     * Encoded as: [ 9 3 6 18 5 6 12 22 15 ] // == command type 7 (ClosePath),
     * length 1 // ===== relative LineTo(+12, +22) == LineTo(20, 34) // ===
     * relative LineTo(+5, +6) == LineTo(8, 12) // == [00010 010] = command type
     * 2 (LineTo), length 2 // === relative MoveTo(+3, +6) // == [00001 001] =
     * command type 1 (MoveTo), length 1 // Commands are encoded as uint32
     * varints, vertex parameters are // encoded as sint32 varints (zigzag).
     * Vertex parameters are // also encoded as deltas to the previous position.
     * The original // position is (0,0)
     * 
     * @param cs
     * @return
     */
    List<Integer> commands(Coordinate[] cs, boolean closePathAtEnd) {

        if (cs.length == 0) {
            throw new IllegalArgumentException("empty geometry");
        }

        List<Integer> r = new ArrayList<Integer>();

        int lineToIndex = 0;
        int lineToLength = 0;

        double scale = extent / 256.0;

        for (int i = 0; i < cs.length; i++) {
            Coordinate c = cs[i];

            if (i == 0) {
                r.add(commandAndLength(Command.MoveTo, 1));
            }

            int _x = (int) Math.round(c.x * scale);
            int _y = (int) Math.round(c.y * scale);

            // prevent point equal to the previous
            if (i > 0 && _x == x && _y == y) {
                lineToLength--;
                continue;
            }

            // prevent double closing
            if (closePathAtEnd && cs.length > 1 && i == (cs.length - 1) && cs[0].equals(c)) {
                lineToLength--;
                continue;
            }

            // delta, then zigzag
            r.add(zigZagEncode(_x - x));
            r.add(zigZagEncode(_y - y));

            x = _x;
            y = _y;

            if (i == 0) {
                // can length be too long?
                lineToIndex = r.size();
                lineToLength = cs.length - 1;
                r.add(commandAndLength(Command.LineTo, lineToLength));
            }

        }

        // update LineTo length
        r.set(lineToIndex, commandAndLength(Command.LineTo, lineToLength));

        if (closePathAtEnd) {
            r.add(commandAndLength(Command.ClosePath, 1));
        }

        return r;
    }

    static int commandAndLength(int command, int repeat) {
        return repeat << 3 | command;
    }

    static int zigZagEncode(int n) {
        // https://developers.google.com/protocol-buffers/docs/encoding#types
        return (n << 1) ^ (n >> 31);
    }

    private static final class Layer {

        final List<Feature> features = new ArrayList<VectorTileEncoder.Feature>();

        private final Map<String, Integer> keys = new LinkedHashMap<String, Integer>();
        private final Map<Object, Integer> values = new LinkedHashMap<Object, Integer>();

        public Integer key(String key) {
            Integer i = keys.get(key);
            if (i == null) {
                i = Integer.valueOf(keys.size());
                keys.put(key, i);
            }
            return i;
        }

        public List<String> keys() {
            return Collections.unmodifiableList(new ArrayList<String>(keys.keySet()));
        }

        public Integer value(Object value) {
            Integer i = values.get(value);
            if (i == null) {
                i = Integer.valueOf(values.size());
                values.put(value, i);
            }
            return i;
        }

        public List<Object> values() {
            return Collections.unmodifiableList(new ArrayList<Object>(values.keySet()));
        }
    }

    private static final class Feature {

        Geometry geometry;
        final List<Integer> tags = new ArrayList<Integer>();

    }
}
