# Screenshots

Save screenshots here with these names; the main README already references them (uncomment the image
block near the top of `README.md` once they exist).

| File | What to capture |
|---|---|
| `dashboard.png` | `/dashboard` as the admin, with the simulator running, at 1440×900 |
| `incident-detail.png` | An incident that has been acknowledged, commented on and has repeats, e.g. one of the seeded `INVESTIGATING` incidents |
| `incidents.png` | `/incidents` with the default *Active* view |
| `events.png` | `/events` while the stream is live |
| `rules.png` | `/rules`, ideally with the rule editor open |
| `mobile.png` | The dashboard at a phone width (375 px) |

Tips:

1. `docker compose down -v && docker compose up --build` gives a fresh, fully populated dataset.
2. Let the simulator run for a few minutes so the live feed and trend chart have recent activity.
3. Use the browser's device toolbar at 1440×900 (or 375×812 for mobile) and capture at 2× for crisp text.
