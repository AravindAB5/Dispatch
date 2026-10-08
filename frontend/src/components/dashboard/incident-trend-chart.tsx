"use client";

import { useState } from "react";

import { useElementWidth } from "@/hooks/use-element-width";
import type { TrendPoint } from "@/lib/api/types";
import { columnPath, niceScale } from "@/lib/chart";
import { formatHour } from "@/lib/format";

/*
 * Incidents opened per hour, stacked as "critical & high" (anchored to the baseline, so the
 * important share is directly comparable hour to hour) and "medium & low", with incidents resolved
 * per hour as a line. Colours were validated for CVD separation on the panel surface; identity is
 * also carried by the legend, the mark type (bar vs line) and the tooltip.
 */

const COLORS = {
  severe: "var(--color-series-severe)",
  routine: "var(--color-series-routine)",
  resolved: "var(--color-series-resolved)",
  grid: "var(--color-line)",
  surface: "var(--color-surface)",
};

const HEIGHT = 220;
const MARGIN = { top: 10, right: 8, bottom: 24, left: 30 };
const GAP = 2;

type Row = { start: string; severe: number; routine: number; resolved: number };

function toRows(points: TrendPoint[]): Row[] {
  return points.map((p) => ({
    start: p.bucketStart,
    severe: p.critical + p.high,
    routine: p.medium + p.low,
    resolved: p.resolved,
  }));
}

export function IncidentTrendChart({ points, view }: { points: TrendPoint[]; view: "chart" | "table" }) {
  const rows = toRows(points);
  if (view === "table") return <TrendTable rows={rows} />;
  return <TrendSvg rows={rows} />;
}

export function TrendLegend() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-fg-muted">
      <span className="inline-flex items-center gap-1.5">
        <span className="size-2.5 rounded-[2px]" style={{ background: COLORS.severe }} aria-hidden />
        Critical &amp; high
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="size-2.5 rounded-[2px]" style={{ background: COLORS.routine }} aria-hidden />
        Medium &amp; low
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="h-0.5 w-3.5 rounded-full" style={{ background: COLORS.resolved }} aria-hidden />
        Resolved
      </span>
    </div>
  );
}

