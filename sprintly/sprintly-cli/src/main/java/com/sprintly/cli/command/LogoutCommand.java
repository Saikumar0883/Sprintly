package com.sprintly.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sprintly.cli.client.SprintlyClient;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.common.dto.ApiResponse;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "logout", description = "Logout from Sprintly")
public class LogoutCommand implements Callable<Integer> {

    private final SprintlyClient client = new SprintlyClient();

    @Override
    public Integer call() throws Exception {

        // ── GUARD: Not logged in at all? ──────────────────────────────
        if (!client.isLoggedIn()) {
            System.out.println();
            System.out.println("  ℹ  You are not logged in.");
            System.out.println();
            return 0;
        }
        // ─────────────────────────────────────────────────────────────

        // Try to tell the backend to revoke refresh tokens
        // This is a best-effort call — we clear local config regardless
        // of whether the server call succeeds.
        //
        // WHY clear locally even if server fails?
        //   The token may be expired — the server correctly rejects it.
        //   But from the user's perspective they still want to be
        //   logged out of this machine. Keeping a stale/expired token
        //   in the config serves no purpose.
        //
        //   Worst case: refresh token stays alive on the server for
        //   up to 7 days. But the access token (15 min) is already gone.
        //   Local logout still protects this machine.
        try {
            ApiResponse<Void> response =
                    client.post("/auth/logout", null, new TypeReference<>() {}, true);

            if (response != null && response.isSuccess()) {
                // Server confirmed: refresh tokens revoked on all devices
                CliConfig.clear();
                System.out.println();
                System.out.println("  ✔  Logged out successfully from all devices.");
                System.out.println();
            } else {
                // Server rejected (expired token, server down, etc.)
                // Still clear locally so user isn't stuck
                CliConfig.clear();
                System.out.println();
                System.out.println("  ✔  Logged out locally.");
                System.out.println("     (Could not reach server to revoke server-side tokens)");
                System.out.println();
            }
        } catch (Exception e) {
            // Network error, server down, etc. — still log out locally
            CliConfig.clear();
            System.out.println();
            System.out.println("  ✔  Logged out locally.");
            System.out.println("     (Server unreachable: " + e.getMessage() + ")");
            System.out.println();
        }

        return 0;
    }
}