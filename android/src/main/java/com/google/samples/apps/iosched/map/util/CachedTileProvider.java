/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.samples.apps.iosched.map.util;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;

import com.google.samples.apps.iosched.util.LogUtils;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.google.samples.apps.iosched.util.LogUtils.LOGD;
import static com.google.samples.apps.iosched.util.LogUtils.makeLogTag;

/**
 * Wrapper that provides a disk-based LRU cache for a TileProvider.
 *
 * @see com.jakewharton.disklrucache.DiskLruCache
 */
public class CachedTileProvider implements TileProvider {

    private static final String TAG = LogUtils.makeLogTag(SVGTileProvider.class);

    private static final String KEY_FORMAT = "%d_%d_%d_%s";

    // Index for cache entry streams
    private static final int INDEX_DATA = 0;
    private static final int INDEX_HEIGHT = 1;
    private static final int INDEX_WIDTH = 2;


    private final String mKeyTag;
    private final TileProvider mTileProvider;
    private final DiskLruCache mCache;

    /**
     * TileProvider that wraps another TileProvider and caches all Tiles in a DiskLruCache.
     *
     * <p>A {@link com.jakewharton.disklrucache.DiskLruCache} can be reused across multiple
     * instances.
     * The keyTag is used to annotate entries for this TileProvider, it is recommended to use a
     * unique
     * String for each instance to prevent collisions.
     *
     * <p>NOTE: The supplied {@link com.jakewharton.disklrucache.DiskLruCache} requires space for
     * 3 entries per cached object.
     *
     * @param keyTag       identifier used to identify tiles for this CachedTileProvider instance
     * @param tileProvider tiles from this TileProvider will be cached.
     * @param cache        the cache used to store tiles
     */
    public CachedTileProvider(String keyTag, TileProvider tileProvider, DiskLruCache cache) {
        mKeyTag = keyTag;
        mTileProvider = tileProvider;
        mCache = cache;
    }

    /**
     * Load a tile.
     * If cached, the data for the tile is read from the underlying cache, otherwise the tile is
     * generated by the {@link com.google.android.gms.maps.model.TileProvider} and added to the
     * cache.
     */
    @Override
    public Tile getTile(int x, int y, int zoom) {
        final String key = CachedTileProvider.generateKey(x, y, zoom, mKeyTag);
        Tile tile = getCachedTile(key);

        if (tile == null) {
            // tile not cached, load from provider and then cache
            tile = mTileProvider.getTile(x, y, zoom);
            if (cacheTile(key, tile)) {
                LogUtils.LOGD(TAG, "Added tile to cache " + key);
            }
        }
        return tile;
    }

    /**
     * Load a tile from cache.
     * Returns null if there is no corresponding cache entry or it could not be loaded.
     */
    private Tile getCachedTile(String key) {
        if (mCache.isClosed()) {
            return null;
        }
        try {
            DiskLruCache.Snapshot snapshot = mCache.get(key);
            if (snapshot == null) {
                // tile is not in cache
                return null;
            }

            final byte[] data = readStreamAsByteArray(snapshot.getInputStream(INDEX_DATA));
            final int height = readStreamAsInt(snapshot.getInputStream(INDEX_HEIGHT));
            final int width = readStreamAsInt(snapshot.getInputStream(INDEX_WIDTH));
            if (data != null) {
                LogUtils.LOGD(TAG, "Cache hit for tile " + key);
                return new Tile(width, height, data);
            }

        } catch (IOException e) {
            // ignore error
        }
        return null;
    }

    private boolean cacheTile(String key, Tile tile) {
        if (mCache.isClosed()) {
            return false;
        }
        try {
            DiskLruCache.Editor editor = mCache.edit(key);
            if (editor == null) {
                // editor is not available
                return false;
            }
            writeByteArrayToStream(tile.data, editor.newOutputStream(INDEX_DATA));
            writeIntToStream(tile.height, editor.newOutputStream(INDEX_HEIGHT));
            writeIntToStream(tile.width, editor.newOutputStream(INDEX_WIDTH));
            editor.commit();
            return true;
        } catch (IOException e) {
            // Tile could not be cached
        }
        return false;
    }

    public void closeCache() throws IOException {
        mCache.close();
    }


    private static String generateKey(int x, int y, int zoom, String tag) {
        return String.format(KEY_FORMAT, x, y, zoom, tag);
    }

    private static void writeByteArrayToStream(byte[] data, OutputStream stream)
            throws IOException {
        try {
            stream.write(data);
        } finally {
            stream.close();
        }
    }

    private static void writeIntToStream(int data, OutputStream stream) throws IOException {
        DataOutputStream dos = new DataOutputStream(stream);
        try {
            dos.writeInt(data);
        } finally {
            try {
                dos.close();
            } finally {
                stream.close();
            }
        }
    }

    private static byte[] readStreamAsByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int read = 0;
        byte[] data = new byte[1024];
        try {
            while ((read = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, read);
            }
        } finally {
            inputStream.close();
        }
        return buffer.toByteArray();
    }


    private static int readStreamAsInt(InputStream inputStream) throws IOException {
        DataInputStream buffer = new DataInputStream(inputStream);
        try {
            return buffer.readInt();
        } finally {
            inputStream.close();
        }
    }

}
