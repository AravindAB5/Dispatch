"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { UserPlus, Users } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";

import { Avatar } from "@/components/status/avatar";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/form";
import { PageHeader } from "@/components/ui/panel";
import { EmptyState, ErrorState, InlineError, SkeletonRows } from "@/components/ui/states";
import { useIsAdmin, useMe } from "@/hooks/use-me";
import { usersApi } from "@/lib/api/endpoints";
import { ApiError, describeError } from "@/lib/api/errors";
import type { Role, User } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { formatDate } from "@/lib/format";
import { qk } from "@/lib/query-keys";
import { toast } from "@/stores/toast-store";

function AddMemberDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("OPERATOR");
  const create = useMutation({
    mutationFn: () => usersApi.create({ name: name.trim(), email: email.trim(), password, role }),
    onSuccess: (user) => {
      void queryClient.invalidateQueries({ queryKey: qk.users });
      toast.success("Team member added", `${user.name} can now sign in`);
      onClose();
    },
  });
  const errors = create.error instanceof ApiError ? create.error.fieldErrors : {};

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Add a team member"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="member-form" variant="primary" loading={create.isPending}>
            Add member
          </Button>
        </>
      }
    >
      <form id="member-form" onSubmit={submit} className="space-y-4">
        <Field label="Full name" error={errors.name}>
          {(p) => <Input {...p} value={name} onChange={(e) => setName(e.target.value)} autoFocus maxLength={120} />}
        </Field>
        <Field label="Email" error={errors.email}>
          {(p) => <Input {...p} type="email" value={email} onChange={(e) => setEmail(e.target.value)} />}
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Temporary password" error={errors.password} hint="At least 8 characters">
            {(p) => <Input {...p} type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />}
          </Field>
          <Field label="Role" error={errors.role}>
            {(p) => (
              <Select {...p} value={role} onChange={(e) => setRole(e.target.value as Role)}>
                <option value="OPERATOR">Operator</option>
                <option value="ADMIN">Administrator</option>
              </Select>
            )}
          </Field>
        </div>
        {create.error && Object.keys(errors).length === 0 && <InlineError error={create.error} />}
      </form>
    </Dialog>
  );
}

export function TeamView() {
  const isAdmin = useIsAdmin();
  const { data: me } = useMe();
  const queryClient = useQueryClient();
  const users = useQuery({ queryKey: qk.users, queryFn: usersApi.list });
  const [adding, setAdding] = useState(false);

  const update = useMutation({
    mutationFn: ({ user, patch }: { user: User; patch: { role?: Role; active?: boolean } }) => usersApi.update(user.id, patch),
    onSuccess: (user) => {
      void queryClient.invalidateQueries({ queryKey: qk.users });
      toast.success("Team member updated", `${user.name} · ${user.role.toLowerCase()}${user.active ? "" : " · deactivated"}`);
    },
    onError: (error) => {
      toast.error("Couldn't update the team member", describeError(error));
      void queryClient.invalidateQueries({ queryKey: qk.users });
    },
  });

  return (
    <div>
      <PageHeader
        title="Team"
        description="People who can sign in to Dispatch. Operators work incidents; administrators also manage configuration."
        actions={
          isAdmin && (
            <Button variant="primary" icon={<UserPlus className="size-4" />} onClick={() => setAdding(true)}>
              Add member
            </Button>
          )
        }
      />
      <div className="rounded-lg border border-line bg-surface">
        {users.isPending ? (
          <SkeletonRows rows={6} />
        ) : users.error ? (
          <ErrorState error={users.error} title="Couldn't load the team" onRetry={() => users.refetch()} />
        ) : users.data.length === 0 ? (
          <EmptyState icon={<Users className="size-5" />} title="No team members" />
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-line text-left text-[11px] uppercase tracking-wide text-fg-subtle">
                <th className="py-2 pl-4 pr-3 font-medium">Member</th>
                <th className="py-2 pr-3 font-medium">Role</th>
                <th className="hidden py-2 pr-3 font-medium sm:table-cell">Status</th>
                <th className="hidden py-2 pr-3 text-right font-medium md:table-cell">Active incidents</th>
                <th className="hidden py-2 pr-4 font-medium lg:table-cell">Member since</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {users.data.map((user) => {
                const self = user.id === me?.id;
                return (
                  <tr key={user.id} className={cn(!user.active && "opacity-60")}>
                    <td className="py-3 pl-4 pr-3">
                      <div className="flex items-center gap-3">
                        <Avatar name={user.name} />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium text-fg">
                            {user.name}
                            {self && <span className="ml-2 text-xs font-normal text-fg-subtle">(you)</span>}
                          </p>
                          <p className="truncate text-xs text-fg-subtle">{user.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="py-3 pr-3">
                      {isAdmin && !self ? (
                        <Select
                          aria-label={`Role for ${user.name}`}
                          value={user.role}
                          onChange={(e) => update.mutate({ user, patch: { role: e.target.value as Role } })}
                          className="w-32 sm:w-36"
                        >
                          <option value="OPERATOR">Operator</option>
                          <option value="ADMIN">Administrator</option>
                        </Select>
                      ) : (
                        <span className="text-sm text-fg-muted">{user.role === "ADMIN" ? "Administrator" : "Operator"}</span>
                      )}
                    </td>
                    <td className="hidden py-3 pr-3 sm:table-cell">
                      <div className="flex items-center gap-2">
                        <span className={cn("inline-flex items-center gap-1.5 text-xs", user.active ? "text-ok" : "text-fg-subtle")}>
                          <span className={cn("size-1.5 rounded-full", user.active ? "bg-ok" : "bg-fg-subtle")} aria-hidden />
                          {user.active ? "Active" : "Deactivated"}
                        </span>
                        {isAdmin && !self && (
                          <Button size="sm" variant="ghost" onClick={() => update.mutate({ user, patch: { active: !user.active } })}>
                            {user.active ? "Deactivate" : "Reactivate"}
                          </Button>
                        )}
                      </div>
                    </td>
                    <td className="tabular hidden py-3 pr-3 text-right text-sm md:table-cell">
                      {user.activeIncidents > 0 ? (
                        <Link href={`/incidents?assignee=${user.id}`} className="text-fg hover:text-accent-strong">
                          {user.activeIncidents}
                        </Link>
                      ) : (
                        <span className="text-fg-subtle">0</span>
                      )}
                    </td>
                    <td className="hidden py-3 pr-4 text-sm text-fg-muted lg:table-cell">{formatDate(user.createdAt)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          </div>
        )}
      </div>
      {adding && <AddMemberDialog open onClose={() => setAdding(false)} />}
    </div>
  );
}
