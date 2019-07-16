package com.aibee.app


import java.text.SimpleDateFormat
import java.util.{Date, Properties}

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.tuple.Tuple
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.api.{CheckpointingMode, TimeCharacteristic}
import org.apache.flink.streaming.connectors.elasticsearch.{ElasticsearchSinkFunction, RequestIndexer}
import org.apache.flink.streaming.connectors.elasticsearch6.ElasticsearchSink
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.http.HttpHost
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.Requests

import scala.collection.mutable.ArrayBuffer

object NomeApp {


  def main(args: Array[String]): Unit = {
    System.setProperty("java.security.auth.login.config", "C:\\IdeaProjects\\kafka_client_jaas.conf")

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    //处理消费乱序问题 设置eventtime
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    //checkPoint常用设置参数 cusmer producer都可以使用
    /*    env.enableCheckpointing(5000)
        env.getCheckpointConfig.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE)
        env.getCheckpointConfig.setCheckpointTimeout(10000)
        env.getCheckpointConfig.setMaxConcurrentCheckpoints(1)*/


    //kafka 消费者
    val topic = "online_recognition_result_rsm_p5"
    //    val brokers="106.12.11.34:9092,106.12.35.226:9092,106.12.27.21:9092"

    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "106.12.11.34:30091,106.12.35.226:30092,106.12.27.21:30093")
    properties.setProperty("group.id", "sx")
    properties.setProperty("enable.auto.commit", "true")
    properties.setProperty("security.protocol", "SASL_PLAINTEXT")
    properties.setProperty("sasl.mechanism", "PLAIN")
    properties.setProperty("auto.offset.reset", "latest")


    //消费kafka数据
    val consumer = new FlinkKafkaConsumer[String](topic, new SimpleStringSchema(), properties)
    val data: DataStream[String] = env.addSource(consumer)

    //处理kafka数据
    val targetData = data.map(jsonString => {
      println(s"jsonString = ${jsonString}")
      val dataobject: JSONObject = JSON.parseObject(jsonString)

      /*  //1.测试取出普通json
        val face_id: String = dataobject.getString("face_id")
        println(s"~~~${face_id}~~~~")*/

      //2.取出struct  json
      /*  val client_timeObject: JSONObject = dataobject.getJSONObject("client_time")
        val first_time = client_timeObject.getLong("first_time")
        val last_time = client_timeObject.getLong("last_time")
        val enter_time = client_timeObject.getLong("enter_time")
        val exit_time = client_timeObject.getLong("exit_time")
        println("~~~~"+first_time+"\t"+last_time+"\t"+enter_time+"\t"+exit_time+"~~~")*/

      /*   //3.取出Array Json
         val photoArrayObject: JSONArray = dataobject.getJSONArray("photos")
         for(i<-0 to photoArrayObject.size()) {

           val photoObject: JSONObject = photoArrayObject.getJSONObject(i)
           val url: String = photoObject.getString("url")
           val quality = photoObject.getDouble("quality")
           val frame_time = photoObject.getLong("frame_time")

           println("~~~~"+url+"\t"+quality+"\t"+frame_time+"~~~~")
         }*/


      //业务  取出人脸 动作 时间
      val face_id: String = dataobject.getString("face_id")

      var indoorTime = 0l
      var indoorStatus = ""


      val events: JSONArray = dataobject.getJSONArray("events")
      if (events.size() > 0) {
         indoorTime= events.getJSONObject(0).getLong("host_time")
         indoorStatus = events.getJSONObject(0).getString("name")
      }

      (face_id, indoorTime, indoorStatus)
    })

    //    targetData.print()

    val filterData: DataStream[(String, Long, String)] = targetData.filter(_._2 != 0).filter(_._3.equals("inner"))


    //要添加时间戳和水印来解决 消费乱序的问题*** 结合eventtime*** //需求一：统计每5分钟进店的人数和人次

    val result: DataStream[(String, String, Long)] = filterData.assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarks[(String, Long, String)] {

      val maxOutOfOrderness = 5*60000L // 10 seconds

      var currentMaxTimestamp: Long = _ // scala里面的_是非常重要的

      override def getCurrentWatermark: Watermark = {
        new Watermark(currentMaxTimestamp - maxOutOfOrderness)
      }

      override def extractTimestamp(element: (String, Long, String), previousElementTimestamp: Long): Long = {
        val timestamp = element._2
        currentMaxTimestamp = Math.max(timestamp, currentMaxTimestamp)
        timestamp
      }
    }).keyBy(0)
      .window(TumblingEventTimeWindows.of(Time.minutes(5)))
      .apply(new WindowFunction[(String, Long, String), (String, String, Long), Tuple, TimeWindow] {

        override def apply(key: Tuple, window: TimeWindow, input: Iterable[(String, Long, String)], out: Collector[(String, String, Long)]): Unit = {

          val face_id: String = key.getField(0).toString
          //统计人次 ，人数
          var sumSecond = 0l
          var sumNumber = 0l
          val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm")
          val times = ArrayBuffer[Long]()

          val iterator: Iterator[(String, Long, String)] = input.iterator
          while (iterator.hasNext) {
            val next: (String, Long, String) = iterator.next()
            //统计人次
            sumSecond += 1
            times.append(next._2)
          }

          val date = simpleDateFormat.format(new Date(times.max))

          out.collect(face_id, date, sumSecond)
        }
      })


    //结果：写入es  官网
    val httpHosts = new java.util.ArrayList[HttpHost]
    httpHosts.add(new HttpHost("172.20.10.215", 9200, "http"))

    val esSinkBuilder = new ElasticsearchSink.Builder[(String,String,Long)](
      httpHosts,
      new ElasticsearchSinkFunction[(String,String,Long)] {
        def createIndexRequest(element: (String,String,Long)): IndexRequest = {
          val json = new java.util.HashMap[String, Any]
          json.put("face_id", element._1)
          json.put("indoorTime", element._2)
          json.put("count", element._3)

          val id = element._1 + "-" + element._2  //设置id可以进行去重

          return Requests.indexRequest()
            .index("flink_wsx_test_count")
            .`type`("traffic")
            .id(id)  //设置id可以进行去重
            .source(json)
        }

        override def process(t: (String, String, Long), runtimeContext: RuntimeContext, requestIndexer: RequestIndexer): Unit = {
          requestIndexer.add(createIndexRequest(t))
        }
      }
    )

    // configuration for the bulk requests; this instructs the sink to emit after every element, otherwise they would be buffered
    esSinkBuilder.setBulkFlushMaxActions(1)

    // finally, build and add the sink to the job's pipeline
    result.addSink(esSinkBuilder.build) //.setParallelism(5)




    env.execute("NomeApp")
  }

}
