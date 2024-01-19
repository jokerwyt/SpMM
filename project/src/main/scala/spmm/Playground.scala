package playground

import chisel3._
import chisel3.util._

class Playground extends Module {
  val io = IO(new Bundle {
    val read_idx = Input(UInt(4.W))
    val write_idx = Input(UInt(4.W))
    val write_data = Input(Vec(3, UInt(8.W)))
    val read_data_output = Output(Vec(3, UInt(8.W)))
  })

  val regs = SyncReadMem(16, Vec(3, UInt(8.W)), SyncReadMem.WriteFirst)

  when (true.B) {
    regs.write(io.write_idx, io.write_data)
    io.read_data_output := regs.read(io.read_idx)
  }.otherwise {
    io.read_data_output := VecInit(Seq.fill(3)(0.U(8.W)))
  }
}


// object Main {
//   def main(args: Array[String]): Unit = {
//       emitVerilog(new Playground)
//   }
// }
