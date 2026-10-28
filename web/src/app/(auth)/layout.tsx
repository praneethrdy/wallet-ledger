import { AuthBrandPanel } from "@/components/brand/auth-panel";
import { Logo } from "@/components/brand/logo";
import { ThemeToggle } from "@/components/theme/theme-toggle";

/** Split screen: form on the left, brand panel on the right (desktop only). */
export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="relative flex flex-col">
        <div className="pointer-events-none absolute inset-0 bg-grid [mask-image:radial-gradient(ellipse_at_top,black,transparent_70%)] opacity-60 lg:hidden" />
        <header className="relative flex h-16 items-center justify-between px-4 sm:px-8">
          <span className="lg:invisible">
            <Logo />
          </span>
          <ThemeToggle />
        </header>
        <main className="relative flex flex-1 items-center justify-center px-4 pb-16 sm:px-8">
          <div className="w-full max-w-sm">{children}</div>
        </main>
      </div>
      <AuthBrandPanel />
    </div>
  );
}
