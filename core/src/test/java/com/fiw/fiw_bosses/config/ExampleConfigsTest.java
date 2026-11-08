package com.fiw.fiw_bosses.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every config under examples/ must load with zero warnings through the same validator the
 * mod uses, and every file in a negative/ folder must be reported.
 */
class ExampleConfigsTest {

    private static Path examples() {
        Path here = Path.of("").toAbsolutePath();
        Path root = here.getFileName().toString().equals("core") ? here.getParent() : here;
        return root.resolve("examples");
    }

    private static List<Path> files(String suffix) throws IOException {
        try (Stream<Path> s = Files.walk(examples())) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(suffix)).sorted().toList();
        }
    }

    private static boolean under(Path file, String folder) {
        for (Path part : examples().relativize(file)) if (part.toString().equals(folder)) return true;
        return false;
    }

    @Test
    void everyExampleLoadsWithoutWarnings() throws IOException {
        List<ConfigIssue> issues = new ArrayList<>();
        Map<String, BossDefinition> bosses = new LinkedHashMap<>();
        Map<String, String> bossFiles = new LinkedHashMap<>();
        Map<String, MinionDefinition> minions = new LinkedHashMap<>();
        int checked = 0;
        for (Path file : files(".json")) {
            if (under(file, "items")) continue; // Fiw Tools item configs, checked by Fiw Tools
            String name = examples().relativize(file).toString();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                if (file.getFileName().toString().equals("settings.json")) {
                    issues.addAll(ConfigValidator.settings(name, r).issues());
                } else if (under(file, "minions")) {
                    var c = ConfigValidator.minion(name, r);
                    issues.addAll(c.issues());
                    if (c.value() != null) minions.put(c.value().id, c.value());
                } else {
                    var c = ConfigValidator.boss(name, r);
                    issues.addAll(c.issues());
                    if (c.value() != null) { bosses.put(c.value().id, c.value()); bossFiles.put(c.value().id, name); }
                }
            }
            checked++;
        }
        issues.addAll(ConfigValidator.references(bosses, bossFiles, minions));
        assertTrue(checked > 20, "only " + checked + " example files found under " + examples());
        assertTrue(issues.isEmpty(), "\n" + String.join("\n", issues.stream().map(ConfigIssue::describe).toList()));
    }

    @Test
    void ancestralAbomasnowHasFourCompleteGuardianPhases() throws IOException {
        Path bossFile = examples().resolve("abomasnow_ancestral/bosses/abomasnow_ancestral.json");
        Path minionFile = examples().resolve("abomasnow_ancestral/minions/snover_guard.json");
        BossDefinition boss;
        MinionDefinition minion;
        try (Reader reader = Files.newBufferedReader(bossFile, StandardCharsets.UTF_8)) {
            var result = ConfigValidator.boss("abomasnow_ancestral/bosses/abomasnow_ancestral.json", reader);
            assertTrue(result.issues().isEmpty(), () -> result.issues().toString());
            boss = result.value();
        }
        try (Reader reader = Files.newBufferedReader(minionFile, StandardCharsets.UTF_8)) {
            var result = ConfigValidator.minion("abomasnow_ancestral/minions/snover_guard.json", reader);
            assertTrue(result.issues().isEmpty(), () -> result.issues().toString());
            minion = result.value();
        }

        assertNotNull(boss);
        assertNotNull(minion);
        assertEquals(4, boss.phases.size(), "the encounter must have four phases");
        assertEquals(56, boss.phases.stream().mapToInt(p -> p.shield == null ? 0 : p.shield.count).sum(),
                "all guardian waves must total 56 Snover");
        assertEquals("snover_guard", boss.phases.get(0).shield.minion);
        assertEquals(8, boss.phases.get(0).shield.count);
        assertEquals(5, boss.phases.get(0).abilities.size(),
                "the opening phase must have five distinct attack patterns");
        assertTrue(boss.phases.get(0).abilities.stream().anyMatch(a -> "ground_spike".equals(a.type)));
        assertTrue(boss.phases.get(0).abilities.stream().anyMatch(a -> "ice_crystal".equals(a.type)));
        assertEquals(12, boss.phases.get(1).shield.count);
        assertEquals(16, boss.phases.get(2).shield.count);
        assertEquals(20, boss.phases.get(3).shield.count);
        assertEquals("cobblemon:pokemon:abomasnow mega_evolution=mega", boss.phases.get(3).renderEntity);
        assertEquals("cobblemon:pokemon:snover", minion.renderEntity);
        var snoverSlash = minion.abilities.stream()
                .filter(a -> "melee_slash".equals(a.type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Snover guardians must have an actual attack"));
        assertEquals(12, snoverSlash.params.get("damage").getAsInt(),
                "Snover slash must hurt players wearing diamond armor");
        var finalStorm = boss.phases.get(3).abilities.stream()
                .filter(a -> "particle_tornado".equals(a.type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("final phase must include its blizzard"));
        assertEquals(3.2f, finalStorm.params.get("damage").getAsFloat(), 0.001f,
                "the final blizzard must deal damage, not only show particles");

        Path megaAssignment = examples().getParent().resolve("common-1.21.1/src/main/resources/data/cobblemon/species_feature_assignments/mega_evolution.json");
        String megaAssignmentJson = Files.readString(megaAssignment, StandardCharsets.UTF_8);
        assertTrue(megaAssignmentJson.contains("\"abomasnow\""));
        assertTrue(megaAssignmentJson.contains("\"mega_evolution\""),
                "Abomasnow needs the Mega Showdown feature to resolve its Mega aspect");

        // The icicle attack must remain present and bounded in every late-game phase.
        int[] expectedIcicles = {20, 26, 32};
        for (int phaseIndex = 1; phaseIndex < boss.phases.size(); phaseIndex++) {
            final int checkedPhase = phaseIndex;
            var icicleRain = boss.phases.get(checkedPhase).abilities.stream()
                    .filter(a -> "icicle_rain".equals(a.type))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("phase " + checkedPhase + " has no icicle rain"));
            assertEquals(expectedIcicles[phaseIndex - 1],
                    icicleRain.params.get("icicleCount").getAsInt());
            assertTrue(icicleRain.params.get("warnTicks").getAsInt() >= 20,
                    "players need time to react to marked impact zones");
        }
    }

    @Test
    void everyNegativeExampleIsReported() throws IOException {
        List<Path> negatives = files(".invalid");
        assertFalse(negatives.isEmpty());
        for (Path file : negatives) {
            List<ConfigIssue> issues = ConfigValidator.validateDirectory(single(file));
            assertFalse(issues.isEmpty(), file + " produced no warning");
        }
    }

    /** Copies one negative file into a temporary config folder as an active boss/minion file. */
    private static Path single(Path file) throws IOException {
        Path dir = Files.createTempDirectory("fiw-bosses-negative");
        String name = file.getFileName().toString().replace(".invalid", "");
        Path target = dir.resolve(under(file, "minions") ? "minions" : "bosses").resolve(name);
        Files.createDirectories(target.getParent());
        Files.copy(file, target);
        return dir;
    }
}
