"use client";

import { RotateCwIcon } from "lucide-react";
import Link from "next/link";
import { useEffect } from "react";

import { ErrorState } from "@/components/states/states";
import { Button } from "@/components/ui/button";

/**
 * Anything a page throws that isn't "not found" lands here (backend down, 5xx). In production
 * Next hides the message and gives a `digest`, which is also printed next to the full error
 * (including the backend's correlationId) in the server log: quoting it finds the cause.
 */
export default function AppError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto max-w-lg surface">
      <ErrorState
        title="This page couldn’t load"
        description="Something went wrong on our side, or the server is unreachable. Your money is safe: nothing is half-done in the ledger."
        correlationId={error.digest}
        action={
          <div className="flex gap-2">
            <Button onClick={reset}>
              <RotateCwIcon /> Try again
            </Button>
            <Button variant="outline" asChild>
              <Link href="/">Home</Link>
            </Button>
          </div>
        }
      />
    </div>
  );
}
