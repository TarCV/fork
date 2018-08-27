package com.shazam.fork.pooling;

import com.shazam.fork.model.Pool;

import java.util.Collection;

public interface PoolLoader {
    Collection<Pool> loadPools() throws NoDevicesForPoolException, NoPoolLoaderConfiguredException;
}
