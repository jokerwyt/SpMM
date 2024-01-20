package spmm

import chisel3._
import chisel3.util._

class CollectUnit extends Module {
  val io = IO(new Bundle {
    val input = Input(Shot())
    val output = Output(new Bundle {
      val valid = Bool()
      val outData = Vec(16, F44())
    })
  })

  io.output.valid := false.B
  io.output.outData.foreach { _ := F44.zero }

  // 每个周期接收到一个shot.
  // 收到Iter == 16，将lastData刷入mem.
  // 如果一轮输出结束，重置所有状态

  val inputReg0 = RegNext(io.input, Shot.invalid)
  val inputReg1 = RegNext(inputReg0, Shot.invalid)
  val inputReg2 = RegNext(inputReg1, Shot.invalid)

  // border register
  val lastData = RegInit(F44.zero)        // 尚未插入的数据
  val lastIter = RegInit(0.U(9.W))        // 尚未插入的数据的iter     (C^T row number)
  val lastRow = RegInit(0.U(9.W))         // 尚未插入的数据的rowNum   (C^T col number)
                                          // 这个row意思是A中的row num

  when (true.B) {
    lastData := io.input.reduced_sum(15)
    lastIter := io.input.iter
    lastRow := io.input.lhsRow(15)

    inputReg0.merged := io.input.iter === lastIter && 
      io.input.lhsRow(0) === lastRow;
    inputReg0.lastData := lastData
    inputReg0.previousReducedSum := lastData
    inputReg0.previousIter := lastIter
    inputReg0.previousLhsRow := lastRow

    val preAdd = inputReg0.lastData + inputReg0.reduced_sum(inputReg0.getCollectMetadata().firstValid);

    when (inputReg1.merged) {
      inputReg2.reduced_sum(inputReg1.getCollectMetadata().firstValid) := preAdd;
    }
  }


  val nextRowOutput = RegInit(0.U(9.W));

  val CTmem = Reg(Vec(17, Vec(17, F44())))
  val iterArrived = RegInit(0.U(9.W))       // 最后插入的元素的C^T中的行号

  val validWire = WireDefault(false.B)
  val outputWire = WireDefault(VecInit(Seq.fill(16)(F44.zero)))
  io.output.valid := validWire
  io.output.outData := outputWire

  when (true.B) {
    // reset logic.
    when (inputReg2.iter === 16.U && nextRowOutput === 16.U) {
      // reset all states
      lastData := F44.zero
      lastIter := 0.U
      lastRow := 0.U

      iterArrived := 0.U
      nextRowOutput := 0.U

      // rset CTmem
      for (i <- 0 until 17) {
        for (j <- 0 until 17) {
          CTmem(i)(j) := F44.zero
        }
      }
    }
  }

  when (true.B) {

    // fill into mem & iterArrived logic

    when (iterArrived =/= 16.U) {
      iterArrived := Mux(inputReg2.iter === iterArrived + 1.U(9.W), inputReg2.iter, iterArrived)
    }
    
    for (i <- 0 until 15) {
      when (inputReg2.getCollectMetadata().valid(i)) {
        CTmem(inputReg2.iter)(inputReg2.lhsRow(i)) := inputReg2.reduced_sum(i)
      }
    }

    when (inputReg2.merged === false.B) {
      CTmem(inputReg2.previousIter)(inputReg2.previousLhsRow) := inputReg2.previousReducedSum
    }
  }


  when (true.B) {
    // output logic
    // can we output more ?
    when (nextRowOutput < iterArrived) {
      // output last one
      validWire := true.B
      for (i <- 0 until 16) {
        outputWire(i) := CTmem(nextRowOutput)(i)
      }
      nextRowOutput := nextRowOutput + 1.U
    } .otherwise {
      validWire := false.B;
    }
  }
}