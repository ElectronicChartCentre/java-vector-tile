# Java Vector Tiles

A java encoder and decoder for vector tiles according to
[Mapbox vector tile spec](https://github.com/mapbox/vector-tile-spec)

## Encode a vector tile

```java
VectorTileEncoder encoder = new VectorTileEncoder();

// Add one or more features with a layer name, a Map with attributes and a JTS Geometry. 
// The Geometry uses (0,0) in upper left and (256,256) in lower right.
encoder.addFeature("road", attributes, geometry);

// Finally, get the byte array
byte[] encoded = encoder.encode();
```

or, specifying the feature id:

```java
VectorTileEncoder encoder = new VectorTileEncoder();
encoder.addFeature("road", attributes, geometry, id);
byte[] encoded = encoder.encode();
```

## Maven

```
<repository>
    <id>ECC</id>
    <url>https://maven.ecc.no/releases</url>
</repository>

<dependency>
    <groupId>no.ecc.vectortile</groupId>
    <artifactId>java-vector-tile</artifactId>
    <version>1.3.23</version>
</dependency>
```

## Generate VectorTile.java

```
protoc --java_out=src/main/java/ src/main/resources/vector_tile.proto
```

## License

[Apache License, version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## Credits

Mapbox for their [vector tile spec](https://github.com/mapbox/vector-tile-spec), 
Google for their [Protocol Buffers](https://code.google.com/p/protobuf/) and
Dr JTS and LocationTech for [JTS](https://github.com/locationtech/jts)
