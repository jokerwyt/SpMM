package spmm

import chisel3._
import chisel3.util._

class ElementwiseProduct extends Module {
  val io = IO(new Bundle {
    val input = Input(Shot())
    val output = Output(Shot())
  })
  io.output.default_value();

}