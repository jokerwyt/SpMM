package playground

import chisel3._
import chiseltest._
import org.scalatest.ParallelTestExecution
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable
import scala.util.Random

trait PlaygroundTest extends AnyFlatSpec
  with ChiselScalatestTester
{
  def testItOn(): Unit = {
    test(new Playground)
      .withAnnotations(Seq(
        WriteVcdAnnotation,         // 输出的波形在 test_run_dir 里
        VerilatorBackendAnnotation  // 使用 Verilator 会评测地更快
      )) { dut =>
        dut.clock.step(1)
        dut.io.write_idx.poke(0.U)

        for (i <- 0 until 2) {
          dut.io.write_data(i).poke(i.asUInt)
        }

        dut.clock.step(1)
        dut.io.read_idx.poke(0.U)
        dut.clock.step(1)
        dut.io.read_idx.poke(0.U)
        dut.clock.step(1)
        dut.io.read_idx.poke(0.U)
        dut.clock.step(1)
        dut.io.read_idx.poke(0.U)
        dut.clock.step(1)
      }
  }
}

class PlaygroundTestExample extends PlaygroundTest {
  behavior of "Playground Test"
  it should "work pass test" in
    testItOn()
}
