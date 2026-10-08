"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

import { Toaster } from "@/components/toasts/toaster";
import { ApiError } from "@/lib/api/errors";
import { useClockTicker } from "@/stores/clock-store";

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Realtime messages invalidate what changed, so polling is never needed.
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        retry: (failureCount, error) => {
          // Client errors (401/403/404/422) will not succeed on retry.
          if (error instanceof ApiError && error.status < 500) return false;
          return failureCount < 2;
        },
      },
      mutations: { retry: false },
    },
  });
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(makeQueryClient);
  useClockTicker();
  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <Toaster />
    </QueryClientProvider>
  );
}
