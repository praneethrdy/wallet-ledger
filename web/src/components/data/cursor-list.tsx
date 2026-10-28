"use client";

import { useInfiniteQuery, type InfiniteData } from "@tanstack/react-query";
import { useEffect, useRef } from "react";

import { ErrorState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { bffGet } from "@/lib/bff-client";

export type CursorPage<T> = { items: T[]; nextCursor: string | null };

/**
 * A cursor-paginated list whose first page was rendered on the server. Later pages come from a
 * /bff route (`path` plus `cursor=`); a sentinel near the end loads the next one automatically.
 */
export function useCursorList<T>(queryKey: readonly unknown[], path: string, firstPage: CursorPage<T>) {
  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => {
      const url = new URL(path, "http://x");
      if (pageParam) url.searchParams.set("cursor", pageParam);
      return bffGet<CursorPage<T>>(url.pathname + url.search);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    initialData: { pages: [firstPage], pageParams: [null] } satisfies InfiniteData<CursorPage<T>, string | null>,
    staleTime: Infinity, // the server render is fresh; later pages are loaded on demand
    gcTime: 0, // ...and on the next visit the server renders a fresh first page, so drop this cache on unmount
  });

  const sentinel = useRef<HTMLDivElement>(null);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = query;
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasNextPage) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isFetchingNextPage) void fetchNextPage();
      },
      { rootMargin: "320px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return { query, items: query.data.pages.flatMap((p) => p.items), sentinel };
}

/** Sentinel, loading rows, error and the keyboard-accessible "Load more" under a cursor list. */
export function ListFooter({
  list,
}: {
  list: Pick<ReturnType<typeof useCursorList<unknown>>, "query" | "items" | "sentinel">;
}) {
  const { query, items, sentinel } = list;
  return (
    <>
      <div ref={sentinel} />
      {query.isFetchingNextPage && (
        <div className="space-y-2 p-3" aria-busy aria-label="Loading more">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      )}
      {query.isFetchNextPageError && (
        <ErrorState title="Couldn’t load more" description={query.error?.message} className="py-8" />
      )}
      {query.hasNextPage && !query.isFetchingNextPage && (
        <div className="flex justify-center p-3">
          <Button variant="ghost" size="sm" onClick={() => void query.fetchNextPage()}>
            Load more
          </Button>
        </div>
      )}
      {!query.hasNextPage && items.length > 10 && (
        <p className="py-4 text-center text-xs text-muted-foreground">That’s everything.</p>
      )}
    </>
  );
}
