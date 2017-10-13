This branch is from the tag 1.5.0 using following command:

```
git checkout -b article-1.5.0 1.5.0
```

Following commit has been cherry picked into this branch:

```
git cherry-pick 811a2359345952e3c801d5f2e3ba04c67547b8cd
[article-1.5.0 27e896fb4] Allow customization of @CacheFor cache key #1133
```

Following method has been added to the file ```framework/src/play/data/parsing/DataParsers.java```
```
   /**
     * Set a DataParser for a content type
     * @param contentType The content type this dataParser handles
     * @param dataParser The DataParser implementation
     */
    public static void setDataParser(String contentType, DataParser dataParser) {
        parsers.put(contentType, dataParser);
    }
```
