package spmm

import chisel3._
import chisel3.util._

class ElementwiseProduct extends Module {
  val io = IO(new Bundle {
    val input = Input(Shot())
    val output = Output(Shot())
  })
  io.output.default_value();

  io.output := RegNext(io.input, Shot.invalid);

  when (true.B) {
    for (i <- 0 until 16) {
      io.output.products(i) := io.input.lhsData(i) * io.input.rhsData(i);
    }
  }
}