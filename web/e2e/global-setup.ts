import { API_ROOT } from "./helpers";

/** Fail fast, with instructions, when the backend isn't up. */
export default async function globalSetup() {
  try {
    const res = await fetch(`${API_ROOT}/actuator/health`);
    if (res.ok) return;
  } catch {
    // fall through
  }
  throw new Error(
    `Backend not reachable at ${API_ROOT}. Start it first: docker compose up -d && ` +
      "cd backend && ./mvnw spring-boot:run (dev profile, seeded demo users). See web/README.md.",
  );
}
