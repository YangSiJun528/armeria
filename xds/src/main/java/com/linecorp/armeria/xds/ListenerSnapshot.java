/*
 * Copyright 2024 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.xds;

import com.google.common.base.MoreObjects;

import com.linecorp.armeria.common.annotation.Nullable;
import com.linecorp.armeria.common.annotation.UnstableApi;

import io.envoyproxy.envoy.config.listener.v3.Listener;

/**
 * A snapshot of a {@link Listener} resource.
 */
@UnstableApi
public final class ListenerSnapshot implements Snapshot<ListenerXdsResource> {

    private final ListenerXdsResource listenerXdsResource;
    @Nullable
    private final RouteSnapshot routeSnapshot;

    ListenerSnapshot(ListenerXdsResource listenerXdsResource, @Nullable RouteSnapshot routeSnapshot) {
        this.listenerXdsResource = listenerXdsResource;
        this.routeSnapshot = routeSnapshot;
    }

    @Override
    public ListenerXdsResource xdsResource() {
        return listenerXdsResource;
    }

    /**
     * A {@link RouteSnapshot} which belong to this {@link Listener}.
     */
    @Nullable
    public RouteSnapshot routeSnapshot() {
        return routeSnapshot;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .omitNullValues()
                          .add("listenerXdsResource", listenerXdsResource)
                          .add("routeSnapshot", routeSnapshot)
                          .toString();
    }
}
