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

import com.google.protobuf.nano.MessageNano;
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
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

public class VectorTileEncoder {

    private final Map<String, Layer> layers = new HashMap<String, Layer>();

    private final int extent;

    private final Geometry clipGeometry;

    private final Geometry polygonClipGeometry;
    
    private final boolean autoScale;

    /**
     * Create a {@link VectorTileEncoder} with the default extent of 4096 and
     * clip buffer of 8.
     */
    public VectorTileEncoder() {
        this(4096, 8, true);
    }

    /**
     * Create a {@link VectorTileEncoder} with the given extent and a polygon
     * clip buffer of 8.
     */
    public VectorTileEncoder(int extent) {
        this(extent, 8, true);
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
     * @param autoScale
     *            when true, the encoder expects coordinates in the 0..255 range and will scale
     *             them automatically to the 0..extent-1 range before encoding.
     *            when false, the encoder expects coordinates in the 0..extent-1 range. 
     *            
     */
    public VectorTileEncoder(int extent, int polygonClipBuffer, boolean autoScale) {
        this.extent = extent;
        this.autoScale = autoScale;

        final int size = autoScale ? 256 : extent;
        clipGeometry = createTileEnvelope(0, size);
        polygonClipGeometry = createTileEnvelope(polygonClipBuffer, size);
    }

    private static Geometry createTileEnvelope(int buffer, int size) {        
        Coordinate[] coords = new Coordinate[5];
        coords[0] = new Coordinate(0 - buffer, size + buffer);
        coords[1] = new Coordinate(size + buffer, size + buffer);
        coords[2] = new Coordinate(size + buffer, 0 - buffer);
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
                Geometry original = geometry;
                geometry = polygonClipGeometry.intersection(original);

                // some times a intersection is returned as an empty geometry.
                // going via wkt fixes the problem.
                if (geometry.isEmpty() && original.intersects(polygonClipGeometry)) {
                    Geometry originalViaWkt = new WKTReader().read(original.toText());
                    geometry = polygonClipGeometry.intersection(originalViaWkt);
                }
                
            } else {
                geometry = clipGeometry.intersection(geometry);
            }
        } catch (TopologyException e) {
            // could not intersect. original geometry will be used instead.
        } catch (ParseException e1) {
            // could not encode/decode WKT. original geometry will be used instead.
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

        VectorTile.Tile tile = new VectorTile.Tile();

        List<VectorTile.Tile.Layer> tileLayers = new ArrayList<VectorTile.Tile.Layer>();
        for (Map.Entry<String, Layer> e : layers.entrySet()) {
            String layerName = e.getKey();
            Layer layer = e.getValue();

            VectorTile.Tile.Layer tileLayer = new VectorTile.Tile.Layer();
            tileLayer.version = 1;
            tileLayer.name = layerName;

            tileLayer.keys = layer.keys();

            List<VectorTile.Tile.Value> values = new ArrayList<VectorTile.Tile.Value>();
            for (Object value : layer.values()) {
                VectorTile.Tile.Value tileValue = new VectorTile.Tile.Value();
                if (value instanceof String) {
                    tileValue.setStringValue((String) value);
                } else if (value instanceof Integer) {
                    tileValue.setSintValue(((Integer) value).intValue());
                } else if (value instanceof Long) {
                    tileValue.setSintValue(((Long) value).longValue());
                } else if (value instanceof Float) {
                    tileValue.setFloatValue(((Float) value).floatValue());
                } else if (value instanceof Double) {
                    tileValue.setDoubleValue(((Double) value).doubleValue());
                } else {
                    tileValue.setStringValue(value.toString());
                }
                values.add(tileValue);
            }
            tileLayer.values = values.toArray(new VectorTile.Tile.Value[values.size()]);

            tileLayer.setExtent(extent);

            List<VectorTile.Tile.Feature> features = new ArrayList<VectorTile.Tile.Feature>();
            for (Feature feature : layer.features) {

                Geometry geometry = feature.geometry;

                VectorTile.Tile.Feature featureBuilder = new VectorTile.Tile.Feature();

                featureBuilder.tags = toIntArray(feature.tags);
                featureBuilder.setType(toGeomType(geometry));
                featureBuilder.geometry = toIntArray(commands(geometry));

                features.add(featureBuilder);
            }

            tileLayer.features = features.toArray(new VectorTile.Tile.Feature[features.size()]);
            tileLayers.add(tileLayer);

        }
        
        tile.layers = tileLayers.toArray(new VectorTile.Tile.Layer[tileLayers.size()]);

        return MessageNano.toByteArray(tile);
    }
    
    static int[] toIntArray(List<Integer> ints) {
        int[] r = new int[ints.size()];
        for (int i = 0; i < ints.size(); i++) {
            r[i] = ints.get(i).intValue();
        }
        return r;
    }

    static int toGeomType(Geometry geometry) {
        if (geometry instanceof com.vividsolutions.jts.geom.Point) {
            return VectorTile.Tile.POINT;
        }
        if (geometry instanceof com.vividsolutions.jts.geom.MultiPoint) {
            return VectorTile.Tile.POINT;
        }
        if (geometry instanceof com.vividsolutions.jts.geom.LineString) {
            return VectorTile.Tile.LINESTRING;
        }
        if (geometry instanceof com.vividsolutions.jts.geom.MultiLineString) {
            return VectorTile.Tile.LINESTRING;
        }
        if (geometry instanceof com.vividsolutions.jts.geom.Polygon) {
            return VectorTile.Tile.POLYGON;
        }
        return VectorTile.Tile.UNKNOWN;
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

        double scale = autoScale ? (extent / 256.0) : 1.0;

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

            if (i == 0 && cs.length > 1) {
                // can length be too long?
                lineToIndex = r.size();
                lineToLength = cs.length - 1;
                r.add(commandAndLength(Command.LineTo, lineToLength));
            }

        }

        // update LineTo length
        if (lineToIndex > 0) {
            if (lineToLength == 0) {
                // remove empty LineTo
                r.remove(lineToIndex);
            } else {
                // update LineTo with new length
                r.set(lineToIndex, commandAndLength(Command.LineTo, lineToLength));
            }
        }

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

        public String[] keys() {
            List<String> r = new ArrayList<String>(keys.keySet());
            return r.toArray(new String[r.size()]);
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
