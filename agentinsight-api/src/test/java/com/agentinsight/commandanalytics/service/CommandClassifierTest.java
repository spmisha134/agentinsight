package com.agentinsight.commandanalytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandClassifierTest {
    private final CommandClassifier classifier = new CommandClassifier();

    @Test
    void classifiesCommandCategories() {
        assertThat(classifier.classify("./gradlew test")).isEqualTo("test");
        assertThat(classifier.classify("npm test")).isEqualTo("test");
        assertThat(classifier.classify("npm install")).isEqualTo("package");
        assertThat(classifier.classify("git status")).isEqualTo("git");
        assertThat(classifier.classify("docker compose up")).isEqualTo("docker");
        assertThat(classifier.classify("sqlite3 app.db .tables")).isEqualTo("db");
        assertThat(classifier.classify("curl http://localhost:8080")).isEqualTo("network");
    }

    @Test
    void detectsRiskyCommands() {
        assertThat(classifier.riskLevel("rm -rf /tmp/build")).isEqualTo("medium");
        assertThat(classifier.riskLevel("rm -rf /")).isEqualTo("high");
        assertThat(classifier.riskReason("git reset --hard")).isEqualTo("destructive_git");
    }
}
