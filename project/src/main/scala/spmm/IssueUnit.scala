package spmm

import chisel3._
import chisel3.util._


class IssueUnit extends Module {
  val io = IO(new Bundle {
    
    // SpMM external interface
    val start = Input(Bool())
    val inputReady = Output(Bool())
    val lhsRowEnding = Input(Vec(16, UInt(9.W)))
    val lhsCol = Input(Vec(16, UInt(9.W)))
    val lhsData = Input(Vec(16, F44()))
    val rhsReset = Input(Bool())
    val rhsData = Input(Vec(16, F44()))

    // SpMM internal interface
    val shot = Output(Shot())
  })
  io.shot.default_value();

  // ========== edge 1: Iterator & rowEndingBuffer
  val iterator = Module(new Iterator());
  val lhsRowEndingBuffer = RegInit(VecInit(Seq.fill(16)(0.U(9.W))));

  // if start = 1, aLen should be io.lhsRowEnding(15)
  // otherwise aLen should be lhsRowEndingBuffer(15)
  // this is valid even in edge 1.
  val aLen = Mux(io.start, io.lhsRowEnding(15) + 1.U, lhsRowEndingBuffer(15) + 1.U);

  {
    when (io.start) {
      lhsRowEndingBuffer := io.lhsRowEnding
    }

    iterator.io.aLenIn := aLen
    iterator.io.start := io.start
    io.inputReady := iterator.io.readyForStart && !io.start
  }
  // iterator.io.out can be used in edge 2
  val lhsPickIdx_edge1 = WireDefault(iterator.io.out.firstIdx / 16.U) // used in edge 2

  val iterOut_edge2 = ShiftRegister(iterator.io.out, 1, {
    val output = Wire(IteratorOutput())
    output.iterIdx := 16.U
    output.firstIdx := 0.U
    output.aLen := 0.U
    output
  }, true.B)          // used in edge 3

  // ========== edge 2: lhsDataBuf

  val lhsDataMem = SyncReadMem(17, Vec(16, F44()), SyncReadMem.WriteFirst);
  val lhsColMem = SyncReadMem(17, Vec(16, UInt(9.W)), SyncReadMem.WriteFirst);

  { // always running part in edge 2
    val lhsNxtWrite = RegInit(0.U(9.W)) // control write to lhsDataMem
    when (true.B) {
      when (io.start) {
        lhsNxtWrite := 1.U
      }.otherwise {
        // add until the first index that becomes out of range
        when (lhsNxtWrite * 16.U < iterator.io.out.aLen) {
          lhsNxtWrite := lhsNxtWrite + 1.U
        }
      }
      val groupIndexBypassed = Mux(io.start, 0.U(9.W), lhsNxtWrite)

      when (groupIndexBypassed * 16.U < aLen) {
        lhsDataMem.write(groupIndexBypassed, io.lhsData)
        lhsColMem.write(groupIndexBypassed, io.lhsCol)
      }
    }
  }

  // load address in edge 2
  // used in edge 3
  val lhsDataPick_edge2 = lhsDataMem.read(lhsPickIdx_edge1)
  val lhsColPick_ = lhsColMem.read(lhsPickIdx_edge1)
  val lhsColPick_edge2 = Seq.tabulate(16)(i => {            // used in edge 3
    Mux(
      iterOut_edge2.firstIdx + i.asUInt < iterOut_edge2.aLen,
      lhsColPick_(i),
      16.U(9.W)
    )
  });


  // ========== edge 3: rhsDataBuf read
  val rhsDataMem = SyncReadMem(17, Vec(16, F44()), SyncReadMem.WriteFirst)
  val rhsDataPick_edge2 = rhsDataMem.read(iterator.io.out.iterIdx)

