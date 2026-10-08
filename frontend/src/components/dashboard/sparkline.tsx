"use client";

import { useState } from "react";

import { useElementWidth } from "@/hooks/use-element-width";
import type { VolumePoint } from "@/lib/api/types";
import { formatHour } from "@/lib/format";

const HEIGHT = 40;
const PAD = 4;

/**
 * Events per minute for the last hour: a quiet line in the de-emphasis ink, the current minute
 * marked in the accent. Hover shows the exact minute and count.
 */
export function VolumeSparkline({ points }: { points: VolumePoint[] }) {
  const [ref, width] = useElementWidth<HTMLDivElement>();
  const [active, setActive] = useState<number | null>(null);
  const max = Math.max(1, ...points.map((p) => p.total));
  const x = (i: number) => PAD + (i / Math.max(1, points.length - 1)) * (width - PAD * 2);
  const y = (v: number) => HEIGHT - PAD - (v / max) * (HEIGHT - PAD * 2);
  const line = points.map((p, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(p.total).toFixed(1)}`).join(" ");
  const area = `${line} L${x(points.length - 1).toFixed(1)},${HEIGHT - PAD} L${x(0).toFixed(1)},${HEIGHT - PAD} Z`;
  const last = points.length - 1;
  const shown = active ?? last;

  return (
    <div>
      <div ref={ref} className="relative" style={{ height: HEIGHT }}>
        {width > 0 && points.length > 1 && (
          <svg
            width={width}
            height={HEIGHT}
            role="img"
            aria-label="Events per minute over the last hour"
            onPointerMove={(e) => {
              const rect = e.currentTarget.getBoundingClientRect();
              const ratio = (e.clientX - rect.left - PAD) / Math.max(1, rect.width - PAD * 2);
              setActive(Math.min(last, Math.max(0, Math.round(ratio * last))));
            }}
            onPointerLeave={() => setActive(null)}
            className="cursor-crosshair"
          >
            <path d={area} fill="var(--color-fg-subtle)" opacity={0.1} />
            <path d={line} fill="none" stroke="var(--color-fg-subtle)" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
            {active !== null && (
              <line x1={x(active)} x2={x(active)} y1={PAD} y2={HEIGHT - PAD} stroke="var(--color-line-strong)" strokeWidth={1} />
            )}
            <circle
              cx={x(shown)}
              cy={y(points[shown].total)}
              r={4}
              fill={active === null ? "var(--color-accent)" : "var(--color-fg)"}
              stroke="var(--color-surface)"
              strokeWidth={2}
            />
          </svg>
        )}
      </div>
      <p className="tabular mt-1 h-4 text-[11px] text-fg-subtle">
        {points.length > 0 &&
          (active === null
            ? "Events per minute · last 60 min"
            : `${formatHour(points[active].bucketStart)} · ${points[active].total} events, ${points[active].problems} problems`)}
      </p>
    </div>
  );
}
