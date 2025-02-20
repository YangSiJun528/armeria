/*
 * Copyright 2023 LINE Corporation
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

import java.net.URI;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import com.google.protobuf.Duration;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;

import com.linecorp.armeria.common.annotation.Nullable;

import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap.DynamicResources;
import io.envoyproxy.envoy.config.bootstrap.v3.Bootstrap.StaticResources;
import io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.envoyproxy.envoy.config.cluster.v3.Cluster.DiscoveryType;
import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.config.core.v3.AggregatedConfigSource;
import io.envoyproxy.envoy.config.core.v3.ApiConfigSource;
import io.envoyproxy.envoy.config.core.v3.ApiConfigSource.ApiType;
import io.envoyproxy.envoy.config.core.v3.ApiVersion;
import io.envoyproxy.envoy.config.core.v3.ConfigSource;
import io.envoyproxy.envoy.config.core.v3.GrpcService;
import io.envoyproxy.envoy.config.core.v3.GrpcService.EnvoyGrpc;
import io.envoyproxy.envoy.config.core.v3.Metadata;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.config.core.v3.TransportSocket;
import io.envoyproxy.envoy.config.endpoint.v3.ClusterLoadAssignment;
import io.envoyproxy.envoy.config.endpoint.v3.Endpoint;
import io.envoyproxy.envoy.config.endpoint.v3.LbEndpoint;
import io.envoyproxy.envoy.config.endpoint.v3.LocalityLbEndpoints;
import io.envoyproxy.envoy.config.listener.v3.ApiListener;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import io.envoyproxy.envoy.config.route.v3.Route;
import io.envoyproxy.envoy.config.route.v3.RouteAction;
import io.envoyproxy.envoy.config.route.v3.RouteConfiguration;
import io.envoyproxy.envoy.config.route.v3.RouteMatch;
import io.envoyproxy.envoy.config.route.v3.VirtualHost;
import io.envoyproxy.envoy.extensions.filters.http.router.v3.Router;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager.CodecType;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpFilter;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.Rds;
import io.envoyproxy.envoy.extensions.transport_sockets.tls.v3.UpstreamTlsContext;

public final class XdsTestResources {

    static final String BOOTSTRAP_CLUSTER_NAME = "bootstrap-cluster";

    private XdsTestResources() {}

    public static LbEndpoint endpoint(String address, int port) {
        return endpoint(address, port, Metadata.getDefaultInstance());
    }

    public static LbEndpoint endpoint(String address, int port, Metadata metadata) {
        final SocketAddress socketAddress = SocketAddress.newBuilder()
                                                         .setAddress(address)
                                                         .setPortValue(port)
                                                         .build();
        return LbEndpoint
                .newBuilder()
                .setMetadata(metadata)
                .setEndpoint(Endpoint.newBuilder()
                                     .setAddress(Address.newBuilder()
                                                        .setSocketAddress(socketAddress)
                                                        .build())
                                     .build()).build();
    }

    public static ClusterLoadAssignment loadAssignment(String clusterName, URI uri) {
        return loadAssignment(clusterName, uri.getHost(), uri.getPort());
    }

    public static ClusterLoadAssignment loadAssignment(String clusterName, String address, int port) {
        return ClusterLoadAssignment.newBuilder()
                                    .setClusterName(clusterName)
                                    .addEndpoints(LocalityLbEndpoints.newBuilder()
                                                                     .addLbEndpoints(endpoint(address, port)))
                                    .build();
    }

    public static Cluster bootstrapCluster(URI uri, String bootstrapClusterName) {
        final ClusterLoadAssignment loadAssignment =
                loadAssignment(bootstrapClusterName, uri.getHost(), uri.getPort());
        return createStaticCluster(bootstrapClusterName, loadAssignment);
    }

    public static Bootstrap bootstrap(URI uri) {
        return bootstrap(uri, BOOTSTRAP_CLUSTER_NAME);
    }

    public static Bootstrap bootstrap(URI uri, String clusterName) {
        final Cluster cluster = bootstrapCluster(uri, clusterName);
        final ConfigSource configSource = basicConfigSource(clusterName);
        return bootstrap(configSource, cluster);
    }

    public static Bootstrap bootstrap(@Nullable ApiConfigSource adsConfigSource,
                                      @Nullable ConfigSource basicConfigSource,
                                      Cluster... cluster) {
        final DynamicResources.Builder dynamicResources = DynamicResources.newBuilder();
        if (adsConfigSource != null) {
            dynamicResources.setAdsConfig(adsConfigSource);
        }
        if (basicConfigSource != null) {
            dynamicResources.setCdsConfig(basicConfigSource);
            dynamicResources.setLdsConfig(basicConfigSource);
        }
        return Bootstrap
                .newBuilder()
                .setStaticResources(
                        StaticResources.newBuilder()
                                       .addAllClusters(ImmutableSet.copyOf(cluster)))
                .setDynamicResources(dynamicResources)
                .build();
    }

    public static Bootstrap bootstrap(ConfigSource configSource, Cluster cluster) {
        return bootstrap(configSource, Listener.getDefaultInstance(), cluster);
    }

    public static Bootstrap bootstrap(ConfigSource configSource, Listener listener, Cluster... cluster) {
        final StaticResources.Builder staticResourceBuilder = StaticResources.newBuilder();
        if (listener != Listener.getDefaultInstance()) {
            staticResourceBuilder.addListeners(listener);
        }
        staticResourceBuilder.addAllClusters(ImmutableList.copyOf(cluster));
        return Bootstrap
                .newBuilder()
                .setStaticResources(staticResourceBuilder.build())
                .setDynamicResources(DynamicResources
                                             .newBuilder()
                                             .setCdsConfig(configSource)
                                             .setAdsConfig(configSource.getApiConfigSource())
                )
                .build();
    }

    public static ConfigSource adsConfigSource() {
        return ConfigSource
                .newBuilder()
                .setAds(AggregatedConfigSource.getDefaultInstance())
                .build();
    }

    public static ConfigSource basicConfigSource(String clusterName) {
        return ConfigSource
                .newBuilder()
                .setApiConfigSource(apiConfigSource(clusterName, ApiType.GRPC))
                .build();
    }

    public static ApiConfigSource apiConfigSource(String clusterName, ApiType apiType) {
        return ApiConfigSource
                .newBuilder()
                .addGrpcServices(
                        GrpcService
                                .newBuilder()
                                .setEnvoyGrpc(EnvoyGrpc.newBuilder()
                                                       .setClusterName(clusterName)))
                .setApiType(apiType)
                .build();
    }

    public static Cluster createCluster(String clusterName) {
        return createCluster(clusterName, 5);
    }

    public static Cluster createCluster(String clusterName, int connectTimeoutSeconds) {
        final ConfigSource edsSource =
                ConfigSource.newBuilder()
                            .setInitialFetchTimeout(Duration.newBuilder().setSeconds(0))
                            .setAds(AggregatedConfigSource.getDefaultInstance())
                            .setResourceApiVersion(ApiVersion.V3)
                            .build();
        return createCluster(clusterName, edsSource, connectTimeoutSeconds);
    }

    public static Cluster createCluster(String clusterName, ConfigSource configSource) {
        return createCluster(clusterName, configSource, 5);
    }

    public static Cluster createCluster(String clusterName, ConfigSource configSource,
                                        int connectTimeoutSeconds) {
        return Cluster.newBuilder()
                      .setName(clusterName)
                      .setConnectTimeout(Durations.fromSeconds(connectTimeoutSeconds))
                      .setEdsClusterConfig(
                              Cluster.EdsClusterConfig.newBuilder()
                                                      .setEdsConfig(configSource)
                                                      .setServiceName(clusterName))
                      .setType(Cluster.DiscoveryType.EDS)
                      .build();
    }

    public static Cluster createStaticCluster(String clusterName, ClusterLoadAssignment loadAssignment) {
        return Cluster.newBuilder()
                      .setName(clusterName)
                      .setLoadAssignment(loadAssignment)
                      .setType(DiscoveryType.STATIC)
                      .build();
    }

    public static Cluster createTlsStaticCluster(String clusterName, ClusterLoadAssignment loadAssignment) {
        return Cluster.newBuilder()
                      .setName(clusterName)
                      .setLoadAssignment(loadAssignment)
                      .setType(DiscoveryType.STATIC)
                      .setTransportSocket(TransportSocket.newBuilder()
                                                         .setName("envoy.transport_sockets.tls")
                                                         .setTypedConfig(Any.pack(
                                                                 UpstreamTlsContext.getDefaultInstance())))
                      .build();
    }

    public static Listener exampleListener(String listenerName, String routeName) {
        final HttpConnectionManager manager =
                HttpConnectionManager
                        .newBuilder()
                        .setCodecType(CodecType.AUTO)
                        .setStatPrefix("ingress_http")
                        .setRds(Rds.newBuilder().setRouteConfigName(routeName))
                        .addHttpFilters(HttpFilter.newBuilder()
                                                  .setName("envoy.filters.http.router")
                                                  .setTypedConfig(Any.pack(Router.getDefaultInstance())))
                        .build();
        return Listener.newBuilder()
                       .setName(listenerName)
                       .setApiListener(ApiListener.newBuilder()
                                                  .setApiListener(Any.pack(manager)))
                       .build();
    }

    public static Listener exampleListener(String listenerName, String routeName, String clusterName) {
        final ConfigSource configSource = basicConfigSource(clusterName);
        final HttpConnectionManager manager =
                HttpConnectionManager
                        .newBuilder()
                        .setCodecType(CodecType.AUTO)
                        .setStatPrefix("ingress_http")
                        .setRds(Rds.newBuilder()
                                   .setRouteConfigName(routeName)
                                   .setConfigSource(configSource))
                        .addHttpFilters(HttpFilter.newBuilder()
                                                  .setName("envoy.filters.http.router")
                                                  .setTypedConfig(Any.pack(Router.getDefaultInstance())))
                        .build();
        return Listener.newBuilder()
                       .setName(listenerName)
                       .setApiListener(ApiListener.newBuilder()
                                                  .setApiListener(Any.pack(manager)))
                       .build();
    }

    public static RouteConfiguration routeConfiguration(String routeName, VirtualHost... virtualHosts) {
        return RouteConfiguration.newBuilder()
                                 .setName(routeName)
                                 .addAllVirtualHosts(ImmutableList.copyOf(virtualHosts))
                                 .build();
    }

    public static RouteConfiguration routeConfiguration(String routeName, String clusterName) {
        return routeConfiguration(routeName, virtualHost(routeName, clusterName));
    }

    public static VirtualHost virtualHost(String name, String... clusterNames) {
        final VirtualHost.Builder builder =
                VirtualHost.newBuilder().setName(name).addDomains("*");
        for (String clusterName: clusterNames) {
            builder.addRoutes(Route.newBuilder()
                                   .setMatch(RouteMatch.newBuilder().setPrefix("/"))
                                   .setRoute(RouteAction.newBuilder()
                                                        .setCluster(clusterName)));
        }
        return builder.build();
    }

    public static Value stringValue(String value) {
        return Value.newBuilder().setStringValue(value).build();
    }
}
