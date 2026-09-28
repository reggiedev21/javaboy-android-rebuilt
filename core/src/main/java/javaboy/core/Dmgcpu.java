// Mahjong Quest - Value set at 045E

/*

JavaBoy
                                  
COPYRIGHT (C) 2001 Neil Millstone and The Victoria University of Manchester
                                                                         ;;;
This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation; either version 2 of the License, or (at your option)
any later version.        

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
more details.


You should have received a copy of the GNU General Public License along with
this program; if not, write to the Free Software Foundation, Inc., 59 Temple
Place - Suite 330, Boston, MA 02111-1307, USA.

*/

package javaboy.core;

/** This is the main controlling class for the emulation
 *  It contains the code to emulate the Z80-like processor
 *  found in the Gameboy, and code to provide the locations
 *  in CPU address space that points to the correct area of
 *  ROM/RAM/IO.
 */
public final class Dmgcpu {
 /** Registers: 8-bit */
 int a, b, c, d, e, f;
 /** Registers: 16-bit */
 public int sp, pc, hl;

 /** The number of instructions that have been executed since the
  *  last reset
  */
 public int instrCount = 0;

 boolean interruptsEnabled = false;

 /** Used to implement the IE delay slot */
 int ieDelay = -1;

 boolean timaEnabled = false;
 int instrsPerTima = 6000;

 /** TRUE when the CPU is currently processing an interrupt */
 boolean inInterrupt = false;

 /** Enable the breakpoint flag.  As breakpoint instruction is used in some games, this is used to skip over it unless the breakpoint is actually in use */
 boolean breakpointEnable = false;

 // Constants for flags register

 /** Zero flag */
 static final int F_ZERO =      0x80;
 /** Subtract/negative flag */
 static final int F_SUBTRACT =  0x40;
 /** Half carry flag */
 static final int F_HALFCARRY = 0x20;
 /** Carry flag */
 static final int F_CARRY =     0x10;

 final short INSTRS_PER_VBLANK = 9000; /* 10000  */

 /** Used to set the speed of the emulator.  This controls how
  *  many instructions are executed for each horizontal line scanned
  *  on the screen.  Multiply by 154 to find out how many instructions
  *  per frame.
  */
 final short BASE_INSTRS_PER_HBLANK = 60;    /* 60    */
 short INSTRS_PER_HBLANK = BASE_INSTRS_PER_HBLANK;

 /** Used to set the speed of DIV increments */
 final short BASE_INSTRS_PER_DIV    = 33;    /* 33    */
 short INSTRS_PER_DIV = BASE_INSTRS_PER_DIV;

 // Constants for interrupts

 /** Vertical blank interrupt */
 public final short INT_VBLANK =  0x01;

 /** LCD Coincidence interrupt */
 public final short INT_LCDC =    0x02;
         
 /** TIMA (programmable timer) interrupt */
 public final short INT_TIMA =    0x04;

 /** Serial interrupt */
 public final short INT_SER =     0x08;

 /** P10 - P13 (Joypad) interrupt */
 public final short INT_P10 =     0x10;

 String[] registerNames =
     {"B", "C", "D", "E", "H", "L", "(HL)", "A"};
 String[] aluOperations =
     {"ADD", "ADC", "SUB", "SBC", "AND", "XOR", "OR", "CP"};
 String[] shiftOperations =
     {"RLC", "RRC", "RL", "RR", "SLA", "SRA", "SWAP", "SRL"};

 // 8Kb main system RAM appears at 0xC000 in address space
 // 32Kb for GBC
 byte[] mainRam = new byte[0x8000];

 // 256 bytes at top of RAM are used mainly for registers
 byte[] oam = new byte[0x100];

 Cartridge cartridge;
 GraphicsChip graphicsChip;
 SoundChip soundChip;
 GameLink gameLink;
 IoHandler ioHandler;
 boolean terminate;
 boolean running = false;

 /** Mis à vrai à la fin de chaque image ; execute() s'arrête alors si stopAtFrame est vrai. */
 boolean frameReady = false;
 boolean stopAtFrame = false;

 boolean gbcFeatures = true;
 boolean allowGbcFeatures = true;
 int gbcRamBank = 1;

 /** Create a CPU emulator with the supplied cartridge and game link objects.  Both can be set up
  *  or changed later if needed
  */
 public Dmgcpu(Cartridge c, GameLink l, Joypad joypad, AudioOutput audio, int sampleRate) {
  cartridge = c;
  gameLink = l;
  if (gameLink != null) gameLink.setDmgcpu(this);
  graphicsChip = new GraphicsChip(this);
  checkEnableGbc();
  soundChip = new SoundChip(audio, sampleRate);
  ioHandler = new IoHandler(this, joypad);
  buildTables();
 }


 /** Force the execution thread to stop and return to it's caller */
 public void terminateProcess() {
  terminate = true;
/*  do {
   try {
    java.lang.Thread.sleep(100);
    System.out.println("Wating for CPU...");
   } catch (InterruptedException e) {
    // Nothing
   }
  } while (running);*/
 }

 /** Perform a CPU address space read.  This maps all the relevant objects into the correct parts of
  *  the memory
  */
 public final short addressRead(int addr) {

/*  if ((addr >= 0xDFD8) && (addr <= 0xDFF0) && (running)) {
   System.out.println(Bits.hexWord(addr) + " read at " + Bits.hexWord(pc) + " bank " + cartridge.currentBank);
  }*/

/*  if ((addr < 0) || (addr > 65535)) {
    System.out.println("Tried to read address " + addr + ".  pc = " + Bits.hexWord(pc));
    return 0xFF;
  }*/

  addr = addr & 0xFFFF;

  switch ((addr & 0xF000)) {
   case 0x0000 :
   case 0x1000 :
   case 0x2000 :
   case 0x3000 :
   case 0x4000 :
   case 0x5000 :
   case 0x6000 :
   case 0x7000 :
    return cartridge.addressRead(addr);

   case 0x8000 :
   case 0x9000 :
    return (short) graphicsChip.addressRead(addr - 0x8000);

   case 0xA000 :
   case 0xB000 :
    return cartridge.addressRead(addr);

   case 0xC000 :
    return (mainRam[addr - 0xC000]);

   case 0xD000 :
    return (mainRam[addr - 0xD000 + (gbcRamBank * 0x1000)]);

   case 0xE000 :
    return mainRam[addr - 0xE000];
     /* (short) (mainRam[addr - 0xE000] & 0x00FF); */
    
   case 0xF000 :
    if (addr < 0xFE00) {
     return mainRam[addr - 0xE000];
    } else if (addr < 0xFF00) {
     return (short) (oam[addr - 0xFE00] & 0x00FF);
    } else {
     return ioHandler.ioRead(addr - 0xFF00);
    }

   default:
    System.out.println("Tried to read address " + addr + ".  pc = " + Bits.hexWord(pc));
    return 0xFF;
  }

 }

