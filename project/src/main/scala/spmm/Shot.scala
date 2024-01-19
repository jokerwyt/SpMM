package spmm

import chisel3._
import chisel3.util._

class Shot extends Bundle {

  // issue output
  val lhsData = Vec(16, F44());
  val lhsRow = Vec(16, UInt(8.W));  // row index of lhs, i.e. C^T(*, _)
  val lhsCol = Vec(16, UInt(8.W));  // no need in fact
  val rhsData = Vec(16, F44());
  val iter = UInt(8.W);             // iteration number, i.e. C^T(_, *)
  


  // product output
  val products = Vec(16, F44());

  def default_value(): Unit = {
    lhsData.foreach { _ := F44.zero }
    lhsRow.foreach { _ := 0.U }
    lhsCol.foreach { _ := 0.U }
    rhsData.foreach { _ := F44.zero }
    iter := 16.U;

    products.foreach { _ := F44.zero }
  }
}

object Shot {
  def apply(): Shot = {
    val shot = new Shot()
    shot
  }
}