  { // always running part in edge 3
    val rhsDataNxtWrite = RegInit(0.U(9.W)) // control write to rhsDataMem
    when (true.B) {
      when (io.start && io.rhsReset) {
        rhsDataNxtWrite := 1.U
      }.otherwise {
        // add until the first index that becomes out of range
        when (rhsDataNxtWrite < 16.U) {
          rhsDataNxtWrite := rhsDataNxtWrite + 1.U
        }
      }
      val groupIndexBypassed = Mux(io.start && io.rhsReset, 0.U(9.W), rhsDataNxtWrite)

      when (groupIndexBypassed < 16.U) {
        rhsDataMem.write(groupIndexBypassed, io.rhsData)
      }
    }
  }

  val lhsRowTmp = WireDefault(VecInit(Seq.fill(17)(0.U(9.W))))
  io.shot.lhsRow := ShiftRegister(lhsRowTmp, 1)

  for (i <- 0 until 16) {
    io.shot.lhsData(i) := RegNext(lhsDataPick_edge2(i))

    // assign io.shot.lhsRow, according to lhsRowEndingBuffer
    lhsRowTmp(i) := 0.U
    for (j <- 0 until 16) {
      when (lhsRowEndingBuffer(j) < iterOut_edge2.firstIdx + i.asUInt(9.W)) {
        lhsRowTmp(i) := j.asUInt(9.W) + 1.U
      }
    }

    io.shot.lhsCol(i) := lhsColPick_edge2(i)
    io.shot.rhsData(i) := RegNext(rhsDataPick_edge2(lhsColPick_edge2(i)))
  }

  io.shot.iter := RegNext(iterOut_edge2.iterIdx, 16.U)
}

class IteratorOutput extends Bundle {
  val iterIdx = UInt(9.W);          // 0..16, 16 means invalid
  val firstIdx = UInt(9.W);         // [firstIdx, max(firstIdx+16, aLen)) 
                                    // is the range of A to issue
  val aLen = UInt(9.W);
}

object IteratorOutput {
  def apply(): IteratorOutput = {
    val output = new IteratorOutput()
    output
  }
}

// 从start信号为真的下一周期开始，重复迭代A 16次：
// 每一遍：
// 若干周期，每个周期输出一个firstIdx表示当前发射的16个元素从firstIdx开始
// firstIdx分别是0, 16, 32, 直到小于alen的第一个16的倍数
// 如果firstIdx后不足16个元素，由后边的模块负责处理边界情况
//  
// 在上述过程结束后，还需要输出kWaitGap个iterIdx为16的周期，表示结束
// 才能重新拉高readyForStart信号
//
// 如果aLen=0，那么直接跳过所有的迭代，进入wait阶段（不断输出16）
class Iterator extends Module {
  val io = IO(new Bundle{
    val start = Input(Bool())
    val aLenIn = Input(UInt(9.W))

    val readyForStart = Output(Bool())
    val out = new IteratorOutput()
  })

  val iterIdx = RegInit(16.U(9.W))  // 0..16, 16 means invalid
  val next = RegInit(0.U(9.W))      // next index of A to issue
  val aLen = RegInit(0.U(9.W))      // length of A to issue

  val kWaitGap = 15.U(9.W)          // wait gap between two runs' issues
  val waitCounter = RegInit(0.U)
  val readyForStart = WireDefault(iterIdx === 16.U)

  when (io.start) {
    iterIdx := 0.U
    next := 0.U
    aLen := io.aLenIn
    waitCounter := kWaitGap
    
  } .elsewhen(iterIdx < 16.U) {
    
    when (aLen === 0.U) {
      // special case
      // we skill all the issues, jump to wait directly.
      iterIdx := 16.U
    }.otherwise {

      when (next + 16.U < aLen) {
        next := next + 16.U
      } .otherwise {
        iterIdx := iterIdx + 1.U
        next := 0.U
      }
    }

  } .otherwise { 
    //iterIdx == 16
    when (waitCounter > 0.U) {
      waitCounter := waitCounter - 1.U
    }
  }

  io.out.iterIdx := iterIdx
  io.out.firstIdx := next
  io.out.aLen := aLen
  io.readyForStart := readyForStart && (waitCounter === 0.U)
}
