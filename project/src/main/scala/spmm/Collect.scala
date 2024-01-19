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

  val nextRowOutput = RegInit(0.U(8.W));
  val maxIter = RegInit(0.U(8.W));


  val CTmem = Reg(Vec(17, Vec(17, F44())))
  val newestIter = RegInit(0.U(8.W))       // 最后插入的元素的C^T中的行号

  val hasLastData = RegInit(false.B)      // 是否有尚未插入的数据
  val lastData = RegInit(F44.zero)        // 尚未插入的数据
  val lastIter = RegInit(0.U(8.W))        // 尚未插入的数据的iter     (C^T row number)
  val lastRow = RegInit(0.U(8.W))         // 尚未插入的数据的rowNum   (C^T col number)
                                          // 这个row意思是A中的row num


  when (true.B) {
    // calculate part
    // 先找出所有最后的reduced_sum[i]
    // reduced_sum[i]最终应该加到C^T的(iter, lhsRow[i])上
    
    when (io.input.iter === 16.U) {

      when (hasLastData) {
        CTmem(lastIter)(lastRow) := lastData
        newestIter := lastIter
        hasLastData := false.B
      }
      newestIter := 16.U

    } .otherwise {

      val valid = Wire(Vec(16, Bool()))

      valid(15) := true.B
      for (i <- 0 until 15) {
        valid(i) := io.input.lhsRow(i) =/= io.input.lhsRow(i+1)
      }
      
      
      val firstValidTmp = Seq.tabulate(16) { _ => WireDefault(16.U(8.W))};

      firstValidTmp(15) := 15.U(8.W)
      for (i <- 14 until 0 by -1) {
        firstValidTmp(i) := Mux(valid(i), i.U, firstValidTmp(i + 1))
      }
      val firstValidIdx = firstValidTmp(0)
      val lastValidIdx = 15.U(8.W)

      when (firstValidIdx === lastValidIdx) {
        // 判断是否要与lastData合并
        when (hasLastData && lastIter === io.input.iter && io.input.lhsRow(firstValidIdx) === lastRow) {
          // 合并
          // 至多合并一次，直接将合并后的结果写入
          CTmem(lastIter)(lastRow) := lastData + io.input.reduced_sum(firstValidIdx)
          newestIter := RegNext(lastIter) // 加法有一周期延迟，确保当结果刷入时newestIter才更新
          hasLastData := false.B

        } .otherwise {
          // 不合并, lastData写入mem，取代lastData
          when (hasLastData) {
            CTmem(lastIter)(lastRow) := CTmem(lastIter)(lastRow) + lastData
            newestIter := RegNext(lastIter) // 加法有一周期延迟，确保当结果刷入时newestIter才更新
          }
          
          lastData := io.input.reduced_sum(firstValidIdx)
          lastIter := io.input.iter
          lastRow := io.input.lhsRow(firstValidIdx)
          hasLastData := true.B
        }

      } .otherwise {
        // consider firstValidIdx
        // merge with previous?

        val firstMerged = WireDefault(false.B)
        when (hasLastData && lastIter === io.input.iter && io.input.lhsRow(firstValidIdx) === lastRow) {
          // 合并
          // 至多合并一次，直接将合并后的结果写入
          CTmem(lastIter)(lastRow) := lastData + io.input.reduced_sum(firstValidIdx)
          firstMerged := true.B
          hasLastData := false.B

        } .otherwise {
          // 不合并, lastData写入mem
          when (hasLastData) {
            CTmem(lastIter)(lastRow) := lastData
          }
          hasLastData := false.B
        }

        // more than one valid
        // 中间的直接插入
        val midInsert = WireDefault(false.B)
        for (i <- 0 until 16) {
          when (valid(i) =/= firstValidIdx && valid(i) =/= lastValidIdx) {
            CTmem(io.input.iter)(io.input.lhsRow(i)) := io.input.reduced_sum(i)
            midInsert := true.B
          }
        }
        
        hasLastData := true.B
        lastData := io.input.reduced_sum(lastValidIdx)
        lastIter := io.input.iter
        lastRow := io.input.lhsRow(lastValidIdx)

        when (firstMerged || midInsert) {
          when (firstMerged && !midInsert) {
            newestIter := RegNext(io.input.iter)
          } .otherwise {
            newestIter := io.input.iter
          }
        } .otherwise {
          // do nothing
        }
      }
    }
  }

  when (true.B) {
    // output part.
    
    val maxIterWire = Mux(io.input.iter > maxIter, io.input.iter, maxIter)
    maxIter := maxIterWire

    when (nextRowOutput === 16.U) {
      // one round of output finished
      // reset all states
      nextRowOutput := 0.U
      maxIter := 0.U
      hasLastData := false.B
      CTmem.foreach { _ := VecInit(Seq.fill(17)(F44.zero)) }
    } .otherwise {
      // can we output more ?
      when (nextRowOutput < newestIter) {
        // output last one
        io.output.valid := true.B
        for (i <- 0 until 16) {
          io.output.outData(i) := CTmem(nextRowOutput)(i)
        }
        nextRowOutput := nextRowOutput + 1.U
      } .otherwise {
        // output nothing
      }
    }
  }
}