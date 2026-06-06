package com.agentinsight.commandanalytics.service;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CommandClassifier {
    public String classify(String command) {
        String executable = executable(command);
        if (executable.isBlank()) {
            return "unknown";
        }
        if (isTestCommand(command)) {
            return "test";
        }
        if (matches(executable, "git", "gh")) {
            return "git";
        }
        if (matches(executable, "docker", "docker-compose", "kubectl", "podman")) {
            return "docker";
        }
        if (matches(executable, "npm", "pnpm", "yarn", "npx", "pip", "pip3", "uv", "brew", "apt", "apt-get")) {
            return "package";
        }
        if (matches(executable, "gradle", "./gradlew", "mvn", "./mvnw", "make", "cmake", "go", "cargo", "javac", "tsc", "vite")) {
            return "build";
        }
        if (matches(executable, "pytest", "jest", "vitest", "mocha", "junit", "go test", "cargo test")) {
            return "test";
        }
        if (matches(executable, "sqlite3", "psql", "mysql", "redis-cli")) {
            return "db";
        }
        if (matches(executable, "rm", "mv", "cp", "mkdir", "chmod", "chown", "find", "sed", "awk", "cat", "ls", "touch")) {
            return "filesystem";
        }
        if (matches(executable, "curl", "wget", "ssh", "scp", "rsync", "nc", "telnet")) {
            return "network";
        }
        return "unknown";
    }

    public String executable(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String trimmed = command.strip();
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2 && isEnvironmentAssignment(parts[0])) {
            return parts[1].toLowerCase(Locale.ROOT);
        }
        if (parts.length >= 2 && ("sudo".equals(parts[0]) || "command".equals(parts[0]))) {
            return parts[1].toLowerCase(Locale.ROOT);
        }
        if (parts.length >= 2 && "go".equals(parts[0]) && "test".equals(parts[1])) {
            return "go test";
        }
        if (parts.length >= 2 && "cargo".equals(parts[0]) && "test".equals(parts[1])) {
            return "cargo test";
        }
        return parts[0].toLowerCase(Locale.ROOT);
    }

    public String riskLevel(String command) {
        String lower = lower(command);
        if (lower.isBlank()) {
            return "none";
        }
        if (lower.matches(".*\\brm\\s+-[a-z]*r[a-z]*f[a-z]*\\s+/\\s*(?:[;&|].*)?$")
            || containsAny(lower, List.of("mkfs", "dd if=", ":(){", "chmod -r 777 /", "chown -r /"))) {
            return "high";
        }
        if (containsAny(lower, List.of("rm -rf", "git reset --hard", "git clean -fd", "drop database", "truncate table", "docker system prune", "kubectl delete", "sudo rm"))) {
            return "medium";
        }
        if (containsAny(lower, List.of("curl ", "wget ", "ssh ", "scp ", "rsync ", "chmod ", "sudo "))) {
            return "low";
        }
        return "none";
    }

    public String riskReason(String command) {
        String level = riskLevel(command);
        if ("none".equals(level)) {
            return "not_risky";
        }
        String lower = lower(command);
        if (lower.contains("rm ") || lower.contains("rm -")) {
            return "destructive_filesystem";
        }
        if (lower.contains("git reset --hard") || lower.contains("git clean -fd")) {
            return "destructive_git";
        }
        if (lower.contains("drop database") || lower.contains("truncate table")) {
            return "destructive_database";
        }
        if (lower.contains("curl ") || lower.contains("wget ") || lower.contains("ssh ")) {
            return "network_or_remote_access";
        }
        return "sensitive_command";
    }

    private boolean isTestCommand(String command) {
        String lower = lower(command);
        return lower.contains(" test") || lower.contains(":test") || lower.contains(" check") || lower.contains("verify");
    }

    private boolean matches(String executable, String... values) {
        for (String value : values) {
            if (value.equals(executable)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEnvironmentAssignment(String part) {
        return part.matches("[A-Za-z_][A-Za-z0-9_]*=.*");
    }

    private boolean containsAny(String value, List<String> needles) {
        return needles.stream().anyMatch(value::contains);
    }

    private String lower(String command) {
        return command == null ? "" : command.toLowerCase(Locale.ROOT);
    }
}
