"use client";

import { useQuery } from "@tanstack/react-query";

import { authApi } from "@/lib/api/endpoints";
import { qk } from "@/lib/query-keys";

export function useMe() {
  return useQuery({ queryKey: qk.me, queryFn: authApi.me, staleTime: 5 * 60_000 });
}

/** UI-level permission check. The API enforces the same rule on every request. */
export function useIsAdmin(): boolean {
  return useMe().data?.role === "ADMIN";
}
