/**
 * Re-created on every navigation, so each page arrives with a short fade-and-rise. Plain CSS, so
 * server-rendered HTML animates without waiting for JavaScript, and the animation leaves no
 * transform behind once it ends. Off with reduced motion.
 */
export default function AppTemplate({ children }: { children: React.ReactNode }) {
  return <div className="animate-page-in motion-reduce:animate-none">{children}</div>;
}
