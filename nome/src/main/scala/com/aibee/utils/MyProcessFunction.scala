package com.aibee.utils

import com.aibee.bean.JsonBean
import org.apache.flink.api.java.tuple.Tuple
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector

object MyProcessFunction extends  ProcessFunction[JsonBean.JsonData,Tuple]{


  override def processElement(value: JsonBean.JsonData, ctx: ProcessFunction[JsonBean.JsonData, Tuple]#Context, out: Collector[Tuple]): Unit = {


  }
}