function TrendSvg({ rows }: { rows: Row[] }) {
  const [containerRef, width] = useElementWidth<HTMLDivElement>();
  const [active, setActive] = useState<number | null>(null);

  const innerWidth = Math.max(0, width - MARGIN.left - MARGIN.right);
  const innerHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
  const maxValue = Math.max(0, ...rows.map((r) => Math.max(r.severe + r.routine, r.resolved)));
  const { max, step } = niceScale(maxValue, 4);
  const y = (value: number) => MARGIN.top + innerHeight - (value / max) * innerHeight;
  const band = rows.length ? innerWidth / rows.length : 0;
  const barWidth = Math.max(2, Math.min(24, band * 0.62));
  const center = (i: number) => MARGIN.left + band * i + band / 2;
  const labelEvery = band < 22 ? 6 : band < 34 ? 3 : 2;
  const ticks = Array.from({ length: Math.floor(max / step) + 1 }, (_, i) => i * step);
  const empty = maxValue === 0;

  const linePath = rows
    .map((r, i) => `${i === 0 ? "M" : "L"}${center(i).toFixed(1)},${y(r.resolved).toFixed(1)}`)
    .join(" ");

  const activeRow = active !== null ? rows[active] : null;
  const tooltipLeft = active !== null ? Math.min(Math.max(center(active), 90), width - 90) : 0;

  return (
    <div ref={containerRef} className="relative w-full min-w-0 overflow-hidden" style={{ height: HEIGHT }}>
      {width > 0 && (
        <svg
          width={width}
          height={HEIGHT}
          role="img"
          aria-label={`Incidents opened and resolved per hour over the last ${rows.length} hours`}
          onPointerLeave={() => setActive(null)}
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(tick)} y2={y(tick)} stroke={COLORS.grid} strokeWidth={1} />
              <text x={MARGIN.left - 8} y={y(tick)} dy="0.32em" textAnchor="end" className="tabular fill-fg-subtle text-[10px]">
                {tick}
              </text>
            </g>
          ))}

          {rows.map((row, i) => {
            const x = center(i) - barWidth / 2;
            const severeTop = y(row.severe);
            const severeHeight = y(0) - severeTop;
            const routineHeight = (row.routine / max) * innerHeight;
            const routineBottom = severeTop - (row.severe > 0 && row.routine > 0 ? GAP : 0);
            const dimmed = active !== null && active !== i;
            return (
              <g key={row.start} opacity={dimmed ? 0.45 : 1} style={{ transition: "opacity 120ms" }}>
                {row.severe > 0 && (
                  <path
                    d={columnPath(x, severeTop, barWidth, severeHeight, row.routine > 0 ? 0 : 4)}
                    fill={COLORS.severe}
                  />
                )}
                {row.routine > 0 && (
                  <path d={columnPath(x, routineBottom - routineHeight, barWidth, routineHeight, 4)} fill={COLORS.routine} />
                )}
              </g>
            );
          })}

          <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(0)} y2={y(0)} stroke="var(--color-line-strong)" strokeWidth={1} />

          {!empty && (
            <path d={linePath} fill="none" stroke={COLORS.resolved} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
          )}
          {activeRow && (
            <circle cx={center(active!)} cy={y(activeRow.resolved)} r={4} fill={COLORS.resolved} stroke={COLORS.surface} strokeWidth={2} />
          )}

          {rows.map((row, i) =>
            i % labelEvery === (rows.length - 1) % labelEvery ? (
              <text key={row.start} x={center(i)} y={HEIGHT - 6} textAnchor="middle" className="tabular fill-fg-subtle text-[10px]">
                {formatHour(row.start)}
              </text>
            ) : null,
          )}

          {/* Hit targets: the whole hour band, larger than the mark, focusable for keyboard users. */}
          {rows.map((row, i) => (
            <rect
              key={row.start}
              x={MARGIN.left + band * i}
              y={MARGIN.top}
              width={band}
              height={innerHeight}
              fill="transparent"
              tabIndex={0}
              aria-label={`${formatHour(row.start)}: ${row.severe} critical or high, ${row.routine} medium or low opened, ${row.resolved} resolved`}
              onPointerEnter={() => setActive(i)}
              onPointerMove={() => setActive(i)}
              onFocus={() => setActive(i)}
              onBlur={() => setActive(null)}
              className="cursor-crosshair outline-none"
            />
          ))}
        </svg>
      )}

      {empty && width > 0 && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center pb-6 text-sm text-fg-subtle">
          No incidents opened in this window
        </div>
      )}

      {activeRow && (
        <div
          className="pointer-events-none absolute top-0 z-10 w-44 -translate-x-1/2 rounded-md border border-line-strong bg-surface-2/95 px-3 py-2 text-xs shadow-lg shadow-black/40 backdrop-blur"
          style={{ left: tooltipLeft }}
        >
          <p className="mb-1.5 text-fg-muted">
            {formatHour(activeRow.start)}–{formatHour(new Date(new Date(activeRow.start).getTime() + 3_600_000).toISOString())}
          </p>
          <TooltipRow color={COLORS.severe} label="Critical & high" value={activeRow.severe} />
          <TooltipRow color={COLORS.routine} label="Medium & low" value={activeRow.routine} />
          <TooltipRow color={COLORS.resolved} label="Resolved" value={activeRow.resolved} line />
        </div>
      )}
    </div>
  );
}

function TooltipRow({ color, label, value, line }: { color: string; label: string; value: number; line?: boolean }) {
  return (
    <div className="flex items-center gap-2 py-0.5">
      <span className="w-3 shrink-0 rounded-full" style={{ background: color, height: line ? 2 : 3 }} aria-hidden />
      <span className="tabular min-w-5 font-semibold text-fg">{value}</span>
      <span className="text-fg-muted">{label}</span>
    </div>
  );
}

function TrendTable({ rows }: { rows: Row[] }) {
  return (
    <div className="max-h-[220px] overflow-y-auto">
      <table className="w-full text-xs">
        <thead className="sticky top-0 bg-surface text-left text-fg-subtle">
          <tr>
            <th className="py-1.5 font-medium">Hour</th>
            <th className="py-1.5 text-right font-medium">Critical &amp; high</th>
            <th className="py-1.5 text-right font-medium">Medium &amp; low</th>
            <th className="py-1.5 text-right font-medium">Resolved</th>
          </tr>
        </thead>
        <tbody className="tabular divide-y divide-line">
          {[...rows].reverse().map((row) => (
            <tr key={row.start}>
              <td className="py-1.5 text-fg-muted">{formatHour(row.start)}</td>
              <td className="py-1.5 text-right text-fg">{row.severe}</td>
              <td className="py-1.5 text-right text-fg">{row.routine}</td>
              <td className="py-1.5 text-right text-fg">{row.resolved}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
