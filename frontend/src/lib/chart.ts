/** A "nice" axis maximum and step (1, 2, 5 × 10^n) with roughly `ticks` intervals. */
export function niceScale(maxValue: number, ticks = 4): { max: number; step: number } {
  if (maxValue <= 0) return { max: ticks, step: 1 };
  const rough = maxValue / ticks;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const residual = rough / magnitude;
  const niceResidual = residual <= 1 ? 1 : residual <= 2 ? 2 : residual <= 5 ? 5 : 10;
  const step = Math.max(1, niceResidual * magnitude);
  return { max: Math.ceil(maxValue / step) * step, step };
}

/** SVG path for a column with rounded top corners and a square base. */
export function columnPath(x: number, y: number, width: number, height: number, radius = 4): string {
  if (height <= 0) return "";
  const r = Math.min(radius, width / 2, height);
  return [
    `M${x},${y + height}`,
    `V${y + r}`,
    `Q${x},${y} ${x + r},${y}`,
    `H${x + width - r}`,
    `Q${x + width},${y} ${x + width},${y + r}`,
    `V${y + height}`,
    "Z",
  ].join(" ");
}
