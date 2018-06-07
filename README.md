# PngMetaReader
implement comment to PNG file.

# How to use
```java
PngMetaReader png = new PngMetaReader(file);
png.SetInternationalText("Comment", "comment");
png.Save(file);
```
```java
File file = new File(path);
PngMetaReader png = new PngMetaReader(file);
String comment = png.GetInternationalText("Comment");
```
