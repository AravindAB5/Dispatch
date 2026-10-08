"use client";

import { useEffect, useRef } from "react";

import { useHighlightStamp } from "@/stores/realtime-store";

const FLASH = [{ backgroundColor: "rgba(139, 150, 255, 0.22)" }, { backgroundColor: "rgba(139, 150, 255, 0)" }];

/**
 * Briefly highlights an element when the entity it shows changes over the socket.
 * Uses the Web Animations API so the element is never re-mounted: remounting would steal focus
 * and swallow clicks on controls inside it (e.g. the incident action bar) mid-interaction.
 */
export function useFlash<T extends HTMLElement>(highlightKey: string) {
  const ref = useRef<T>(null);
  const stamp = useHighlightStamp(highlightKey);

  useEffect(() => {
    const element = ref.current;
    if (!element || !stamp || stamp < Date.now()) return;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return;
    element.animate?.(FLASH, { duration: 2400, easing: "ease-out" });
  }, [stamp]);

  return ref;
}
