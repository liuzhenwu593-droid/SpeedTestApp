package com.shadiao.nb.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.shadiao.nb.R
import com.shadiao.nb.data.ProxyNode
import com.shadiao.nb.util.PrefsHelper
import com.shadiao.nb.util.SpeedTestUtil
import com.shadiao.nb.util.SubscriptionManager
import kotlinx.coroutines.launch

class NodeListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: NodeAdapter
    private lateinit var subscriptionManager: SubscriptionManager
    private var nodes: MutableList<ProxyNode> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node_list)

        subscriptionManager = SubscriptionManager(this)

        // 从 Intent 获取节点
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val receivedNodes = intent.getSerializableExtra("nodes") as? ArrayList<ProxyNode>
        if (receivedNodes != null) {
            nodes = receivedNodes.toMutableList()
        } else {
            // 从缓存加载
            nodes = subscriptionManager.loadFromCache().toMutableList()
        }

        initViews()

        // 打开节点列表时自动测速
        if (PrefsHelper.isAutoSpeedTestEnabled() && nodes.isNotEmpty()) {
            startSpeedTest()
        }
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.node_list)

        recyclerView = findViewById(R.id.recycler_nodes)
        swipeRefresh = findViewById(R.id.swipe_refresh)

        adapter = NodeAdapter(nodes) { node ->
            // 选中节点
            PrefsHelper.setSelectedNode(node.id)
            val intent = Intent().apply {
                putExtra("selected_node_id", node.id)
                putExtra("selected_node_name", node.name)
            }
            setResult(RESULT_OK, intent)
            finish()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            startSpeedTest()
        }

        findViewById<MaterialButton>(R.id.btn_test_all).setOnClickListener {
            startSpeedTest()
        }

        findViewById<MaterialButton>(R.id.btn_update).setOnClickListener {
            updateSubscription()
        }
    }

    private fun startSpeedTest() {
        if (nodes.isEmpty()) return
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            SpeedTestUtil.testAll(nodes) { node ->
                runOnUiThread {
                    val index = nodes.indexOfFirst { it.id == node.id }
                    if (index >= 0) {
                        nodes[index] = node
                        adapter.notifyItemChanged(index)
                    }
                }
            }
            swipeRefresh.isRefreshing = false
        }
    }

    private fun updateSubscription() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val result = subscriptionManager.fetchSubscription(PrefsHelper.getSubscriptionUrl())
            swipeRefresh.isRefreshing = false
            if (result.isSuccess) {
                nodes.clear()
                nodes.addAll(result.getOrDefault(emptyList()))
                adapter.notifyDataSetChanged()
                startSpeedTest()
            }
        }
    }

    // --- Adapter ---

    private class NodeAdapter(
        private val nodes: List<ProxyNode>,
        private val onClick: (ProxyNode) -> Unit
    ) : RecyclerView.Adapter<NodeAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_name)
            val tvAddress: TextView = view.findViewById(R.id.tv_address)
            val tvProtocol: TextView = view.findViewById(R.id.tv_protocol)
            val tvLatency: TextView = view.findViewById(R.id.tv_latency)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_node, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val node = nodes[position]
            holder.tvName.text = node.name
            holder.tvAddress.text = "${node.address}:${node.port}"
            holder.tvProtocol.text = node.protocol

            when {
                node.latency == -1 -> {
                    holder.tvLatency.text = "—"
                    holder.tvLatency.setTextColor(0xFF999999.toInt())
                }
                node.latency == -2 -> {
                    holder.tvLatency.text = "超时"
                    holder.tvLatency.setTextColor(0xFFEF5350.toInt())
                }
                node.latency == 0 -> {
                    holder.tvLatency.text = "..."
                    holder.tvLatency.setTextColor(0xFF999999.toInt())
                }
                else -> {
                    holder.tvLatency.text = "${node.latency}ms"
                    val color = when {
                        node.latency < 200 -> 0xFF4CAF50.toInt()
                        node.latency < 500 -> 0xFFFF9800.toInt()
                        else -> 0xFFEF5350.toInt()
                    }
                    holder.tvLatency.setTextColor(color)
                }
            }

            val selectedId = PrefsHelper.getSelectedNode()
            holder.itemView.alpha = if (selectedId == node.id) 1.0f else 0.7f

            holder.itemView.setOnClickListener { onClick(node) }
        }

        override fun getItemCount() = nodes.size
    }
}
