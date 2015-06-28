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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import vector_tile.VectorTile;
import vector_tile.VectorTile.Tile.Layer;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;

public class VectorTileDecoder {
    
    private boolean autoScale = true;
    
    /**
     * Get the autoScale setting.
     * 
     * @return autoScale
     */
    public boolean isAutoScale() {
            return autoScale;
    }

    /**
     * Set the autoScale setting.
     * 
     * @param autoScale
     *            when true, the encoder automatically scale and return all coordinates in the 0..255 range.
     *            when false, the encoder returns all coordinates in the 0..extent-1 range as they are encoded. 
     * 
     */
    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
    }

    public FeatureIterable decode(byte[] data) throws IOException {
        return decode(data, Filter.ALL);
    }

    public FeatureIterable decode(byte[] data, String layerName) throws IOException {
        return decode(data, new Filter.Single(layerName));
    }

    private FeatureIterable decode(byte[] data, Filter filter) throws IOException {
        VectorTile.Tile tile = VectorTile.Tile.parseFrom(data);
        return new FeatureIterable(tile, filter, autoScale);
    }

    static int zigZagDecode(int n) {
        return ((n >> 1) ^ (-(n & 1)));
    }

    public static final class FeatureIterable implements Iterable<Feature> {

        private final VectorTile.Tile tile;
        private final Filter filter;
        private boolean autoScale;

        public FeatureIterable(VectorTile.Tile tile, Filter filter, boolean autoScale) {
            this.tile = tile;
            this.filter = filter;
            this.autoScale = autoScale;
        }

        public Iterator<Feature> iterator() {
            return new FeatureIterator(tile, filter, autoScale);
        }

        public List<Feature> asList() {
            List<Feature> features = new ArrayList<VectorTileDecoder.Feature>();
            for (Feature feature : this) {
                features.add(feature);
            }
            return features;
        }

        public Collection<String> getLayerNames() {
            Set<String> layerNames = new HashSet<String>();
            for (VectorTile.Tile.Layer layer : tile.layers) {
                layerNames.add(layer.name);
            }
            return Collections.unmodifiableSet(layerNames);
        }

    }

    private static final class FeatureIterator implements Iterator<Feature> {

        private final GeometryFactory gf = new GeometryFactory();

        private final Filter filter;

        private final Iterator<VectorTile.Tile.Layer> layerIterator;

        private Iterator<VectorTile.Tile.Feature> featureIterator;

        private int extent;
        private String layerName;
        private double scale;
        private boolean autoScale;

        private final List<String> keys = new ArrayList<String>();
        private final List<Object> values = new ArrayList<Object>();

        private Feature next;

        public FeatureIterator(VectorTile.Tile tile, Filter filter, boolean autoScale) {
            layerIterator = Arrays.asList(tile.layers).iterator();
            this.filter = filter;
            this.autoScale = autoScale;
        }

        public boolean hasNext() {
            findNext();
            return next != null;
        }

        public Feature next() {
            findNext();
            if (next == null) {
                throw new NoSuchElementException();
            }
            Feature n = next;
            next = null;
            return n;
        }

        private void findNext() {

            if (next != null) {
                return;
            }

            while (true) {

                if (featureIterator == null || !featureIterator.hasNext()) {
                    if (!layerIterator.hasNext()) {
                        next = null;
                        break;
                    }

                    Layer layer = layerIterator.next();
                    if (!filter.include(layer.name)) {
                        continue;
                    }

                    parseLayer(layer);
                    continue;
                }

                next = parseFeature(featureIterator.next());
                break;

            }

        }

        private void parseLayer(VectorTile.Tile.Layer layer) {

            layerName = layer.name;
            extent = layer.getExtent();
            scale = autoScale ? extent / 256.0 : 1.0;

            keys.clear();
            keys.addAll(Arrays.asList(layer.keys));
            values.clear();

            for (VectorTile.Tile.Value value : layer.values) {
                if (value.hasBoolValue()) {
                    values.add(value.getBoolValue());
                } else if (value.hasDoubleValue()) {
                    values.add(value.getDoubleValue());
                } else if (value.hasFloatValue()) {
                    values.add(value.getFloatValue());
                } else if (value.hasIntValue()) {
                    values.add(value.getIntValue());
                } else if (value.hasSintValue()) {
                    values.add(value.getSintValue());
                } else if (value.hasUintValue()) {
                    values.add(value.getUintValue());
                } else if (value.hasStringValue()) {
                    values.add(value.getStringValue());
                } else {
                    values.add(null);
                }

            }

            featureIterator = Arrays.asList(layer.features).iterator();
        }

        private Feature parseFeature(VectorTile.Tile.Feature feature) {

            int tagsCount = feature.tags.length;
            Map<String, Object> attributes = new HashMap<String, Object>(tagsCount / 2);
            int tagIdx = 0;
            while (tagIdx < feature.tags.length) {
                String key = keys.get(feature.tags[tagIdx++]);
                Object value = values.get(feature.tags[tagIdx++]);
                attributes.put(key, value);
            }

            int x = 0;
            int y = 0;

            List<List<Coordinate>> coordsList = new ArrayList<List<Coordinate>>();
            List<Coordinate> coords = null;

            int geometryCount = feature.geometry.length;
            int length = 0;
            int command = 0;
            int i = 0;
            while (i < geometryCount) {

                if (length <= 0) {
                    length = feature.geometry[i++];
                    command = length & ((1 << 3) - 1);
                    length = length >> 3;
                }

                if (length > 0) {

                    if (command == Command.MoveTo) {
                        coords = new ArrayList<Coordinate>();
                        coordsList.add(coords);
                    }

                    if (command == Command.ClosePath) {
                        if (feature.getType() != VectorTile.Tile.POINT && !coords.isEmpty()) {
                            coords.add(coords.get(0));
                        }
                        length--;
                        continue;
                    }

                    int dx = feature.geometry[i++];
                    int dy = feature.geometry[i++];

                    length--;

                    dx = zigZagDecode(dx);
                    dy = zigZagDecode(dy);

                    x = x + dx;
                    y = y + dy;

                    Coordinate coord = new Coordinate(x / scale, y / scale);
                    coords.add(coord);
                }

            }

            Geometry geometry = null;

            switch (feature.getType()) {
            case VectorTile.Tile.LINESTRING:
                List<LineString> lineStrings = new ArrayList<LineString>();
                for (List<Coordinate> cs : coordsList) {
                    lineStrings.add(gf.createLineString(cs.toArray(new Coordinate[cs.size()])));
                }
                if (lineStrings.size() == 1) {
                    geometry = lineStrings.get(0);
                } else if (lineStrings.size() > 1) {
                    geometry = gf.createMultiLineString(lineStrings.toArray(new LineString[lineStrings.size()]));
                }
                break;
            case VectorTile.Tile.POINT:
                List<Coordinate> allCoords = new ArrayList<Coordinate>();
                for (List<Coordinate> cs : coordsList) {
                    allCoords.addAll(cs);
                }
                if (allCoords.size() == 1) {
                    geometry = gf.createPoint(allCoords.get(0));
                } else if (allCoords.size() > 1) {
                    geometry = gf.createMultiPoint(allCoords.toArray(new Coordinate[allCoords.size()]));
                }
                break;
            case VectorTile.Tile.POLYGON:
                List<LinearRing> rings = new ArrayList<LinearRing>();
                for (List<Coordinate> cs : coordsList) {
                    rings.add(gf.createLinearRing(cs.toArray(new Coordinate[cs.size()])));
                }
                if (rings.size() > 0) {
                    LinearRing shell = rings.get(0);
                    LinearRing[] holes = rings.subList(1, rings.size()).toArray(new LinearRing[rings.size() - 1]);
                    geometry = gf.createPolygon(shell, holes);
                }
                break;
            case VectorTile.Tile.UNKNOWN:
                break;
            default:
                break;
            }

            if (geometry == null) {
                geometry = gf.createGeometryCollection(new Geometry[0]);
            }

            return new Feature(layerName, extent, geometry, Collections.unmodifiableMap(attributes));
        }

    }

    public static final class Feature {

        private final String layerName;
        private final int extent;
        private final Geometry geometry;
        private final Map<String, Object> attributes;

        public Feature(String layerName, int extent, Geometry geometry, Map<String, Object> attributes) {
            this.layerName = layerName;
            this.extent = extent;
            this.geometry = geometry;
            this.attributes = attributes;
        }

        public String getLayerName() {
            return layerName;
        }

        public int getExtent() {
            return extent;
        }

        public Geometry getGeometry() {
            return geometry;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

    }

}
