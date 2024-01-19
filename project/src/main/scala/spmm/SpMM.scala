package spmm

import chisel3._
import chisel3.util._

class SpMMIO extends Bundle {
  val start = Input(Bool())

  val inputReady = Output(Bool())

  // CAUTIOUS: end index of row[i] in CSR format, instead of row number.
  val lhsRowIdx = Input(Vec(16, UInt(8.W))) 

  val lhsCol = Input(Vec(16, UInt(8.W)))
  val lhsData = Input(Vec(16, F44()))

  val rhsReset = Input(Bool())
  val rhsData = Input(Vec(16, F44()))

  val outData = Output(Vec(16, F44()))
  val outValid = Output(Bool())

  def output_default_value(): Unit = {
    outData.foreach { _ := F44.zero }
    outValid := false.B
    inputReady := false.B
  }
}

class SpMM extends Module {
  val io = IO(new SpMMIO)

  io.output_default_value()

  val issueUnit = Module(IssueUnit());
  // link issueUnit to SpMM
  {
    issueUnit.io.start := io.start
    issueUnit.io.lhsRowEnding := io.lhsRowIdx
    issueUnit.io.lhsCol := io.lhsCol
    issueUnit.io.lhsData := io.lhsData
    issueUnit.io.rhsReset := io.rhsReset
    issueUnit.io.rhsData := io.rhsData

    io.inputReady := issueUnit.io.inputReady
  }

  val debug = IO(Output(issueUnit.io.shot.cloneType))
  debug := issueUnit.io.shot
}


object Main {
  def main(args: Array[String]): Unit = {
      emitVerilog(new SpMM)
  }
}
