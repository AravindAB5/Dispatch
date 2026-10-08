package com.dispatch.seed;

import java.util.List;
import java.util.Map;

import com.dispatch.service.Environment;
import com.dispatch.user.Role;

/** Static reference data for the development dataset. */
final class SeedCatalog {

    private SeedCatalog() {
    }

    /** Development-only credentials, documented in the README. */
    record SeedUser(String name, String email, String password, Role role, boolean active) {
    }

    static final List<SeedUser> USERS = List.of(
            new SeedUser("Morgan Hale", "admin@dispatch.local", "admin123", Role.ADMIN, true),
            new SeedUser("Aravind B", "operator@dispatch.local", "operator123", Role.OPERATOR, true),
            new SeedUser("Priya Raman", "priya@dispatch.local", "operator123", Role.OPERATOR, true),
            new SeedUser("Marcus Chen", "marcus@dispatch.local", "operator123", Role.OPERATOR, true),
            new SeedUser("Sofia Alvarez", "sofia@dispatch.local", "operator123", Role.OPERATOR, true),
            new SeedUser("Daniel Okafor", "daniel@dispatch.local", "operator123", Role.OPERATOR, false));

    record SeedService(String name, String description, Environment environment, String team, String deviceType,
            List<Integer> deviceNumbers, List<String> locations) {
    }

    static final List<SeedService> SERVICES = List.of(
            new SeedService("Video Processing Service",
                    "Transcodes uploaded and live media into adaptive-bitrate renditions.",
                    Environment.PRODUCTION, "Media Pipeline", "Transcoder node",
                    List.of(11, 14, 17, 21, 42, 46),
                    List.of("us-east-1 · rack A12", "us-east-1 · rack A14", "eu-west-1 · rack C03")),
            new SeedService("Content Delivery API",
                    "Serves manifests, catalog metadata and signed playback URLs to client apps.",
                    Environment.PRODUCTION, "Content Platform", "API node",
                    List.of(52, 55, 58, 61, 64),
                    List.of("us-east-1", "eu-west-1", "ap-south-1")),
            new SeedService("Device Gateway",
                    "Terminates connections from field hardware and relays their telemetry.",
                    Environment.PRODUCTION, "Edge Systems", "Gateway node",
                    List.of(101, 104, 108, 112, 115, 119),
                    List.of("Chennai DC · hall 2", "Mumbai DC · hall 1", "Frankfurt POP", "London POP")),
            new SeedService("Playback Service",
                    "Playback sessions, DRM licence brokering and quality-of-experience reporting.",
                    Environment.PRODUCTION, "Playback", "Playback node",
                    List.of(71, 73, 76, 79, 83),
                    List.of("us-east-1", "eu-central-1", "ap-south-1")),
            new SeedService("Notification Service",
                    "Delivers push, email and in-app notifications to subscribers.",
                    Environment.PRODUCTION, "Engagement", "Worker",
                    List.of(88, 91, 94),
                    List.of("eu-west-1")),
            new SeedService("Authentication Service",
                    "Issues and validates subscriber sessions and entitlements.",
                    Environment.PRODUCTION, "Identity", "Auth node",
                    List.of(31, 34, 37),
                    List.of("us-east-1", "eu-west-1", "ap-south-1")),
            new SeedService("Ingest Edge",
                    "Contribution encoders receiving live feeds from broadcast partners.",
                    Environment.PRODUCTION, "Live Operations", "Ingest encoder",
                    List.of(121, 124, 127, 130, 133, 136, 139),
                    List.of("London · Studio 2", "Chennai · MCR", "Singapore · Studio 1", "New York · Studio 5",
                            "Frankfurt · Playout", "Mumbai · MCR", "Sydney · Studio 3")),
            new SeedService("Search Indexer",
                    "Builds the catalog search index from content metadata.",
                    Environment.STAGING, "Discovery", "Indexer",
                    List.of(141, 144, 147),
                    List.of("staging · eu-west-1")));

    static final Map<String, List<String>> COMMENTS = Map.of(
            "DEVICE_OFFLINE", List.of(
                    "Device dropped off the management network. Checking the switch port logs.",
                    "Power cycled it through the remote PDU, waiting for it to boot.",
                    "On-site team confirms a fibre patch lead was disturbed during cabinet work. Re-seated it.",
                    "Back online and passing health checks. Keeping an eye on it for the next hour."),
            "CPU_USAGE_HIGH", List.of(
                    "Spike lines up with the 4K re-encode batch that kicked off at the top of the hour.",
                    "Lowered transcode queue concurrency from 8 to 6 on this node.",
                    "Load has rebalanced across the pool. Watching for another 15 minutes."),
            "MEMORY_USAGE_HIGH", List.of(
                    "Heap growth looks like the known leak in the manifest cache (DSP-412).",
                    "Restarted the worker process; memory is back to baseline."),
            "DISK_USAGE_HIGH", List.of(
                    "Log rotation has been failing since the permissions change last week.",
                    "Fixed the logrotate config and rotated manually. 31% free now."),
            "ERROR_RATE_HIGH", List.of(
                    "Errors are 503s coming from the origin shield in eu-west-1.",
                    "Rolled back to the previous release; the error rate is dropping.",
                    "Upstream CDN acknowledged packet loss on their side (ticket #48213)."),
            "LATENCY_HIGH", List.of(
                    "p95 regression started right after the catalog deployment.",
                    "Database connection pool was saturated. Raised it from 20 to 40."),
            "SERVICE_DEGRADED", List.of(
                    "Health checks failing in eu-west-1 only. Shifting traffic to eu-central-1.",
                    "Posted a partial-outage notice on the status page."),
            "SERVICE_DOWN", List.of(
                    "Failover to the secondary region completed.",
                    "Root cause: expired TLS certificate on the internal load balancer. Renewed and rotated."));

    static final Map<String, String> RESOLUTIONS = Map.of(
            "DEVICE_OFFLINE", "Re-seated network cable; device reconnected and stable.",
            "CPU_USAGE_HIGH", "Reduced queue concurrency; CPU back under 60%.",
            "MEMORY_USAGE_HIGH", "Restarted worker. Leak tracked in DSP-412.",
            "DISK_USAGE_HIGH", "Fixed log rotation and cleared old segments.",
            "ERROR_RATE_HIGH", "Rolled back release 3.18.2; error rate at baseline.",
            "LATENCY_HIGH", "Increased connection pool size; latency normal.",
            "SERVICE_DEGRADED", "Regional failover completed; all checks green.",
            "SERVICE_DOWN", "Certificate renewed; monitoring added for expiry.");
}
