<script lang="ts">
  import { onMount } from 'svelte';
  import cytoscape from 'cytoscape';
  import {
    SvelteFlow,
    Background,
    Controls,
    MiniMap,
    type Node,
    type Edge
  } from '@xyflow/svelte';
  import { GitBranch, RefreshCw, ShieldCheck } from 'lucide-svelte';
  import { workshopState } from '../lib/store.svelte';

  let mode = $state<'cytoscape' | 'xyflow'>('cytoscape');
  let cyHost: HTMLDivElement | undefined = $state();
  let cyInstance: cytoscape.Core | null = null;

  let flowNodes = $state.raw<Node[]>([]);
  let flowEdges = $state.raw<Edge[]>([]);

  function rebuildCytoscape(): void {
    if (!cyHost || !workshopState.topology) return;
    if (cyInstance) {
      cyInstance.destroy();
      cyInstance = null;
    }

    const graph = workshopState.topology;
    const elements: cytoscape.ElementDefinition[] = [];

    for (const node of graph.nodes) {
      elements.push({
        data: {
          id: node.id,
          label:
            node.category === 'MASTER'
              ? `MASTER\n${node.label}`
              : `${node.physAddr}\n${node.label.slice(0, 18)}\n[${node.physics}]`,
          category: node.category,
          physAddr: node.physAddr,
          hc: node.hotConnectGroup || ''
        }
      });
    }

    for (const edge of graph.edges) {
      elements.push({
        data: {
          id: edge.id,
          source: edge.sourceId,
          target: edge.targetId,
          label: `${edge.sourcePort}->${edge.targetPort}`,
          linkType: edge.linkType
        }
      });
    }

    cyInstance = cytoscape({
      container: cyHost,
      elements,
      style: [
        {
          selector: 'node',
          style: {
            'background-color': '#1f6feb',
            'border-color': '#58a6ff',
            'border-width': 2,
            color: '#f0f6fc',
            label: 'data(label)',
            'text-wrap': 'wrap',
            'font-size': 10,
            'text-valign': 'center',
            'text-halign': 'center',
            shape: 'round-rectangle',
            width: 118,
            height: 46
          }
        },
        {
          selector: 'node[category="MASTER"]',
          style: {
            'background-color': '#8957e5',
            'border-color': '#d2a8ff',
            shape: 'hexagon',
            width: 130,
            height: 56
          }
        },
        {
          selector: 'node[category="COUPLER"]',
          style: {
            'background-color': '#0e7490',
            'border-color': '#39c5cf'
          }
        },
        {
          selector: 'node[category="EXTENSION_CARD"]',
          style: {
            'background-color': '#238636',
            'border-color': '#56d364'
          }
        },
        {
          selector: 'edge',
          style: {
            width: 2,
            'line-color': '#8b949e',
            'target-arrow-color': '#8b949e',
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            label: 'data(label)',
            'font-size': 9,
            color: '#c9d1d9',
            'text-background-color': '#0d1117',
            'text-background-opacity': 0.85
          }
        },
        {
          selector: 'edge[linkType="REDUNDANCY_RETURN"]',
          style: {
            'line-style': 'dashed',
            'line-color': '#f0883e',
            'target-arrow-color': '#f0883e',
            width: 2.5
          }
        }
      ],
      layout: {
        name: 'breadthfirst',
        directed: true,
        padding: 18,
        spacingFactor: 1.15
      }
    });

    cyInstance.on('tap', 'node', (evt) => {
      const phys = evt.target.data('physAddr') as number | undefined;
      if (typeof phys === 'number' && phys > 0) {
        workshopState.selectedPhysAddr = phys;
      }
    });
  }

  function rebuildFlowGraph(): void {
    const graph = workshopState.topology;
    if (!graph) {
      flowNodes = [];
      flowEdges = [];
      return;
    }
    const cols = 5;
    flowNodes = graph.nodes.slice(0, 120).map((n, idx) => ({
      id: n.id,
      position: {
        x: (idx % cols) * 210 + 30,
        y: Math.floor(idx / cols) * 105 + 30
      },
      data: {
        label:
          n.category === 'MASTER'
            ? `Master: ${n.label}`
            : `#${n.physAddr} ${n.label} (${n.physics})`
      },
      style:
        n.physAddr === workshopState.selectedPhysAddr
          ? 'background:#1f6feb;color:#fff;border:2px solid #79c0ff;border-radius:6px;padding:6px;font-size:11px;'
          : 'background:#161b22;color:#e6edf3;border:1px solid #30363d;border-radius:6px;padding:6px;font-size:11px;'
    }));

    const keptIds = new Set(flowNodes.map((n) => n.id));
    flowEdges = graph.edges
      .filter((e) => keptIds.has(e.sourceId) && keptIds.has(e.targetId))
      .map((e) => ({
        id: e.id,
        source: e.sourceId,
        target: e.targetId,
        label: `${e.sourcePort}->${e.targetPort}`,
        animated: e.linkType === 'REDUNDANCY_RETURN'
      }));
  }

  $effect(() => {
    void workshopState.topology;
    void workshopState.selectedPhysAddr;
    if (mode === 'cytoscape') {
      rebuildCytoscape();
    } else {
      rebuildFlowGraph();
    }
  });

  onMount(() => {
    rebuildCytoscape();
    return () => {
      if (cyInstance) cyInstance.destroy();
    };
  });
