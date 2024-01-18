package spmm

import chisel3._
import scala.collection.mutable

object F44 {
  def apply(): F44 = new F44
  def zero: F44 = {
    val ret = Wire(F44())
    ret.data := 0.S
    ret
  }
  val counter = ThreadLocal.withInitial(() => mutable.HashMap[String, Int]())
}

class F44 extends Bundle {
  val data = SInt(8.W)
  private def arithFunc(tag: String, f: (SInt, SInt) => Bits)(r: F44): F44 = {
    val counter = F44.counter.get()
    counter(tag) = counter.getOrElse(tag, 0) + 1
    val ret = Wire(F44())
    ret.data := RegNext(WireInit(SInt(8.W), f(data, r.data)))
    ret
  }
  def + (rhs: F44): F44 = arithFunc("add", _ + _)(rhs)
  def - (rhs: F44): F44 = arithFunc("add", _ - _)(rhs)
  def * (rhs: F44): F44 = arithFunc("mul", (a, b) => (a * b) >> 4)(rhs)
}
