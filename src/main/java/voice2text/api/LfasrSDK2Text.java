package voice2text.api;

import com.alibaba.fastjson.JSON;
import com.iflytek.msp.cpdb.lfasr.exception.LfasrException;
import com.iflytek.msp.cpdb.lfasr.model.LfasrType;
import com.iflytek.msp.cpdb.lfasr.model.Message;
import com.iflytek.msp.cpdb.lfasr.model.ProgressStatus;
import lombok.extern.slf4j.Slf4j;
import voice2text.entity.Text;
import voice2text.service.WebSocketServer;
import voice2text.utils.FastJsonConvertUtil;
import voice2text.utils.FileUtils;
import voice2text.utils.KeyUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 非实时转写SDK调用demo
 * 此demo只是一个简单的调用示例, 不适合用到实际生产环境中
 * 
 * @author white
 * 
 */
@Slf4j
public class LfasrSDK2Text {

    // 原始音频存放地址
 //   private static final String local_file = "E:/2.lfasr/5分钟解读：属灵汉堡早餐制作方法.mp3";
    private static final String local_save_file = "E:/2.lfasr/listen.txt";

    /*
     * 转写类型选择：标准版和电话版(旧版本, 不建议使用)分别为：
     * LfasrType.LFASR_STANDARD_RECORDED_AUDIO 和 LfasrType.LFASR_TELEPHONY_RECORDED_AUDIO
     * */
    private static final LfasrType type = LfasrType.LFASR_STANDARD_RECORDED_AUDIO;

    // 等待时长（秒）
    private static int sleepSecond = 20;

    public static String   toText(String  local_file) {
        log.debug("转文本的录音路径是：[]" + local_file);
        // 初始化LFASRClient实例
        LfasrClientImp lc = null;
        try {
            lc = LfasrClientImp.initLfasrClient();
        } catch (LfasrException e) {
            // 初始化异常，解析异常描述信息
            Message initMsg = JSON.parseObject(e.getMessage(), Message.class);
            System.out.println("ecode=" + initMsg.getErr_no());
            System.out.println("failed=" + initMsg.getFailed());
        }

        // 获取上传任务ID
        String task_id = "";
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("has_participle", "true");
        //合并后标准版开启电话版功能
        //params.put("has_seperate", "true");
        try {
            // 解析上传的音频文件
            Message uploadMsg = lc.lfasrUpload(local_file, type, params);

            // 判断返回值
            int ok = uploadMsg.getOk();
            if (ok == 0) {
                // 创建任务成功
                task_id = uploadMsg.getData();
                WebSocketServer.sendInfo("开始解析 ... "
                        + "\n" + KeyUtil.getNowDateTime(),null);

                System.out.println("task_id=" + task_id);
            } else {
                // 创建任务失败-服务端异常
                System.out.println("ecode=" + uploadMsg.getErr_no());
                System.out.println("failed=" + uploadMsg.getFailed());
            }
        } catch (LfasrException e) {
            // 上传异常，解析异常描述信息
            Message uploadMsg = JSON.parseObject(e.getMessage(), Message.class);
            System.out.println("ecode=" + uploadMsg.getErr_no());
            System.out.println("failed=" + uploadMsg.getFailed());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 循环等待音频处理结果
        while (true) {
            try {
                // 等待20s在获取任务进度
                Thread.sleep(sleepSecond * 100);
                System.out.println("waiting ...");
                WebSocketServer.sendInfo("音频转化中，waiting ... "+
                          "\n" +KeyUtil.getNowDateTime(),null);

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                // 获取处理进度
                Message progressMsg = lc.lfasrGetProgress(task_id);

                // 如果返回状态不等于0，则任务失败
                if (progressMsg.getOk() != 0) {
                    System.out.println("task was fail. task_id:" + task_id);
                    System.out.println("ecode=" + progressMsg.getErr_no());
                    System.out.println("failed=" + progressMsg.getFailed());
                    throw new RuntimeException("任务失败"+progressMsg.getErr_no()
                            + "ecode=" + progressMsg.getErr_no()
                            + "failed=" + progressMsg.getFailed());

                } else {
                    ProgressStatus progressStatus = JSON.parseObject(progressMsg.getData(), ProgressStatus.class);
                    if (progressStatus.getStatus() == 9) {
                        // 处理完成
                        System.out.println("task was completed. task_id:" + task_id);
                        break;
                    } else {
                        // 未处理完成
                        System.out.println("task is incomplete. task_id:" + task_id + ", status:" + progressStatus.getDesc());
                        continue;
                    }
                }
            } catch (LfasrException e) {
                // 获取进度异常处理，根据返回信息排查问题后，再次进行获取
                Message progressMsg = JSON.parseObject(e.getMessage(), Message.class);
                System.out.println("ecode=" + progressMsg.getErr_no());
                System.out.println("failed=" + progressMsg.getFailed());
                throw new RuntimeException("转换失败");
            }
        }
        String jsonString = "";
        // 获取任务结果
        try {
            Message resultMsg = lc.lfasrGetResult(task_id);
            // 如果返回状态等于0，则获取任务结果成功
            if (resultMsg.getOk() == 0) {
                // 打印转写结果
                 jsonString = resultMsg.getData();
                System.out.println(jsonString);
                List<Text> textList =  FastJsonConvertUtil.jsonToList(jsonString, Text.class);

             //   FileUtils.saveFile(local_save_file,jsonString);

                String a = textList.stream().map(e -> e.getOnebest()).collect(Collectors.joining()).toString();
                System.out.println(a );
                return jsonString;
            } else {
                // 获取任务结果失败
                System.out.println("ecode=" + resultMsg.getErr_no());
                System.out.println("failed=" + resultMsg.getFailed());
            }
        } catch (LfasrException e) {
            // 获取结果异常处理，解析异常描述信息
            Message resultMsg = JSON.parseObject(e.getMessage(), Message.class);
            System.out.println("ecode=" + resultMsg.getErr_no());
            System.out.println("failed=" + resultMsg.getFailed());
        }

        return jsonString ;

    }
}