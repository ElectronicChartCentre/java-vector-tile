# Java Vector Tiles

A java encoder and decoder for vector tiles according to
[Mapbox vector tile spec](https://github.com/mapbox/vector-tile-spec)


## Encode a vector tile

```java
VectorTileEncoder encoder = new VectorTileEncoder();

// Add one or more features with a layer name, a Map with attributes and a JTS Geometry. 
// The Geometry uses (0,0) in lower left and (256,256) in upper right.
encoder.addFeature("road", attributes, geometry);

// Finally, get the byte array
byte[] encoded = encoder.encode();
```

## Maven

```
<repository>
    <id>ECC</id>
    <url>https://github.com/ElectronicChartCentre/ecc-mvn-repo/raw/master/releases</url>
</repository>

<dependency>
    <groupId>no.ecc.vectortile</groupId>
    <artifactId>java-vector-tile</artifactId>
    <version>1.0.9</version>
</dependency>
```

## Generate VectorTile.java

```
protoc --javanano_out=store_unknown_fields=true,optional_field_style=accessors:src/main/java/ src/main/resources/vector_tile.proto
```

## License

[Apache License, version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## Credits

Mapbox for their [vector tile spec](https://github.com/mapbox/vector-tile-spec), 
Google for their [Protocol Buffers](https://code.google.com/p/protobuf/) and
[JTS](http://sourceforge.net/projects/jts-topo-suite/)