 /** Performs a CPU address space write.  Maps all of the relevant object into the right parts of
  *  memory.
  */
 public final void addressWrite(int addr, int data) {

/*  if ((addr >= 0xCFF8) && (addr <= 0xCFF8) && (running)) {
   System.out.println(Bits.hexWord(data) + " written to " + Bits.hexWord(addr) + " at " + Bits.hexWord(pc) + " bank " + cartridge.currentBank);
  }
  if ((addr >= 0xEFF8) && (addr <= 0xEFF8) && (running)) {
   System.out.println(Bits.hexWord(data) + " written to " + Bits.hexWord(addr) + " at " + Bits.hexWord(pc) + " bank " + cartridge.currentBank);
  }*/

/*  if ((addr < 0) || (addr > 65535)) {
   System.out.println(Bits.hexWord(data) + " written to " + Bits.hexWord(addr) + " at " + Bits.hexWord(pc) + " bank " + cartridge.currentBank);
  }*/

  switch (addr & 0xF000) {
   case 0x0000 :
   case 0x1000 :
   case 0x2000 :
   case 0x3000 :
   case 0x4000 :
   case 0x5000 :
   case 0x6000 :
   case 0x7000 :
    if (!running) {
     cartridge.debuggerAddressWrite(addr, data);
    } else {
     cartridge.addressWrite(addr, data);
 //    System.out.println("Tried to write to ROM! PC = " + Bits.hexWord(pc) + ", Data = " + Bits.hexByte(Bits.unsign((byte) data)));
    }
    break;

   case 0x8000 :
   case 0x9000 :
    graphicsChip.addressWrite(addr - 0x8000, (byte) data);
    break;

   case 0xA000 :
   case 0xB000 :
    cartridge.addressWrite(addr, data);
    break;

   case 0xC000 :
    mainRam[addr - 0xC000] = (byte) data;
    break;

   case 0xD000 :
    mainRam[addr - 0xD000 + (gbcRamBank * 0x1000)] = (byte) data;
    break;

   case 0xE000 :
    mainRam[addr - 0xE000] = (byte) data;
    break;

   case 0xF000 :
    if (addr < 0xFE00) {
     try {
      mainRam[addr - 0xE000] = (byte) data;
     } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("Address error: " + addr + " pc = " + Bits.hexWord(pc));
     }
    } else if (addr < 0xFF00) {
     oam[addr - 0xFE00] = (byte) data;
    } else {
     ioHandler.ioWrite(addr - 0xFF00, (short) data);
    }
    break;
  }


 }


 public final void addressWriteOld(int addr, int data) {

/*  if ((addr >= 0xFFA4) && (addr <= 0xFFA5) && (running)) {
   System.out.println(Bits.hexWord(addr) + " written at " + Bits.hexWord(pc) + " bank " + cartridge.currentBank);
  }*/


//  System.out.print(Bits.hexByte(Bits.unsign((short) data)) + " --> " + Bits.hexWord(addr) + ", ");
  if ((addr < 0x8000)) {
   if (!running) {
    cartridge.debuggerAddressWrite(addr, data);
   } else {
    cartridge.addressWrite(addr, data);
//    System.out.println("Tried to write to ROM! PC = " + Bits.hexWord(pc) + ", Data = " + Bits.hexByte(Bits.unsign((byte) data)));
   }
  } else if (addr < 0xA000) {
   try {
    graphicsChip.addressWrite(addr - 0x8000, (byte) data);
   } catch (ArrayIndexOutOfBoundsException e) {
    System.out.println("Error address " + addr);
   }
  } else if (addr < 0xC000) {
   // RAM Bank write
//   System.out.println("RAM bank write! + " + Bits.hexWord(addr) + " = " + Bits.hexByte(data) + " at " + Bits.hexWord(pc));
   cartridge.addressWrite(addr, data);
  } else if (addr < 0xE000) {
   mainRam[addr - 0xC000] = (byte) data;
  } else if (addr < 0xFE00) {
   mainRam[addr - 0xE000] = (byte) data;
  } else if (addr < 0xFF00) {
   oam[addr - 0xFE00] = (byte) data;
  } else if (addr <= 0xFFFF) {
   if (addr == 0xFF80) {
//    System.out.println("Register write: " + Bits.hexWord(addr) + " = " + Bits.hexWord(data));
   }
   ioHandler.ioWrite(addr - 0xFF00, (short) data);
//   registers[addr - 0xFF00] = (byte) data;
  } else {
   System.out.println("Attempt to write to address "+ Bits.hexWord(addr));
  }
 }

 /** Sets the value of a register by it's name */
 public boolean setRegister(String reg, int value) {
  if (reg.equals("a") || reg.equals("acc")) {
   a = (short) value;
  } else if (reg.equals("b")) {
   b = (short) value;
  } else if (reg.equals("c")) {
   c = (short) value;
  } else if (reg.equals("d")) {
   d = (short) value;
  } else if (reg.equals("e")) {
   e = (short) value;
  } else if (reg.equals("f")) {
   f = (short) value;
  } else if (reg.equals("h")) {
   hl = (hl & 0x00FF) | (value << 8);
  } else if (reg.equals("l")) {
   hl = (hl & 0xFF00) | value;
  } else if (reg.equals("sp")) {
   sp = value;
  } else if (reg.equals("pc") || reg.equals("ip")) {
   pc = value;                             
  } else if (reg.equals("bc")) {
   b = (short) (value >> 8);
   c = (short) (value & 0x00FF);
  } else if (reg.equals("de")) {
   d = (short) (value >> 8);
   e = (short) (value & 0x00FF);
  } else if (reg.equals("hl")) {
   hl = value;
  } else {
   return false;
  }
  return true;
 }

 public void setBC(int value) {
  b = (short) ((value & 0xFF00) >> 8);
  c = (short) (value & 0x00FF);
 }

 public void setDE(int value) {
  d = (short) ((value & 0xFF00) >> 8);
  e = (short) (value & 0x00FF);
 }

 public void setHL(int value) {
  hl = value;
 }

 /** Performs a read of a register by internal register number */
 public final int registerRead(int regNum) {
  switch (regNum) {
   case 0  : return b;
   case 1  : return c;
   case 2  : return d;
   case 3  : return e;
   case 4  : return (short) ((hl & 0xFF00) >> 8);
   case 5  : return (short) (hl & 0x00FF);
   case 6  : return Bits.unsign(addressRead(hl));
   case 7  : return a;
   default : return -1;
  }
 }

 /** Performs a write of a register by internal register number */
 public final void registerWrite(int regNum, int data) {
  switch (regNum) {
   case 0  : b = (short) data;
             return;
   case 1  : c = (short) data;
             return;
   case 2  : d = (short) data;
             return;
   case 3  : e = (short) data;
             return;
   case 4  : hl = (hl & 0x00FF) | (data << 8);
             return;
   case 5  : hl = (hl & 0xFF00) | data;
             return;
   case 6  : addressWrite(hl, data);
             return;
   case 7  : a = (short) data;
             return;
   default : return;
  }
 }

 public void checkEnableGbc() {
  if ( ((cartridge.rom[0x143] & 0x80) == 0x80) && (allowGbcFeatures)) { // GBC Cartridge ID
   gbcFeatures = true;
  } else {
   gbcFeatures = false;
  }
 }


 /** Resets the CPU to it's power on state.  Memory contents are not cleared. */
 public void reset() {

  checkEnableGbc();
  setDoubleSpeedCpu(false);
  graphicsChip.reset();
  cartridge.reset();
  interruptsEnabled = false;
  halted = false;
  ieDelay = -1;
  pc = 0x0100;
  sp = 0xFFFE;
  f = 0xB0;
  gbcRamBank = 1;
  instrCount = 0;

  if (gbcFeatures) {
   a = 0x11;
  } else {
   a = 0x01;
  }

  for (int r = 0; r < 0x8000; r++) {
   mainRam[r] = 0;
  }

  setBC(0x0013);
  setDE(0x00D8);
  setHL(0x014D);
  

  ioHandler.reset();
//  pc = 0x0100;
 }

 public void setDoubleSpeedCpu(boolean enabled) {

  if (enabled) {
   INSTRS_PER_HBLANK = BASE_INSTRS_PER_HBLANK * 2;
   INSTRS_PER_DIV = BASE_INSTRS_PER_DIV * 2;
  } else {
   INSTRS_PER_HBLANK = BASE_INSTRS_PER_HBLANK;
   INSTRS_PER_DIV = BASE_INSTRS_PER_DIV;
  }

 }

 /** If an interrupt is enabled an the interrupt register shows that it has occured, jump to
  *  the relevant interrupt vector address
  */
 public final void checkInterrupts() {
  int intFlags = ioHandler.registers[0x0F];
  int ieReg = ioHandler.registers[0xFF];
  if ((intFlags & ieReg) != 0) {
   sp -= 2;
   addressWrite(sp + 1, pc >> 8);  // Push current program counter onto stack
   addressWrite(sp, pc & 0x00FF);
   interruptsEnabled = false;

   if ((intFlags & ieReg & INT_VBLANK) != 0) {
    pc = 0x40;                      // Jump to Vblank interrupt address
    intFlags -= INT_VBLANK;
//    System.out.println("VBLANK Interrupt called");
   } else if ((intFlags & ieReg & INT_LCDC) != 0) {
    pc = 0x48;
    intFlags -= INT_LCDC;
//    System.out.println("LCDC Interrupt called");
   } else if ((intFlags & ieReg & INT_TIMA) != 0) {
    pc = 0x50;
    intFlags -= INT_TIMA;
//    System.out.println("TIMA Interrupt called");
   } else if ((intFlags & ieReg & INT_SER) != 0) {
    pc = 0x58;
    intFlags -= INT_SER;
//    System.out.println("TIMA Interrupt called");
   } else if ((intFlags & ieReg & INT_P10) != 0) {	// Joypad interrupt
    pc = 0x60;
    intFlags -= INT_P10;
//	System.out.println("Joypad int.");
   } /* Other interrupts go here, not done yet */

   ioHandler.registers[0x0F] = (byte) intFlags;
   inInterrupt = true;
  }
 }

 /** Initiate an interrupt of the specified type */
 public final void triggerInterrupt(int intr) {
  ioHandler.registers[0x0F] |= intr;
//  System.out.println("Triggered:" + intr);
 }

 public final void triggerInterruptIfEnabled(int intr) {
  if ((ioHandler.registers[0xFF] & (short) (intr)) != 0) ioHandler.registers[0x0F] |= intr;
//  System.out.println("Triggered:" + intr);
 }

 /** Check for interrupts that need to be initiated */
 public final void initiateInterrupts() {
   if (timaEnabled && ((instrCount % instrsPerTima) == 0)) {
    if (Bits.unsign(ioHandler.registers[05]) == 0) {
     ioHandler.registers[05] = ioHandler.registers[06]; // Set TIMA modulo
     if ((ioHandler.registers[0xFF] & INT_TIMA) != 0)
      triggerInterrupt(INT_TIMA);
    }
    ioHandler.registers[05]++;
   }

   if ((instrCount % INSTRS_PER_DIV) == 0) {
    ioHandler.registers[04]++;
   }

   if ((instrCount % INSTRS_PER_HBLANK) == 0) {


    // LCY Coincidence
	// The +1 is due to the LCY register being just about to be incremented
	int cline = Bits.unsign(ioHandler.registers[0x44]) + 1;
	if (cline == 152) cline = 0;

    if (((ioHandler.registers[0xFF] & INT_LCDC) != 0) &&
	     ((ioHandler.registers[0x41] & 64) != 0) &&
       (Bits.unsign(ioHandler.registers[0x45]) == cline) && ((ioHandler.registers[0x40] & 0x80) != 0) && (cline < 0x90)) {
//    System.out.println("Hblank " + cline);
//	 System.out.println("** LCDC Int **");
     triggerInterrupt(INT_LCDC);
    }

	// Trigger on every line
    if (((ioHandler.registers[0xFF] & INT_LCDC) != 0) &&
	     ((ioHandler.registers[0x41] & 0x8) != 0) && ((ioHandler.registers[0x40] & 0x80) != 0) && (cline < 0x90) ) {
//	 System.out.println("** LCDC Int **");
     triggerInterrupt(INT_LCDC);
    }
	
	

    if ((gbcFeatures) && (ioHandler.hdmaRunning)) {
     ioHandler.performHdma();
    }

    if (Bits.unsign(ioHandler.registers[0x44]) == 143) {
//     System.out.println("VBLANK!");
     for (int r = 144; r < 170; r++) {
      graphicsChip.notifyScanline(r);
     } 
     if ( ((ioHandler.registers[0x40] & 0x80) != 0) && ((ioHandler.registers[0xFF] & INT_VBLANK) != 0) ) {
      triggerInterrupt(INT_VBLANK);
	  if ( ((ioHandler.registers[0x41] & 16) != 0) && ((ioHandler.registers[0xFF] & INT_LCDC) != 0) ) {
       triggerInterrupt(INT_LCDC);
//	   System.out.println("VBlank LCDC!");
	  }
     }

    }


	graphicsChip.notifyScanline(Bits.unsign(ioHandler.registers[0x44]));
    ioHandler.registers[0x44] = (byte) (Bits.unsign(ioHandler.registers[0x44]) + 1);
//	System.out.println("Reg 44 = " + Bits.unsign(ioHandler.registers[0x44]));

    if (Bits.unsign(ioHandler.registers[0x44]) >= 153) {
//     System.out.println("VBlank");

     ioHandler.registers[0x44] = 0;
     soundChip.outputSound();
     cartridge.update();
     graphicsChip.frameCompleted();
     frameReady = true;
//     System.out.println("LCDC reset");
    }
   }
 }

 // ------------------------------------------------------------------
 //  Décodage par table : Opcode[256] pour les opcodes de base, Opcode[256]
 //  pour le préfixe CB. Les lambdas sont créées une seule fois à la
 //  construction ; la boucle chaude ne fait que table[op].execute(this).
 // ------------------------------------------------------------------

 @FunctionalInterface
 interface Opcode { void execute(Dmgcpu cpu); }

 private final Opcode[] table = new Opcode[256];
 private final Opcode[] cbTable = new Opcode[256];

 /** Vrai quand le CPU est en HALT (attend une interruption). */
 boolean halted = false;

 // --- accès mémoire courts pour les lambdas ---
 final int rd(int addr) { return addressRead(addr) & 0xFF; }
 final void wr(int addr, int v) { addressWrite(addr, v & 0xFF); }
 final int imm8() { return rd(pc + 1); }
 final int imm16() { return rd(pc + 1) | (rd(pc + 2) << 8); }
 final int bc() { return (b << 8) | c; }
 final int de() { return (d << 8) | e; }
 final int h() { return hl >> 8; }
 final int l() { return hl & 0xFF; }
 final void push(int v) { sp = (sp - 2) & 0xFFFF; wr(sp, v); wr(sp + 1, v >> 8); }
 final int pop() { int v = rd(sp) | (rd(sp + 1) << 8); sp = (sp + 2) & 0xFFFF; return v; }
 final void call(int target) { push(pc); pc = target; }
 final void setAlu(int packed) { a = Alu.result(packed); f = Alu.flags(packed); }
 final boolean cond(int cc) {
  switch (cc) {
   case 0: return (f & F_ZERO) == 0;   // NZ
   case 1: return (f & F_ZERO) != 0;   // Z
   case 2: return (f & F_CARRY) == 0;  // NC
   default: return (f & F_CARRY) != 0; // C
  }
 }
 /** Lecture d'un registre 16 bits par index (BC, DE, HL, SP). */
 final int rr16(int i) { switch (i) { case 0: return bc(); case 1: return de(); case 2: return hl; default: return sp; } }
 final void setRr16(int i, int v) { v &= 0xFFFF; switch (i) { case 0: setBC(v); break; case 1: setDE(v); break; case 2: hl = v; break; default: sp = v; } }

 private void buildTables() {
  // ----- 0x00-0x3F : groupe irrégulier -----
  table[0x00] = cpu -> cpu.pc++;                                            // NOP
  table[0x10] = Dmgcpu::stop;                                               // STOP
  table[0x07] = cpu -> { cpu.setAlu(Alu.clearZ(Alu.rlc(cpu.a))); cpu.pc++; };        // RLCA
  table[0x0F] = cpu -> { cpu.setAlu(Alu.clearZ(Alu.rrc(cpu.a))); cpu.pc++; };        // RRCA
  table[0x17] = cpu -> { cpu.setAlu(Alu.clearZ(Alu.rl(cpu.a, cpu.f))); cpu.pc++; };  // RLA
  table[0x1F] = cpu -> { cpu.setAlu(Alu.clearZ(Alu.rr(cpu.a, cpu.f))); cpu.pc++; };  // RRA
  table[0x08] = cpu -> { int nn = cpu.imm16(); cpu.wr(nn, cpu.sp); cpu.wr(nn + 1, cpu.sp >> 8); cpu.pc += 3; }; // LD (nn),SP
  table[0x18] = cpu -> cpu.pc += 2 + (byte) cpu.imm8();                     // JR e
  table[0x27] = cpu -> { cpu.setAlu(Alu.daa(cpu.a, cpu.f)); cpu.pc++; };    // DAA
  table[0x2F] = cpu -> { cpu.a ^= 0xFF; cpu.f |= F_SUBTRACT | F_HALFCARRY; cpu.pc++; }; // CPL
  table[0x37] = cpu -> { cpu.f = (cpu.f & F_ZERO) | F_CARRY; cpu.pc++; };  // SCF
  table[0x3F] = cpu -> { cpu.f = (cpu.f & F_ZERO) | ((cpu.f & F_CARRY) ^ F_CARRY); cpu.pc++; }; // CCF
  table[0x02] = cpu -> { cpu.wr(cpu.bc(), cpu.a); cpu.pc++; };             // LD (BC),A
  table[0x12] = cpu -> { cpu.wr(cpu.de(), cpu.a); cpu.pc++; };             // LD (DE),A
  table[0x22] = cpu -> { cpu.wr(cpu.hl, cpu.a); cpu.hl = (cpu.hl + 1) & 0xFFFF; cpu.pc++; }; // LD (HL+),A
  table[0x32] = cpu -> { cpu.wr(cpu.hl, cpu.a); cpu.hl = (cpu.hl - 1) & 0xFFFF; cpu.pc++; }; // LD (HL-),A
  table[0x0A] = cpu -> { cpu.a = cpu.rd(cpu.bc()); cpu.pc++; };            // LD A,(BC)
  table[0x1A] = cpu -> { cpu.a = cpu.rd(cpu.de()); cpu.pc++; };            // LD A,(DE)
  table[0x2A] = cpu -> { cpu.a = cpu.rd(cpu.hl); cpu.hl = (cpu.hl + 1) & 0xFFFF; cpu.pc++; }; // LD A,(HL+)
  table[0x3A] = cpu -> { cpu.a = cpu.rd(cpu.hl); cpu.hl = (cpu.hl - 1) & 0xFFFF; cpu.pc++; }; // LD A,(HL-)
  for (int i = 0; i < 4; i++) {
   final int r = i;
   table[0x01 | (r << 4)] = cpu -> { cpu.setRr16(r, cpu.imm16()); cpu.pc += 3; };        // LD rr,nn
   table[0x03 | (r << 4)] = cpu -> { cpu.setRr16(r, cpu.rr16(r) + 1); cpu.pc++; };       // INC rr
   table[0x0B | (r << 4)] = cpu -> { cpu.setRr16(r, cpu.rr16(r) - 1); cpu.pc++; };       // DEC rr
   table[0x09 | (r << 4)] = cpu -> { int p = Alu.add16(cpu.hl, cpu.rr16(r), cpu.f); cpu.hl = Alu.result16(p); cpu.f = Alu.flags16(p); cpu.pc++; }; // ADD HL,rr
   final int cc = i;
   table[0x20 | (cc << 3)] = cpu -> { if (cpu.cond(cc)) cpu.pc += 2 + (byte) cpu.imm8(); else cpu.pc += 2; }; // JR cc,e
   table[0xC0 | (cc << 3)] = cpu -> { if (cpu.cond(cc)) cpu.pc = cpu.pop(); else cpu.pc++; };                // RET cc
   table[0xC2 | (cc << 3)] = cpu -> { if (cpu.cond(cc)) cpu.pc = cpu.imm16(); else cpu.pc += 3; };           // JP cc,nn
   table[0xC4 | (cc << 3)] = cpu -> { int nn = cpu.imm16(); cpu.pc += 3; if (cpu.cond(cc)) cpu.call(nn); };  // CALL cc,nn
  }
  for (int i = 0; i < 8; i++) {
   final int r = i;
   table[0x04 | (r << 3)] = cpu -> { int p = Alu.inc8(cpu.registerRead(r), cpu.f); cpu.registerWrite(r, Alu.result(p)); cpu.f = Alu.flags(p); cpu.pc++; }; // INC r
   table[0x05 | (r << 3)] = cpu -> { int p = Alu.dec8(cpu.registerRead(r), cpu.f); cpu.registerWrite(r, Alu.result(p)); cpu.f = Alu.flags(p); cpu.pc++; }; // DEC r
   table[0x06 | (r << 3)] = cpu -> { cpu.registerWrite(r, cpu.imm8()); cpu.pc += 2; };   // LD r,n
   table[0xC7 | (r << 3)] = cpu -> { cpu.pc++; cpu.call(r * 8); };                       // RST
  }
  // ----- 0x40-0x7F : LD r,r' (0x76 = HALT) -----
  for (int op = 0x40; op < 0x80; op++) {
   final int dst = (op >> 3) & 7, src = op & 7;
   table[op] = cpu -> { cpu.registerWrite(dst, cpu.registerRead(src)); cpu.pc++; };
  }
  table[0x76] = cpu -> { cpu.halted = true; cpu.pc++; };
  // ----- 0x80-0xBF : ALU A,r  et  0xC6/CE/D6/DE/E6/EE/F6/FE : ALU A,n -----
  for (int opn = 0; opn < 8; opn++) {
   final int o = opn;
   for (int r = 0; r < 8; r++) {
    final int rr = r;
    table[0x80 | (o << 3) | r] = cpu -> { cpu.alu(o, cpu.registerRead(rr)); cpu.pc++; };
   }
   table[0xC6 | (o << 3)] = cpu -> { cpu.alu(o, cpu.imm8()); cpu.pc += 2; };
  }
  // ----- 0xC0-0xFF : reste -----
  table[0xC1] = cpu -> { cpu.setBC(cpu.pop()); cpu.pc++; };
  table[0xD1] = cpu -> { cpu.setDE(cpu.pop()); cpu.pc++; };
  table[0xE1] = cpu -> { cpu.hl = cpu.pop(); cpu.pc++; };
  table[0xF1] = cpu -> { int v = cpu.pop(); cpu.a = v >> 8; cpu.f = v & 0xF0; cpu.pc++; };
  table[0xC5] = cpu -> { cpu.push(cpu.bc()); cpu.pc++; };
  table[0xD5] = cpu -> { cpu.push(cpu.de()); cpu.pc++; };
  table[0xE5] = cpu -> { cpu.push(cpu.hl); cpu.pc++; };
  table[0xF5] = cpu -> { cpu.push((cpu.a << 8) | (cpu.f & 0xF0)); cpu.pc++; };
  table[0xC3] = cpu -> cpu.pc = cpu.imm16();                                // JP nn
  table[0xC9] = cpu -> cpu.pc = cpu.pop();                                  // RET
  table[0xD9] = cpu -> { cpu.pc = cpu.pop(); cpu.interruptsEnabled = true; cpu.inInterrupt = false; }; // RETI
  table[0xCD] = cpu -> { int nn = cpu.imm16(); cpu.pc += 3; cpu.call(nn); }; // CALL nn
  table[0xCB] = cpu -> { int op = cpu.imm8(); cpu.pc += 2; cpu.cbTable[op].execute(cpu); };
  table[0xE0] = cpu -> { cpu.wr(0xFF00 + cpu.imm8(), cpu.a); cpu.pc += 2; }; // LDH (n),A
  table[0xF0] = cpu -> { cpu.a = cpu.rd(0xFF00 + cpu.imm8()); cpu.pc += 2; }; // LDH A,(n)
  table[0xE2] = cpu -> { cpu.wr(0xFF00 + cpu.c, cpu.a); cpu.pc++; };        // LD (C),A
  table[0xF2] = cpu -> { cpu.a = cpu.rd(0xFF00 + cpu.c); cpu.pc++; };       // LD A,(C)
  table[0xEA] = cpu -> { cpu.wr(cpu.imm16(), cpu.a); cpu.pc += 3; };        // LD (nn),A
  table[0xFA] = cpu -> { cpu.a = cpu.rd(cpu.imm16()); cpu.pc += 3; };       // LD A,(nn)
  table[0xE8] = cpu -> { int p = Alu.addSpSigned(cpu.sp, cpu.imm8()); cpu.sp = Alu.result16(p); cpu.f = Alu.flags16(p); cpu.pc += 2; }; // ADD SP,e
  table[0xF8] = cpu -> { int p = Alu.addSpSigned(cpu.sp, cpu.imm8()); cpu.hl = Alu.result16(p); cpu.f = Alu.flags16(p); cpu.pc += 2; }; // LD HL,SP+e
  table[0xE9] = cpu -> cpu.pc = cpu.hl;                                     // JP HL
  table[0xF9] = cpu -> { cpu.sp = cpu.hl; cpu.pc++; };                      // LD SP,HL
  table[0xF3] = cpu -> { cpu.interruptsEnabled = false; cpu.ieDelay = -1; cpu.pc++; }; // DI
  table[0xFB] = cpu -> { cpu.ieDelay = 1; cpu.pc++; };                      // EI (effet différé d'une instruction)
  // Opcodes illégaux : traités comme NOP pour ne pas figer l'émulation
  for (int op = 0; op < 256; op++) if (table[op] == null) { final int bad = op; table[op] = cpu -> { System.out.println("Opcode illégal " + Bits.hexByte(bad) + " en " + Bits.hexWord(cpu.pc)); cpu.pc++; }; }

  // ----- préfixe CB : 8 opérations de décalage × 8 registres, puis BIT/RES/SET -----
  for (int r = 0; r < 8; r++) {
   final int rr = r;
   cbTable[0x00 | r] = cpu -> cpu.cbSet(rr, Alu.rlc(cpu.registerRead(rr)));
   cbTable[0x08 | r] = cpu -> cpu.cbSet(rr, Alu.rrc(cpu.registerRead(rr)));
   cbTable[0x10 | r] = cpu -> cpu.cbSet(rr, Alu.rl(cpu.registerRead(rr), cpu.f));
   cbTable[0x18 | r] = cpu -> cpu.cbSet(rr, Alu.rr(cpu.registerRead(rr), cpu.f));
   cbTable[0x20 | r] = cpu -> cpu.cbSet(rr, Alu.sla(cpu.registerRead(rr)));
   cbTable[0x28 | r] = cpu -> cpu.cbSet(rr, Alu.sra(cpu.registerRead(rr)));
   cbTable[0x30 | r] = cpu -> cpu.cbSet(rr, Alu.swap(cpu.registerRead(rr)));
   cbTable[0x38 | r] = cpu -> cpu.cbSet(rr, Alu.srl(cpu.registerRead(rr)));
   for (int n = 0; n < 8; n++) {
    final int bit = n;
    cbTable[0x40 | (n << 3) | r] = cpu -> cpu.f = Alu.flags(Alu.bit(bit, cpu.registerRead(rr), cpu.f));
    cbTable[0x80 | (n << 3) | r] = cpu -> cpu.registerWrite(rr, cpu.registerRead(rr) & ~(1 << bit));
    cbTable[0xC0 | (n << 3) | r] = cpu -> cpu.registerWrite(rr, cpu.registerRead(rr) | (1 << bit));
   }
  }
 }

 final void cbSet(int r, int packed) { registerWrite(r, Alu.result(packed)); f = Alu.flags(packed); }

 /** Opération ALU 0-7 (ADD, ADC, SUB, SBC, AND, XOR, OR, CP) sur A. */
 final void alu(int op, int v) {
  int carry = (f & F_CARRY) != 0 ? 1 : 0;
  switch (op) {
   case 0: setAlu(Alu.add8(a, v)); break;
   case 1: setAlu(Alu.adc8(a, v, carry)); break;
   case 2: setAlu(Alu.sub8(a, v)); break;
   case 3: setAlu(Alu.sbc8(a, v, carry)); break;
   case 4: setAlu(Alu.and8(a, v)); break;
   case 5: setAlu(Alu.xor8(a, v)); break;
   case 6: setAlu(Alu.or8(a, v)); break;
   default: f = Alu.flags(Alu.cp8(a, v)); break;
  }
 }

 private void stop() {
  pc += 2;
  if (gbcFeatures && (ioHandler.registers[0x4D] & 0x01) == 1) {      // commutation de vitesse GBC
   int key1 = ioHandler.registers[0x4D] & 0xFE;
   if ((key1 & 0x80) == 0x80) { setDoubleSpeedCpu(false); key1 &= 0x7F; }
   else { setDoubleSpeedCpu(true); key1 |= 0x80; }
   ioHandler.registers[0x4D] = (byte) key1;
  }
 }

 /** Exécute jusqu'à la fin de l'image courante (VBlank), puis rend la main à l'hôte. */
 public void runFrame() {
  frameReady = false;
  stopAtFrame = true;
  execute(-1);
  stopAtFrame = false;
 }

 /** Exécute le nombre d'instructions donné (-1 : sans limite, jusqu'à terminate ou fin d'image). */
 public final void execute(int numInstr) {
  terminate = false;
  running = true;
  final Opcode[] t = table;
  final byte[] io = ioHandler.registers;

  for (int r = 0; (r != numInstr) && !terminate && !(stopAtFrame && frameReady); r++) {
   instrCount++;

   if (halted) {
    if ((io[0x0F] & io[0xFF] & 0x1F) != 0) halted = false;     // réveil : IF & IE
   } else {
    t[rd(pc)].execute(this);
    pc &= 0xFFFF;
   }

   if (ieDelay != -1) {
    if (ieDelay > 0) ieDelay--;
    else { interruptsEnabled = true; ieDelay = -1; }
   }
   if (interruptsEnabled && !halted) checkInterrupts();

   initiateInterrupts();
  }
  running = false;
  terminate = false;
 }

 public void setBreakpoint(boolean on) {
  breakpointEnable = on;
 }

 /** Output a disassembly of the specified number of instructions starting at the speicifed address.
  */
 public String disassemble(int address, int numInstr) {

  System.out.println("Addr  Data      Instruction");

  for (int r = 0; r < numInstr; r++) {
   int b1 = Bits.unsign(addressRead(address));
   short offset = addressRead(address + 1);
   int b3 = Bits.unsign(addressRead(address + 2));
   int b2 = Bits.unsign(offset);

   String instr = new String("Unknown Opcode! (" + Integer.toHexString(b1) + ")");
   byte instrLength = 1;

   switch (b1) {
    case 0x00 :
           instr = "NOP";
           break;
    case 0x01 :
           instr = "LD BC, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0x02 :
           instr = "LD (BC), A";
           break;
    case 0x03 :
           instr = "INC BC";
           break;
    case 0x04 :
           instr = "INC B";
           break;
    case 0x05 :
           instr = "DEC B";
           break;
    case 0x06 :
           instr = "LD B, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0x07 :
           instr = "RLC A";
           break;
    case 0x08 :
           instr = "LD (" + Bits.hexWord((b3 << 8) + b2) + "), SP";
           instrLength = 3;        // Non Z80
           break;
    case 0x09 :
           instr = "ADD HL, BC";
           break;
    case 0x0A :
           instr = "LD A, (BC)";
           break;
    case 0x0B :
           instr = "DEC BC";
           break;
    case 0x0C :
           instr = "INC C";
           break;
    case 0x0D :
           instr = "DEC C";
           break;
    case 0x0E :
           instr = "LD C, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0x0F :
           instr = "RRC A";
           break;
    case 0x10 :
           instr = "STOP";
           instrLength = 2;  // STOP instruction must be followed by a NOP
           break;
    case 0x11 :
           instr = "LD DE, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0x12 :
           instr = "LD (DE), A";
           break;
    case 0x13 :
           instr = "INC DE";
           break;
    case 0x14 :
           instr = "INC D";
           break;
    case 0x15 :
           instr = "DEC D";
           break;
    case 0x16 :
           instr = "LD D, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0x17 :
           instr = "RL A";
           break;
    case 0x18 :
           instr = "JR " + Bits.hexWord(address + 2 + offset);
           instrLength = 2;
           break;
    case 0x19 :
           instr = "ADD HL, DE";
           break;
    case 0x1A :
           instr = "LD A, (DE)";
           break;
    case 0x1B :
           instr = "DEC DE";
           break;
    case 0x1C :
           instr = "INC E";
           break;
    case 0x1D :
           instr = "DEC E";
           break;
    case 0x1E :
           instr = "LD E, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0x1F :
           instr = "RR A";
           break;
    case 0x20 :
           instr = "JR NZ, " + Bits.hexWord(address + 2 + offset) + ": " + offset;
           
           instrLength = 2;
           break;
    case 0x21 :
           instr = "LD HL, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0x22 :
           instr = "LD (HL+), A";     // Non Z80
           break;
    case 0x23 :
           instr = "INC HL";
           break;
    case 0x24 :
           instr = "INC H";
           break;
    case 0x25 :
           instr = "DEC H";
           break;
    case 0x26 :
           instr = "LD H, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0x27 :
           instr = "DAA";
           break;
    case 0x28 :
           instr = "JR Z, " + Bits.hexWord(address + 2 + offset);
           instrLength = 2;
           break;
    case 0x29 :
           instr = "ADD HL, HL";
           break;
    case 0x2A :
           instr = "LDI A, (HL)";
           break;
    case 0x2B :
           instr = "DEC HL";
           break;
    case 0x2C :
           instr = "INC L";
           break;
    case 0x2D :
           instr = "DEC L";
           break;
    case 0x2E :
           instr = "LD L, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0x2F :
           instr = "CPL";
           break;
    case 0x30 :
           instr = "JR NC, " + Bits.hexWord(address + 2 + offset);
           instrLength = 2;
           break;
    case 0x31 :
           instr = "LD SP, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0x32 :
           instr = "LD (HL-), A";
           break;
    case 0x33 :
           instr = "INC SP";
           break;
    case 0x34 :
           instr = "INC (HL)";
           break;
    case 0x35 :
           instr = "DEC (HL)";
           break;
    case 0x36 :
           instr = "LD (HL), " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0x37 :
           instr = "SCF";     // Set carry flag? 
           break;
    case 0x38 :
           instr = "JR C, " + Bits.hexWord(address + 2 + offset);
           instrLength = 2;
           break;
    case 0x39 :
           instr = "ADD HL, SP";
           break;
    case 0x3A :
           instr = "LD A, (HL-)";
           break;
    case 0x3B :
           instr = "DEC SP";
           break;
    case 0x3C :
           instr = "INC A";
           break;
    case 0x3D :
           instr = "DEC A";
           break;
    case 0x3E :
           instr = "LD A, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0x3F :
           instr = "CCF";   // Clear carry flag? 
           break;

    case 0x76 :
           instr = "HALT";
           break;

    // 0x40 - 0x7F = LD Reg, Reg - see below
    // 0x80 - 0xBF = ALU ops - see below

    case 0xC0 :
           instr = "RET NZ";
           break;
    case 0xC1 :
           instr = "POP BC";
           break;
    case 0xC2 :
           instr = "JP NZ, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0xC3 :
           instr = "JP " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0xC4 :
           instr = "CALL NZ, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0xC5 :
           instr = "PUSH BC";
           break;
    case 0xC6 :
           instr = "ADD A, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0xC7 :
           instr = "RST 00";      // Is this an interrupt call?
           break;
    case 0xC8 :
           instr = "RET Z";
           break;
    case 0xC9 :
           instr = "RET";
           break;
    case 0xCA :
           instr = "JP Z, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;

    // 0xCB = Shifts (see below)

    case 0xCC :
           instr = "CALL Z, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0xCD :
           instr = "CALL " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0xCE :
           instr = "ADC A, " + Bits.hexByte(b2);  // Signed or unsigned?
           instrLength = 2;
           break;
    case 0xCF :
           instr = "RST 08";      // Is this an interrupt call?
           break;
    case 0xD0 :
           instr = "RET NC";
           break;
    case 0xD1 :
           instr = "POP DE";
           break;
    case 0xD2 :
           instr = "JP NC, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;

    // 0xD3: Unknown

    case 0xD4 :
           instr = "CALL NC, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;
    case 0xD5 :
           instr = "PUSH DE";
           break;
    case 0xD6 :
           instr = "SUB A, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0xD7 :
           instr = "RST 10";
           break;
    case 0xD8 :
           instr = "RET C";
           break;
    case 0xD9 :
           instr = "RETI";
           break;
    case 0xDA :
           instr = "JP C, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;

    // 0xDB: Unknown

    case 0xDC :
           instr = "CALL C, " + Bits.hexWord((b3 << 8) + b2);
           instrLength = 3;
           break;

    // 0xDD: Unknown

    case 0xDE :
           instr = "SBC A, " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0xDF :
           instr = "RST 18";
           break;
    case 0xE0 :
           instr = "LDH (FF" + Bits.hexByte(b2 & 0xFF) + "), A";
           instrLength = 2;
           break;
    case 0xE1 :
           instr = "POP HL";
           break;
    case 0xE2 :
           instr = "LDH (FF00 + C), A";
           break;

    // 0xE3 - 0xE4: Unknown

    case 0xE5 :
           instr = "PUSH HL";
           break;
    case 0xE6 :
           instr = "AND " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0xE7 :
           instr = "RST 20";
           break;
    case 0xE8 :
           instr = "ADD SP, " + Bits.hexByte(offset);
           instrLength = 2;
           break;
    case 0xE9 :
           instr = "JP (HL)";
           break;
    case 0xEA :
           instr = "LD (" + Bits.hexWord((b3 << 8) + b2) + "), A";
           instrLength = 3;
           break;

    // 0xEB - 0xED: Unknown

    case 0xEE :
           instr = "XOR " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0xEF :
           instr = "RST 28";
           break;
    case 0xF0 :
           instr = "LDH A, (FF" + Bits.hexByte(b2) + ")";
           instrLength = 2;
           break;
    case 0xF1 :
           instr = "POP AF";
           break;
    case 0xF2 :
           instr = "LD A, (FF00 + C)";              // What's this for?
           break;
    case 0xF3 :
           instr = "DI";
           break;

    // 0xF4: Unknown

    case 0xF5 :
           instr = "PUSH AF";
           break;
    case 0xF6 :
           instr = "OR " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0xF7 :
           instr = "RST 30";
           break;
    case 0xF8 :
           instr = "LD HL, SP + " + Bits.hexByte(offset);  // Check this one, docs disagree
           instrLength = 2;
           break;
    case 0xF9 :
           instr = "LD SP, HL";
           break;
    case 0xFA :
           instr = "LD A, (" + Bits.hexWord((b3 << 8) + b2) + ")";
           instrLength = 3;
           break;
    case 0xFB :
           instr = "EI";
           break;

    // 0xFC - 0xFD: Unknown

    case 0xFE :
           instr = "CP " + Bits.hexByte(b2);
           instrLength = 2;
           break;
    case 0xFF :
           instr = "RST 38";
           break;
   }

 // The following section handles LD Reg, Reg instructions
 // Bit 7 6 5 4 3 2 1 0    D = Dest register
 //     0 1 D D D S S S    S = Source register
 // The exception to this rule is 0x76, which is HALT, and takes
 // the place of LD (HL), (HL)

   if ((Bits.unsign(b1) >= 0x40) && (Bits.unsign(b1) <= 0x7F) && (
       (Bits.unsign(b1) != 0x76))) {
    /* 0x76 is HALT, and takes the place of LD (HL), (HL) */
    int sourceRegister = b1 & 0x07;         /* Lower 3 bits */
    int destRegister = (b1 & 0x38) >> 3;    /* Bits 5 - 3 */

//    System.out.println("LD Op src" + sourceRegister + " dest " + destRegister);

    instr = "LD " + registerNames[destRegister] + ", " +
                    registerNames[sourceRegister];
   }
   
 // The following section handles arithmetic instructions
 // Bit 7 6 5 4 3 2 1 0    Operation       Opcode
 //     1 0 0 0 0 R R R    Add             ADD
 //     1 0 0 0 1 R R R    Add with carry  ADC
 //     1 0 0 1 0 R R R    Subtract        SUB
 //     1 0 0 1 1 R R R    Sub with carry  SBC
 //     1 0 1 0 0 R R R    Logical and     AND
 //     1 0 1 0 1 R R R    Logical xor     XOR
 //     1 0 1 1 0 R R R    Logical or      OR
 //     1 0 1 1 1 R R R    Compare?        CP

   if ((Bits.unsign(b1) >= 0x80) && (Bits.unsign(b1) <= 0xBF)) {
    int sourceRegister = Bits.unsign(b1) & 0x07;
    int operation = (Bits.unsign(b1) & 0x38) >> 3;

 //   System.out.println("ALU Op " + operation + " reg " + sourceRegister);

    instr = aluOperations[operation] + " A, " + registerNames[sourceRegister];
   }

 // The following section handles shift instructions
 // These are formed by the byte 0xCB followed by the this:
 // Bit 7 6 5 4 3 2 1 0    Operation             Opcode
 //     0 0 0 0 0 R R R    Rotate Left Carry     RLC
 //     0 0 0 0 1 R R R    Rotate Right Carry    RRC
 //     0 0 0 1 0 R R R    Rotate Left           RL
 //     0 0 0 1 1 R R R    Rotate Right          RR
 //     0 0 1 0 0 R R R    Arith. Shift Left     SLA
 //     0 0 1 0 1 R R R    Arith. Shift Right    SRA
 //     0 0 1 1 0 R R R    Hi/Lo Nibble Swap     SWAP
 //     0 0 1 1 1 R R R    Shift Right Logical   SRL
 //     0 1 N N N R R R    Bit Test n            BIT
 //     1 0 N N N R R R    Reset Bit n           RES
 //     1 1 N N N R R R    Set Bit n             SET

   if (Bits.unsign(b1) == 0xCB) {
    int operation;
    int sourceRegister;
    int bitNumber;

    instrLength = 2;

    switch ((Bits.unsign(b2) & 0xC0) >> 6) {
     case 0 :
      operation = (Bits.unsign(b2) & 0x38) >> 3;
      sourceRegister = Bits.unsign(b2) & 0x07;
      instr = shiftOperations[operation] + " " + registerNames[sourceRegister];
      break;
     case 1 :
      bitNumber = (Bits.unsign(b2) & 0x38) >> 3;
      sourceRegister = Bits.unsign(b2) & 0x07;
      instr = "BIT " + bitNumber + ", " + registerNames[sourceRegister];
      break;
     case 2 :
      bitNumber = (Bits.unsign(b2) & 0x38) >> 3;
      sourceRegister = Bits.unsign(b2) & 0x07;
      instr = "RES " + bitNumber + ", " + registerNames[sourceRegister];
      break;
     case 3 :
      bitNumber = (Bits.unsign(b2) & 0x38) >> 3;
      sourceRegister = Bits.unsign(b2) & 0x07;
      instr = "SET " + bitNumber + ", " + registerNames[sourceRegister];
      break;
    }
   }


   System.out.print(Bits.hexWord(address) + ": " + Bits.hexByte(Bits.unsign(b1)));

   if (instrLength >= 2) {
    System.out.print(" " + Bits.hexByte(b2));
   } else {
    System.out.print("   ");
   }

   if (instrLength == 3) {
    System.out.print(" " + Bits.hexByte(Bits.unsign(b3))+ "  ");
   } else {
    System.out.print("     ");
   }


   System.out.println(instr);
   address += instrLength;
  }


  return null;
 }
}