</script>

<div class="topo-root">
  <div class="topo-bar">
    <div class="topo-title">
      <GitBranch size={14} />
      <strong>EtherCAT Bus Topology</strong>
      {#if workshopState.topology}
        <span class="badge badge-info">{workshopState.topology.nodes.length - 1} slaves</span>
        {#if workshopState.topology.redundancyEnabled}
          <span class="badge badge-ok">
            <ShieldCheck size={12} /> Cable Redundancy
          </span>
        {/if}
        {#if workshopState.topology.hotConnectGroups.length > 0}
          <span class="badge badge-warn">
            {workshopState.topology.hotConnectGroups.length} HotConnect groups
          </span>
        {/if}
      {/if}
    </div>
    <div class="topo-actions">
      <button
        class="btn btn-sm"
        class:btn-primary={mode === 'cytoscape'}
        onclick={() => {
          mode = 'cytoscape';
        }}>
        Cytoscape Topology
      </button>
      <button
        class="btn btn-sm"
        class:btn-primary={mode === 'xyflow'}
        onclick={() => {
          mode = 'xyflow';
        }}>
        XYFlow Designer
      </button>
      {#if mode === 'cytoscape'}
        <button class="btn btn-sm" onclick={() => rebuildCytoscape()}>
          <RefreshCw size={12} /> Relayout
        </button>
      {/if}
    </div>
  </div>

  {#if !workshopState.topology}
    <div class="empty-msg">Select or import an ENI file to inspect its topology graph.</div>
  {:else if mode === 'cytoscape'}
    <div class="canvas-wrap" bind:this={cyHost}></div>
  {:else}
    <div class="canvas-wrap">
      <SvelteFlow
        nodes={flowNodes}
        edges={flowEdges}
        fitView
        onnodeclick={(ev) => {
          const matched = workshopState.topology?.nodes.find((n) => n.id === ev.node.id);
          if (matched && matched.physAddr > 0) {
            workshopState.selectedPhysAddr = matched.physAddr;
          }
        }}>
        <Background />
        <Controls />
        <MiniMap />
      </SvelteFlow>
    </div>
  {/if}
</div>

<style>
  .topo-root {
    display: flex;
    flex-direction: column;
    inline-size: 100%;
    block-size: 100%;
    min-inline-size: 0;
  }

  .topo-bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 8px 12px;
    border-bottom: 1px solid var(--border-subtle);
    background: #11161d;
  }

  .topo-title,
  .topo-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .canvas-wrap {
    flex: 1 1 auto;
    min-block-size: 200px;
    position: relative;
    background: #0d1117;
  }

  .empty-msg {
    padding: 24px;
    color: var(--text-muted);
  }
</style>
