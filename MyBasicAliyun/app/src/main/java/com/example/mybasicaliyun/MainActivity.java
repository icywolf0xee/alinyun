package com.example.mybasicaliyun;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;
import org.eclipse.paho.android.service.MqttAndroidClient;


public class MainActivity extends AppCompatActivity {

    /* 设备三元组信息 */
    private String IotInstanceId="***-********";
    private String PRODUCTKEY="***********";
    private String DEVICENAME="***";
    private String DEVICESECRET="*************************";

    /* 自动Topic, 用于上报消息 */
    private String PUB_TOPIC;

    /* 自动Topic, 用于接受消息 */
    private String SUB_TOPIC;

    /* 阿里云Mqtt服务器域名 */
    String host;

    /*Mqtt建连参数*/
    private String clientId;
    private String userName;
    private String passWord;

    //设置log.e的TAG
    private final String TAG = "AiotMqtt";

    MqttAndroidClient mqttAndroidClient;

    //ui相关变量
    TextView tv_student_name;
    EditText et_maths_score,et_chinese_score;
    Button btn_publish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_student_name=this.findViewById(R.id.student_name_content);
        et_maths_score=this.findViewById(R.id.maths_score);
        et_chinese_score=this.findViewById(R.id.chinese_score);
        btn_publish= findViewById(R.id.btn_publish);

        //根据阿里云三要素构建subtopic、pubtopic、host
        AliyunTopicHostSet(0);

        //MQTT建连选项类，输入设备三元组productKey, deviceName和deviceSecret, 生成Mqtt建连参数clientId，username和password
        AiotMqttOption aiotMqttOption = new AiotMqttOption().getMqttOption(PRODUCTKEY, DEVICENAME, DEVICESECRET);
        if (aiotMqttOption == null) {
            Log.e(TAG, "device info error");
        } else {
            clientId = aiotMqttOption.getClientId();
            userName = aiotMqttOption.getUsername();
            passWord = aiotMqttOption.getPassword();
        }



                /* Mqtt建连 */
                try {

                    /* 创建MqttConnectOptions对象并配置username和password */
                    final MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
                    mqttConnectOptions.setUserName(userName);
                    mqttConnectOptions.setPassword(passWord.toCharArray());

                    /* 创建MqttAndroidClient对象, 并设置回调接口 */
                    //String plstring;
                    mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), host, clientId);
                    mqttAndroidClient.connect(mqttConnectOptions,null, new IMqttActionListener() {
                        //连接成功方法
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            Log.i(TAG, "connect succeed");

                            subscribeTopic(SUB_TOPIC);
                        }
                        //连接失败方法
                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            Log.i(TAG, "connect failed");
                        }
                    });

                } catch (MqttException e) {
                    e.printStackTrace();
                }

        /**
         * mqtt回调类，此类内包含三个方法：connectionLost（掉线），messageArrived（订阅消息到达），deliveryComplete（发布消息送达）
         */
        mqttAndroidClient.setCallback(new MqttCallback() {

            //连接中断方法
            @Override
            public void connectionLost(Throwable cause) {
                Log.i(TAG, "connection lost");
            }

            @SuppressLint("SetTextI18n")
            @Override

            //订阅消息后，消息到达时方法
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                Log.i(TAG, "topic: " + topic + ", msg: " + new String(message.getPayload()));

                String payload = new String(message.getPayload());
                JSONObject Jobj_payload = new JSONObject(payload);
                JSONObject Jobj_params=new JSONObject(Jobj_payload.getString("params"));
                String student_name=Jobj_params.getString("student_name");

                if( (Jobj_params.has("student_name")))
                {
                    System.out.println(student_name);
                    tv_student_name.setText(student_name);
                }

            }// messageArrived类结束标志

            //发布消息后，消息投递成功后返回方法
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.i(TAG, "msg delivered");
            }
        });//mqttAndroidClient.setCallback类结束标志



        /**
         * 点"上传"按钮后，将数学、语文分数发送到阿里云物联网平台
         */
        btn_publish.setOnClickListener((view)-> {
            int maths_score=Integer.parseInt(et_maths_score.getText().toString());
            int chinese_score=Integer.parseInt(et_chinese_score.getText().toString());

            //发布消息的payload数据包生成方法一：利用JSONObject，分两层将params内的数学、语文分数，和params外的id，version打成一个json数据包
            JSONObject Jobj_payload = new JSONObject();
            JSONObject Jobj_params = new JSONObject();
            try {

                Jobj_params.put("maths_score",maths_score);
                Jobj_params.put("chinese_score",chinese_score);

                Jobj_payload.put("id", DEVICENAME);
                Jobj_payload.put("version", "1.0");
                Jobj_payload.put("params", Jobj_params);


            } catch (JSONException e) {
                e.printStackTrace();
            }
            publishMessage(Jobj_payload.toString());

            ////发布消息的payload数据包生成方法二：利用构建字符串的方法，按照json格式把字符和变量连接起来，形成一个json数据字符串。
//            String Jobj_payload_string="{\"id\":\""  +DEVICENAME+  "\", \"version\":\"1.0\"" + ",\"params\":{\"maths_score\":"+ et_maths_score.getText().toString() +",\"chinese_score\":"+chinese_score+"}}";
//            publishMessage(Jobj_payload_string);

        });

    }//oncreat结束标志


    /**
     * 设置阿里云物联网平台参数
     * @param IotInstanceType 实例类型，0:华东2（上海）服务器公共实例;1:企业实例
     */
    public void AliyunTopicHostSet(int IotInstanceType) {

        SUB_TOPIC ="/sys/" + PRODUCTKEY + "/" + DEVICENAME + "/thing/service/property/set";
        PUB_TOPIC = "/sys/"+ PRODUCTKEY + "/" + DEVICENAME + "/user/update";
        if(IotInstanceType==0)
        {
            host="tcp://" + PRODUCTKEY + ".iot-as-mqtt.cn-shanghai.aliyuncs.com:1883";//适用于公共实例华东2(上海)
        }
        else
        {
            host="tcp://" + IotInstanceId + ".mqtt.iothub.aliyuncs.com:1883";//试用于企业实例
        }

    }



    /**
     * 订阅特定的主题
     * @param topic mqtt主题
     */
    public void subscribeTopic(String topic) {
        try {
            mqttAndroidClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "subscribed succeed");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "subscribed failed");
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向默认的主题/user/update发布消息
     * @param payload 消息载荷
     */
    public void publishMessage(String payload) {
        try {
            if (!mqttAndroidClient.isConnected()) {
                mqttAndroidClient.connect();
            }

            MqttMessage message = new MqttMessage();
            message.setPayload(payload.getBytes());
            message.setQos(0);
            String PUB_TOPIC = "/sys/" + PRODUCTKEY + "/" + DEVICENAME + "/thing/event/property/post";
            mqttAndroidClient.publish(PUB_TOPIC, message,null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "publish succeed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "publish failed!");
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }



}
