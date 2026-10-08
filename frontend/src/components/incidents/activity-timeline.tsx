"use client";

import {
  CircleCheck,
  CircleCheckBig,
  Gauge,
  RotateCcw,
  RotateCw,
  Search,
  Siren,
  Undo2,
  UserCheck,
  UserMinus,
  CircleDot,
} from "lucide-react";
import { useState, type FormEvent, type KeyboardEvent } from "react";

import { Avatar } from "@/components/status/avatar";
import { RelativeTime } from "@/components/status/relative-time";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/form";
import { InlineError } from "@/components/ui/states";
import { ApiError } from "@/lib/api/errors";
import type { HistoryAction, HistoryEntry } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import type { ActivityItem } from "@/lib/incidents";

const ICONS: Record<HistoryAction, { Icon: typeof Siren; tone: string }> = {
  CREATED: { Icon: Siren, tone: "text-sev-critical" },
  STATUS_CHANGED: { Icon: CircleDot, tone: "text-fg-muted" },
  SEVERITY_CHANGED: { Icon: Gauge, tone: "text-sev-high" },
  ASSIGNED: { Icon: UserCheck, tone: "text-accent" },
  UNASSIGNED: { Icon: UserMinus, tone: "text-fg-muted" },
  COMMENT_ADDED: { Icon: CircleDot, tone: "text-fg-muted" },
  RECOVERY_DETECTED: { Icon: Undo2, tone: "text-ok" },
  RECURRED: { Icon: RotateCw, tone: "text-sev-high" },
  AUTO_RESOLVED: { Icon: CircleCheckBig, tone: "text-ok" },
  REOPENED: { Icon: RotateCcw, tone: "text-st-open" },
};

function iconFor(entry: HistoryEntry) {
  if (entry.action === "STATUS_CHANGED") {
    if (entry.toValue === "RESOLVED") return { Icon: CircleCheck, tone: "text-st-resolved" };
    if (entry.toValue === "INVESTIGATING") return { Icon: Search, tone: "text-st-investigating" };
    return { Icon: CircleDot, tone: "text-st-open" };
  }
  return ICONS[entry.action];
}

function HistoryItem({ entry }: { entry: HistoryEntry }) {
  const { Icon, tone } = iconFor(entry);
  return (
    <>
      <span className="relative z-10 flex size-7 shrink-0 items-center justify-center rounded-full border border-line-strong bg-surface">
        <Icon className={cn("size-3.5", tone)} aria-hidden />
      </span>
      <div className="min-w-0 pt-1">
        <p className="text-sm text-fg-muted">
          <span className="font-medium text-fg">{entry.actor ? entry.actor.name : "Dispatch"}</span>
          <span className="text-fg-subtle"> · </span>
          {entry.message}
        </p>
        <p className="mt-0.5 text-xs text-fg-subtle">
          <RelativeTime iso={entry.createdAt} />
        </p>
      </div>
    </>
  );
}

/** History and comments as a single chronological story, oldest first. */
export function ActivityTimeline({ items }: { items: ActivityItem[] }) {
  return (
    <ol className="relative space-y-5 before:absolute before:bottom-2 before:left-[13px] before:top-2 before:w-px before:bg-line-strong">
      {items.map((item) => (
        <li key={item.id} className="relative flex gap-3">
          {item.kind === "history" ? (
            <HistoryItem entry={item.entry} />
          ) : (
            <>
              <Avatar name={item.comment.author.name} className="relative z-10" />
              <div className="min-w-0 flex-1 rounded-lg border border-line-strong bg-surface-2">
                <p className="border-b border-line px-3 py-1.5 text-xs text-fg-subtle">
                  <span className="font-medium text-fg">{item.comment.author.name}</span> commented ·{" "}
                  <RelativeTime iso={item.comment.createdAt} />
                </p>
                <p className="whitespace-pre-wrap break-words px-3 py-2.5 text-sm leading-relaxed text-fg">{item.comment.body}</p>
              </div>
            </>
          )}
        </li>
      ))}
    </ol>
  );
}

export function CommentComposer({
  onSubmit,
  pending,
  error,
}: {
  onSubmit: (body: string, done: () => void) => void;
  pending: boolean;
  error: unknown;
}) {
  const [body, setBody] = useState("");
  const fieldError = error instanceof ApiError ? error.fieldErrors.body : undefined;

  function submit(event?: FormEvent) {
    event?.preventDefault();
    // Ignore repeats while a post is in flight, so a double Ctrl+Enter can't post twice.
    if (pending || !body.trim()) return;
    onSubmit(body.trim(), () => setBody(""));
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey) && !event.repeat) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <form onSubmit={submit} className="space-y-2">
      <Textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        onKeyDown={onKeyDown}
        placeholder="Add a note for the team — findings, actions taken, next steps…"
        rows={3}
        maxLength={5000}
        invalid={Boolean(fieldError)}
        aria-label="Comment"
      />
      {error ? <InlineError error={error} /> : null}
      <div className="flex items-center justify-between gap-3">
        <span className="text-xs text-fg-subtle">
          <kbd className="rounded border border-line-strong px-1 font-mono text-[10px]">Ctrl</kbd> +{" "}
          <kbd className="rounded border border-line-strong px-1 font-mono text-[10px]">Enter</kbd> to post
        </span>
        <Button type="submit" size="sm" variant="primary" loading={pending} disabled={!body.trim()}>
          Comment
        </Button>
      </div>
    </form>
  );
}
