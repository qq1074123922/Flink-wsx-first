package com.aibee.app

import java.text.SimpleDateFormat
import java.util.Properties

import com.aibee.bean.JsonBean
import com.alibaba.fastjson.JSON
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.table.api.scala.StreamTableEnvironment
import org.apache.flink.table.api.{Table, TableEnvironment}
import org.apache.flink.types.Row


object NomeNewTableApiApp {

  def main(args: Array[String]): Unit = {

    //env
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    val tableEnv: StreamTableEnvironment = TableEnvironment.getTableEnvironment(env)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    //source kafka
    val topic = "online_recognition_result_rsm_p5"
    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "106.12.11.34:30091,106.12.35.226:30092,106.12.27.21:30093")
    properties.setProperty("group.id", "sx")
    properties.setProperty("enable.auto.commit", "true")
    properties.setProperty("security.protocol", "SASL_PLAINTEXT")
    properties.setProperty("sasl.mechanism", "PLAIN")
    properties.setProperty("auto.offset.reset", "latest")
    System.setProperty("java.security.auth.login.config", "C:\\IdeaProjects\\kafka_client_jaas.conf")

    val consumer = new FlinkKafkaConsumer[String](topic, new SimpleStringSchema(), properties)
    val data: DataStream[String] = env.addSource(consumer)

    //done transform
    val dataDstream: DataStream[JsonBean.JsonData] = data.map(json => {
      JSON.parseObject(json, classOf[JsonBean.JsonData])
    })

    //watermark ts latetime
    val dataWithEventTime: DataStream[JsonBean.JsonData] = dataDstream.assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarks[JsonBean.JsonData] {
      val maxOutOfOrderness = 10000L // 10 seconds
      var currentMaxTimestamp: Long = _ // scala里面的_是非常重要的
      val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

      override def getCurrentWatermark: Watermark = {
        new Watermark(currentMaxTimestamp - maxOutOfOrderness)
      }

      override def extractTimestamp(element: JsonBean.JsonData, previousElementTimestamp: Long): Long = {
        var timestamp =0l

        val events: Array[JsonBean.Events] = element.events
        for (x <-events){

          if(x.name.equals("inner")){
            timestamp = x.host_time
            currentMaxTimestamp = Math.max(timestamp, currentMaxTimestamp)
            timestamp
          }
        }
        currentMaxTimestamp = Math.max(timestamp, currentMaxTimestamp)
        timestamp
      }
    }).setParallelism(1)


   /* //侧输出 zai  shui yin zhi qian
    val real_time_unique_enter = new OutputTag[JsonBean.JsonData]("real_time_unique_enter")
    val real_time_all_enter = new OutputTag[JsonBean.JsonData]("real_time_all_enter")
    val pass_sub_store_count = new OutputTag[JsonBean.JsonData]("pass_sub_store_count")*/

    //change table api
    val dataWithTimeTable: Table = tableEnv.fromDataStream(dataWithEventTime)
    val testtable: Table = dataWithTimeTable.groupBy("face_id").select("face_id,count(1) as countpeople")
    testtable.printSchema()
     val dsRow: DataStream[(Boolean, Row)] = tableEnv.toRetractStream[Row](testtable)

    dsRow.print()

    env.execute("NomeNewTableApiApp")
  }

}
