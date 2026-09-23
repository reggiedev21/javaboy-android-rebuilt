package javaboy.core;

/** Résultat typé du chargement d'une ROM : succès ou erreur, sans boîte de dialogue.
 *  L'hôte fait un {@code switch} exhaustif avec pattern matching. */
public sealed interface CartridgeResult {
    record Ok(Cartridge cartridge, boolean checksumValid) implements CartridgeResult {}
    record Error(String message) implements CartridgeResult {}
}
