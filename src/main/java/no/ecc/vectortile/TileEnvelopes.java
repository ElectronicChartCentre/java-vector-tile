package no.ecc.vectortile;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

public final class TileEnvelopes {

	private TileEnvelopes() {
	}
	
	public static Geometry createTileEnvelope(int buffer, int size) {        
        Coordinate[] coords = new Coordinate[5];
        coords[0] = new Coordinate(0 - buffer, size + buffer);
        coords[1] = new Coordinate(size + buffer, size + buffer);
        coords[2] = new Coordinate(size + buffer, 0 - buffer);
        coords[3] = new Coordinate(0 - buffer, 0 - buffer);
        coords[4] = coords[0];
        return new GeometryFactory().createPolygon(coords);
    }
}
