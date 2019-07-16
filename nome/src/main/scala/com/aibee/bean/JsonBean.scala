package com.aibee.bean

object JsonBean {

  //client_time :bean
  case class Client_time(           // 客户端时间
                first_time: Long,   // 第一次出现在当前摄像头的时间
                last_time: Long,    // 离开当前摄像头的时间
                enter_time: Long,   // 第一次出现在当前摄像头有效区域的时间
                exit_time: Long     // 离开当前摄像头有效区域的时间
                        )

  //photos : bean
  case  class Photos(                // 客户端请求的照片列表，通常长度为3
                url:String,          // 照片url，为百度云的bos地址，可以直接访问
                quality:Double,      // 照片质量分（0~1）
                frame_time:Long      // 抓取时间
                    )

  //events
  case class Events(                 // track的进出门事件
                host_time : Long,    // 事件发生时的时间戳
                name : String,       // 事件名字 inner在门内、outer在门外、left、right、Other TBD
                osd_time : Long      // 事件发生时OCR识别时间，如果没使用OCR，该值为0
                   )

  //tracks
  case class Tracks(                 // 从0开始的track信息索引，用来区分track信息先后次序。
                box:  Box,           // 人头像在帧数据中的位置
                host_time: Long,     // 获取该track点数据时服务器的时间戳，最后一个结束项该数据为0
                index: Int,          // 最后一个结束项索引为-1，并且数据无效
                video_time: Long
  )

  //box
  case class Box(
              angle: Int,
              height: Int,
              left: Int,
              top: Int,
              width: Int
                )

  //process_context
  case  class  Process_context(       // 处理的上下文，用于debug
                tmp_res: String,      // 临时库比对详情 json string
                history_res: String   // 历史库比对详情 json string
                              )

  //json-all
  case class JsonData(
                request_id: String,        // 请求唯一标识
                project_id: String,        // 项目id
                mall_id: String,           // 商场id，（后续会废弃，请使用site_id）
                site_id: String,           // 站点id，比如：AFU_beijing_xhm，详细定义：http://wiki.aibee.cn/pages/viewpage.action?pageId=12422397
                product_id: String,        // 产品id，表示产品的功能or模块，比如：trafficlite|trafficfull|vip
                face_id: String,           // 一次人脸track的唯一id，同一个face_id可能会推送多次
                temp_id: String,           // 临时库id，人脸比对服务中的id，通常下游可以不用关心该id，主要用于debug
                user_id: String,           // 用户唯一id，等价于pid，该字段可能为空
                action: String,            // 进店状态（新），详细定义请查看：http://wiki.aibee.cn/pages/viewpage.action?pageId=12424592
                is_new_user: Boolean,      // 是否是新用户
                camera_ip: String,         // 摄像头的ip，通常是内网ip
                camera_id: String,         // 客户端配置的摄像头标识
                status: Int,               // 进店状态（老）：-1未知、0进店、1离店、2路过、3店内、4异常（建议使用action字段）
                age: Int,                  // 年龄
                gender: Int,               // 性别：0女、1男
                race: Int,                 // 人种：0黄种人、1白人、2黑人、3印第安人
                white_type: Int,           // 白名单：0普通客流，1店员
                request_time: Long,        // 接收到请求的时间
                process_start_time: Long,  // 开始处理请求的时间
                process_end_time: Long,    // 处理完成推送到kafka的时间
                process_status: Int,       // 推送状态，用于下游对齐数据，-1表示未知，0表示临时库注册并成功，1表示比对低质量分，2表示空照片，3表示注册异常，4表示注册质量分不达标，5vip识别数据
                client_time: Client_time,
                package_index: Int,        // 数据包索引，从1开始，最后一个包索引是索引的负值
                branch: String,            // 推送分支，主要用于debug
                match_score: Int,          // 比对分数
                match_photo_index: Int,    // 比对的照片在photos列表中的索引
                photos: Array[Photos],     // 客户端请求的照片列表，通常长度为3
                events : Array[Events],    // track的进出门事件
                tracks: Array[Tracks],      // 从0开始的track信息索引，用来区分track信息先后次序。
                process_context: Process_context // 处理的上下文，用于debug
                     )


}
