package javaboy.core.bench;

import javaboy.core.*;

/** Banc de mesure : frames émulées par seconde, sans affichage ni son.
 *  Charge de travail : une ROM synthétique qui boucle sur des opérations ALU, des
 *  accès mémoire, des rotations CB, des appels/retours et un DMA OAM, avec LCD allumé
 *  (le rendu par ligne est donc inclus dans la mesure).
 *  Usage : java -cp build/test-classes javaboy.core.bench.Benchmark [frames] [rom.gb] */
public final class Benchmark {
    static byte[] workload() {
        TestRom r = new TestRom()
            .ldA(0xE4).ldhA(0x47)
            .ldA(0x91).ldhA(0x40)              // LCD on, BG on
            .emit(0x31, 0xFE, 0xFF);           // LD SP,0xFFFE
        // boucle principale à 0x0150+9 :
        //   LD HL,0x8000 ; LD B,0x40
        //   ADD A,B ; LD (HL+),A ; RLC A ; XOR A,C ; INC C ; CALL sub ; DEC B ; JR NZ
        //   LD A,0xC0 ; LDH (0x46),A   (DMA OAM) ; JR boucle
        r.ldHL(0x8000).ldB(0x40)
         .emit(0x80)                 // ADD A,B
         .ldHLIA()                   // LD (HL+),A
         .emit(0xCB, 0x07)           // RLC A
         .emit(0xA9)                 // XOR C
         .emit(0x0C)                 // INC C
         .emit(0xCD, 0x80, 0x01)     // CALL 0x0180
         .decB()
         .jrNZ(-12)
         .ldA(0xC0).ldhA(0x46)
         .emit(0x18, 0xE8);          // JR -24 -> retour au LD HL
        while (r.size() < 0x30) r.emit(0x00);
        // sous-programme à 0x0180 : PUSH/POP, SWAP, CP, RET
        r.emit(0xE5)                 // PUSH HL
         .emit(0xCB, 0x37)           // SWAP A
         .emit(0xFE, 0x42)           // CP 0x42
         .emit(0xE1)                 // POP HL
         .emit(0xC9);                // RET
        return r.build();
    }

    public static void main(String[] args) throws Exception {
        int frames = args.length > 0 ? Integer.parseInt(args[0]) : 3000;
        byte[] rom = args.length > 1 ? java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[1])) : workload();
        Emulator emu = new Emulator(TestRom.cartridge(rom));
        emu.reset();
        for (int i = 0; i < frames / 5; i++) emu.runFrame(); // échauffement JIT
        long t0 = System.nanoTime();
        for (int i = 0; i < frames; i++) emu.runFrame();
        double sec = (System.nanoTime() - t0) / 1e9;
        long instr = emu.getCpu().instrCount;
        System.out.printf("%d frames en %.2f s : %.0f frames/s (%.1fx temps réel), %.1f M instr/s%n",
                frames, sec, frames / sec, frames / sec / 60.0, instr / sec / 1e6);
    }
}
