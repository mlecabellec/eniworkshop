<script lang="ts">
  import { scaleLinear } from 'd3-scale';
  import { Activity } from 'lucide-svelte';
  import { workshopState } from '../lib/store.svelte';
  import type { FrameField, MappedFrame } from '../lib/types';

  let selectedField = $state<FrameField | null>(null);

  const CATEGORY_COLORS: Record<string, string> = {
    ETHERNET: '#8957e5',
    ECAT_HEADER: '#1f6feb',
    DATAGRAM_HEADER: '#0e7490',
    DATAGRAM_DATA: '#238636',
    WKC: '#d29922',
    PADDING: '#484f58',
    FCS: '#da3633'
  };

  function barScale(frame: MappedFrame) {
    return scaleLinear().domain([0, Math.max(frame.totalWireBytes, 64)]).range([0, 760]);
  }
</script>

<div class="panel-scroll">
  <div class="fm-hdr">
    <Activity size={14} />
    <strong>EtherCAT Ethernet Frame Byte-Map ({workshopState.frames.length} cyclic frames)</strong>
  </div>

  {#if workshopState.frames.length === 0}
    <p class="muted">No cyclic frames present in the selected ENI.</p>
  {:else}
    <div class="frames-list">
      {#each workshopState.frames as frame}
        {@const scale = barScale(frame)}
        <div class="card">
          <div class="frame-title">
            <strong>Frame #{frame.frameIndex}</strong>
            <span class="badge badge-info">{frame.cycleTimeUs} us</span>
            <span class="badge badge-ok">{frame.totalWireBytes} wire bytes</span>
            <span class="muted">ECAT payload: {frame.ethercatPayloadBytes} B</span>
          </div>

          <div class="svg-wrap">
            <svg viewBox="0 0 780 68" class="frame-svg" role="img" aria-label="EtherCAT Frame Map">
              {#each frame.fields as field}
                {@const x = 10 + scale(field.byteOffset)}
                {@const w = Math.max(4, scale(field.byteOffset + field.byteLength) - scale(field.byteOffset))}
                <g
                  class="field-rect"
                  tabindex="0"
                  role="button"
                  onclick={() => (selectedField = field)}
                  onkeydown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') selectedField = field;
                  }}>
                  <rect
                    {x}
                    y="10"
                    width={w}
                    height="34"
                    rx="3"
                    fill={CATEGORY_COLORS[field.category] ?? '#388bfd'}
                    stroke={selectedField === field ? '#ffffff' : '#0d1117'}
                    stroke-width={selectedField === field ? 2 : 1}>
                    <title>{field.name} ({field.byteOffset}..{field.byteOffset + field.byteLength - 1})</title>
                  </rect>
                  {#if w > 34}
                    <text x={x + 4} y="31" fill="#ffffff" font-size="9" font-family="monospace">
                      {field.name.slice(0, Math.floor(w / 6))}
                    </text>
                  {/if}
                </g>
              {/each}
              <text x="10" y="60" fill="#8b949e" font-size="9" font-family="monospace">0 B</text>
              <text x="730" y="60" fill="#8b949e" font-size="9" font-family="monospace">
                {frame.totalWireBytes} B
              </text>
            </svg>
          </div>
        </div>
      {/each}
    </div>

    {#if selectedField}
      <div class="card" style="margin-top:12px;">
        <div class="field-meta">
          <strong>{selectedField.name}</strong>
          <span class="badge badge-info">{selectedField.category}</span>
          <code>Offset {selectedField.byteOffset} B | Len {selectedField.byteLength} B</code>
        </div>
        <p class="detail-line">{selectedField.detail}</p>

        {#if selectedField.mappedVars.length > 0}
          <div class="var-table-wrap">
            <table class="var-table">
              <thead>
                <tr>
                  <th>Dir</th>
                  <th>Variable</th>
                  <th>Type</th>
                  <th>Rel Byte</th>
                  <th>BitInByte</th>
                  <th>Size (bits)</th>
                </tr>
              </thead>
              <tbody>
                {#each selectedField.mappedVars as v}
                  <tr>
                    <td><span class="badge badge-ok">{v.direction}</span></td>
                    <td>{v.name}</td>
                    <td><code>{v.dataType}</code></td>
                    <td>{v.relativeByteOffset}</td>
                    <td>{v.bitOffsetInByte}</td>
                    <td>{v.bitSize}</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
        {/if}
      </div>
    {/if}
  {/if}
</div>

<style>
  .fm-hdr {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
  }

  .frames-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .frame-title,
  .field-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }

  .svg-wrap {
    inline-size: 100%;
    overflow-x: auto;
  }

  .frame-svg {
    inline-size: 100%;
    min-inline-size: 560px;
    block-size: auto;
  }

  .field-rect {
    cursor: pointer;
  }

  .detail-line {
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--text-muted);
    margin: 4px 0 8px 0;
  }

  .var-table-wrap {
    max-block-size: 200px;
    overflow: auto;
  }

  .var-table {
    inline-size: 100%;
    border-collapse: collapse;
    font-size: 11px;
  }

  .var-table th,
  .var-table td {
    text-align: left;
    padding: 4px 8px;
    border-bottom: 1px solid var(--border-subtle);
  }

  .muted {
    color: var(--text-muted);
  }
</style>
