package spmm

import chisel3._
import chisel3.util._

class SpMMIO extends Bundle {
  val start = Input(Bool())

  val inputReady = Output(Bool())
  val lhsRowIdx = Input(Vec(16, UInt(8.W)))
  val lhsCol = Input(Vec(16, UInt(8.W)))
  val lhsData = Input(Vec(16, F44()))

  val rhsReset = Input(Bool())
  val rhsData = Input(Vec(16, F44()))

  val outData = Output(Vec(16, F44()))
  val outValid = Output(Bool())
}

class SpMM extends Module {
    val io = IO(new SpMMIO)
    io.inputReady := false.B
    io.outValid := false.B
    io.outData := DontCare
    io.outData(0) := io.lhsData(0) + io.lhsData(1)
}

object Main {
    def main(args: Array[String]): Unit = {
        emitVerilog(new SpMM)
    }
}
