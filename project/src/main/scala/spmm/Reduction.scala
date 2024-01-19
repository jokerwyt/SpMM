package spmm

import chisel3._
import chisel3.util._

class Reduction extends Module {
  val io = IO(new Bundle {
    val input = Input(Shot())
    val output = Output(Shot())
  })
  io.output.default_value();


  // lhs相等的必然是区间。将这个区间的product相加，加到最后一个上.
  // 用尽量少的F44加法器，不能stall（每个周期必须读入一个shot）
  // 没有延迟限制

  // 先写一个最简单的，latency=16的前缀和 + 最后一个周期的n^2个加法器?

  val stages = Seq.tabulate(16)( _ => Reg(Shot()));
  for (i <- 0 until 16) {
    stages(i).default_value();
  }

  when (true.B) {
    // input to stage 0
    stages(0) := io.input;
    stages(0).prefix_sum := io.input.products;
  }

  for (i <- 1 until 16) {
    when (true.B) {
      stages(i) := RegNext(stages(i-1))
      stages(i).prefix_sum(i) := stages(i-1).prefix_sum(i-1) + stages(i-1).products(i);
    }
  }

  // stage 15 to output
  io.output := stages(15);

  // for every i in [0, 15]
  // find the left and right bound of the interval with the same lhsRow
  // add the product of the interval to the right bound

  for (i <- 0 until 16) {
    val left = WireDefault(0.U(8.W))
    val right = WireDefault(0.U(8.W))

    for (j <- 0 until 16) {
      when (stages(15).lhsRow(j) === stages(15).lhsRow(i)) {
        right := j.U
      }
    }

    // from 15 to 0
    for (j <- 15 to 0 by -1) {
      when (stages(15).lhsRow(j) === stages(15).lhsRow(i)) {
        left := j.U
      }
    }

    io.output.reduced_sum(i) := Mux(left === 0.U, 
      stages(15).prefix_sum(right),
      stages(15).prefix_sum(right) - stages(15).prefix_sum(left-1.U));
  }
}