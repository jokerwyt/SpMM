package spmm

import chisel3._
import chisel3.util._

class Shot extends Bundle {

  // issue output
  val lhsData = Vec(16, F44());
  val lhsRow = Vec(16, UInt(9.W));  // row index of lhs, i.e. C^T(*, _)
  val lhsCol = Vec(16, UInt(9.W));  // no need in fact
  val rhsData = Vec(16, F44());
  val iter = UInt(9.W);             // iteration number, i.e. C^T(_, *)
  
  // product output
  val products = Vec(16, F44());
  
  // Reduce output
  val prefix_sum = Vec(16, F44());
  val reduced_sum = Vec(16, F44());

  // collect used

  val merged = Bool();
  // if merged == 1, the previous weight is added to a reduced sum.
  // if merged == 0, the previous weight is bringed as following:
  val previousIter = UInt(9.W); // if none, set previousIter 16.
  val previousLhsRow = UInt(9.W);  // if none, set previousLhsRow 16.
  val previousReducedSum = F44();

  val lastData = F44();

  def default_value(): Unit = {
    lhsData.foreach { _ := F44.zero }
    lhsRow.foreach { _ := 0.U }
    lhsCol.foreach { _ := 0.U }
    rhsData.foreach { _ := F44.zero }
    iter := 16.U;

    products.foreach { _ := F44.zero }
    prefix_sum.foreach { _ := F44.zero }
    reduced_sum.foreach { _ := F44.zero }

    merged := false.B;
    previousIter := 16.U;
    previousLhsRow := 16.U;
    previousReducedSum := F44.zero;
    lastData := F44.zero;
  }

  // Collect used
  def getCollectMetadata(): CollectMetadata = {
      val _valid = Wire(Vec(16, Bool()))
      _valid(15) := true.B
      for (i <- 0 until 15) {
        _valid(i) := lhsRow(i) =/= lhsRow(i+1)
      }
      
      val firstValidTmp = Seq.tabulate(16) { _ => WireDefault(16.U(9.W))};
      firstValidTmp(15) := 15.U(9.W)
      for (i <- 14 until -1 by -1) {
        firstValidTmp(i) := Mux(_valid(i), i.U, firstValidTmp(i + 1))
      }
      val _firstValidIdx = firstValidTmp(0)

      return new CollectMetadata {
        this.valid := _valid
        this.firstValid := _firstValidIdx
      }
  }
}

class CollectMetadata extends Bundle {
  val valid = Wire(Vec(16, Bool()));
  val firstValid = Wire(UInt(9.W));
}


object Shot {
  def apply(): Shot = new Shot;

  def invalid: Shot = {
    val ret = Wire(Shot());
    ret.default_value();
    ret
  }
}

