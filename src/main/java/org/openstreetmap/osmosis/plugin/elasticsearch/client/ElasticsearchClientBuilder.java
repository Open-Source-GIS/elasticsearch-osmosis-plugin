package org.openstreetmap.osmosis.plugin.elasticsearch.client;

import java.util.List;
import java.util.logging.Logger;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

public class ElasticsearchClientBuilder {

	private static final Logger LOG = Logger.getLogger(ElasticsearchClientBuilder.class.getName());

	public String clusterName;
	public boolean isNodeClient;
	public String host;
	public int port;

	public Client build() {
		Client client = isNodeClient ? buildNodeClient() : buildTransportClient();
		// Check the cluster health
		ClusterHealthResponse health = client.admin().cluster()
				.health(new ClusterHealthRequest()).actionGet();
		if (health.getNumberOfDataNodes() == 0) throw new IllegalStateException("Unable to connect");
		LOG.info(String.format("Connected to %d data node(s) with cluster status %s",
				health.getNumberOfDataNodes(), health.getStatus().name()));
		return client;
	}

	protected Client buildNodeClient() {
		LOG.info(String.format("Connecting to elasticsearch cluster '%s' via [%s:%s]" +
				" using NodeClient", clusterName, host, port));
		// Connect as NodeClient (but PURE client)
		// See: https://gist.github.com/2491022 and http://www.elasticsearch.org/guide/reference/modules/discovery/zen.html
		Settings settings = ImmutableSettings.settingsBuilder()
				.put("node.master", false)
				.put("discovery.type", "zen")
				.put("discovery.zen.minimum_master_nodes", 1)
				.put("discovery.zen.ping.multicast.enabled", false)
				.putArray("discovery.zen.ping.unicast.hosts", host + ":" + port)
				.build();
		Node node = NodeBuilder.nodeBuilder()
				.settings(settings)
				.clusterName(clusterName)
				.client(true)
				.local(false)
				.node();
		return node.client();
	}

	protected Client buildTransportClient() {
		LOG.info(String.format("Connecting to elasticsearch cluster '%s' via [%s:%s]" +
				" using TransportClient", clusterName, host, port));
		Settings settings = ImmutableSettings.settingsBuilder()
				.put("cluster.name", clusterName)
				.put("client.transport.sniff", true)
				.build();
		TransportClient transportClient = new TransportClient(settings)
				.addTransportAddress(new InetSocketTransportAddress(host, port));
		List<DiscoveryNode> nodes = transportClient.connectedNodes();
		if (nodes.size() == 0) throw new IllegalStateException("Unable to connect");
		return transportClient;
	}

}
