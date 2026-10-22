package javaboy.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Localise le dossier des ROMs de test locales (jamais versionnées).
 *
 *  Ordre de recherche : propriété système {@code javaboy.roms}, variable d'environnement
 *  {@code JAVABOY_ROMS}, puis {@code core/src/test/resources/roms} (lancement depuis la racine,
 *  cas de test.sh) et {@code src/test/resources/roms} (lancement depuis core/, cas de Gradle). */
final class RomFiles {
    private RomFiles() {}

    static List<Path> candidateDirs() {
        List<Path> dirs = new ArrayList<>();
        String prop = System.getProperty("javaboy.roms");
        if (prop != null && !prop.isBlank()) dirs.add(Path.of(prop));
        String env = System.getenv("JAVABOY_ROMS");
        if (env != null && !env.isBlank()) dirs.add(Path.of(env));
        dirs.add(Path.of("core/src/test/resources/roms"));
        dirs.add(Path.of("src/test/resources/roms"));
        return dirs;
    }

    /** Dossier de ROMs existant, ou {@code null}. */
    static Path romDir() {
        for (Path d : candidateDirs()) if (Files.isDirectory(d)) return d;
        return null;
    }

    /** Toutes les ROMs (.gb, .gbc, .zip, .gz) du dossier, triées par nom. */
    static List<Path> allRoms() throws IOException { return allRoms(romDir()); }

    static List<Path> allRoms(Path dir) throws IOException {
        if (dir == null) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".gb") || n.endsWith(".gbc") || n.endsWith(".zip") || n.endsWith(".gz");
            }).sorted().toList();
        }
    }

    /** Une ROM précise par nom de fichier, ou {@code null}. */
    static Path find(String fileName) {
        for (Path d : candidateDirs()) {
            Path p = d.resolve(fileName);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    static byte[] read(Path rom) throws IOException { return RomArchive.unwrap(Files.readAllBytes(rom)); }
}
