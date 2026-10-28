"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { formatDay } from "@/lib/format";
import { formatMoney } from "@/lib/money";

import { useChartColors } from "./use-chart-colors";

export type DailyFlow = { date: string; inflow: number; outflow: number };

/** 30-day cash flow: money in (emerald) vs money out (neutral grey; outgoing money isn't an error). */
export function CashFlowChart({ data, currency }: { data: DailyFlow[]; currency: string }) {
  const c = useChartColors();
  const compact = (minor: number) => {
    const units = Math.round(minor / 100);
    return units >= 100_000
      ? `${Math.round(units / 1000)}k`
      : units >= 1000
        ? `${(units / 1000).toFixed(1)}k`
        : `${units}`;
  };

  return (
    <div className="h-full min-h-56 w-full" role="img" aria-label="Daily money in and out over the last 30 days">
      <ResponsiveContainer>
        <BarChart data={data} barGap={2} margin={{ top: 8, right: 4, bottom: 0, left: -8 }}>
          <CartesianGrid vertical={false} stroke={c.line} strokeDasharray="3 3" />
          <XAxis
            dataKey="date"
            tickFormatter={formatDay}
            tick={{ fill: c.text, fontSize: 11 }}
            tickLine={false}
            axisLine={false}
            interval={6}
          />
          <YAxis
            tickFormatter={compact}
            tick={{ fill: c.text, fontSize: 11 }}
            tickLine={false}
            axisLine={false}
            width={44}
          />
          <Tooltip
            cursor={{ fill: c.line, opacity: 0.4 }}
            content={({ active, payload, label }) =>
              active && payload?.length ? (
                <div className="rounded-lg border border-border bg-popover px-3 py-2 text-xs shadow-md">
                  <div className="mb-1 font-medium">{formatDay(String(label))}</div>
                  <div className="flex justify-between gap-6">
                    <span className="text-muted-foreground">In</span>
                    <span className="num text-credit-text">
                      {formatMoney(Number(payload[0]?.value ?? 0), currency)}
                    </span>
                  </div>
                  <div className="flex justify-between gap-6">
                    <span className="text-muted-foreground">Out</span>
                    <span className="num">{formatMoney(Number(payload[1]?.value ?? 0), currency)}</span>
                  </div>
                </div>
              ) : null
            }
          />
          <Bar
            dataKey="inflow"
            name="In"
            fill={c.credit}
            radius={[3, 3, 0, 0]}
            maxBarSize={10}
            isAnimationActive={false}
          />
          <Bar
            dataKey="outflow"
            name="Out"
            fill={c.muted}
            radius={[3, 3, 0, 0]}
            maxBarSize={10}
            isAnimationActive={false}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
