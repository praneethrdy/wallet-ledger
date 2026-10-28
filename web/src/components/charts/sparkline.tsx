"use client";

import { useId } from "react";
import { Area, AreaChart, ResponsiveContainer, Tooltip, YAxis } from "recharts";

import { formatDay } from "@/lib/format";
import { formatMoney } from "@/lib/money";

import { useChartColors } from "./use-chart-colors";

export type BalancePoint = { date: string; balance: number };

/** Daily closing balance, drawn in the accent colour (the "key chart line"). */
export function Sparkline({
  data,
  currency,
  height = 64,
}: {
  data: BalancePoint[];
  currency: string;
  height?: number;
}) {
  const c = useChartColors();
  const gradientId = `spark-${useId().replace(/:/g, "")}`;

  return (
    <div style={{ height }} className="w-full" role="img" aria-label="Balance over the last 30 days">
      <ResponsiveContainer>
        <AreaChart data={data} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={c.brand} stopOpacity={0.22} />
              <stop offset="100%" stopColor={c.brand} stopOpacity={0} />
            </linearGradient>
          </defs>
          <YAxis hide domain={["dataMin", "dataMax"]} />
          <Tooltip
            cursor={{ stroke: c.line }}
            content={({ active, payload }) =>
              active && payload?.length ? (
                <div className="rounded-lg border border-border bg-popover px-2.5 py-1.5 text-xs shadow-md">
                  <div className="text-muted-foreground">{formatDay(String(payload[0].payload.date))}</div>
                  <div className="num font-medium">{formatMoney(Number(payload[0].value), currency)}</div>
                </div>
              ) : null
            }
          />
          <Area
            type="monotone"
            dataKey="balance"
            stroke={c.brand}
            strokeWidth={1.75}
            fill={`url(#${gradientId})`}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
