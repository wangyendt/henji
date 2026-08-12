# 轻衡 Android

本地优先的 Android 体重、身体成分与饮食记录 App。它可以实时扫描兼容 Fitdays/QN/ICOMON、Bluetooth SIG 和部分小米协议的蓝牙体脂秤，保存趋势，并通过自托管 CodexTask 服务识别餐食照片。

## 已实现

- 实时扫描、连接与显示体重变化，稳定后自动保存
- Fitdays 常见协议适配：
  - QN / Yolanda：`FFE0/FFE1/FFE3/FFE4`、`FFF0/FFF1/FFF2`
  - ICOMON General Scale：`FFB0/FFB2/FFB3` 的 A2/A3 分包
  - QN AABB 厂商广播
  - Bluetooth SIG Weight Scale `181D/2A9D`
  - Bluetooth SIG Body Composition `181B/2A9C`
  - 小米体重秤与体脂秤常见广播
- QN/Fitdays 单位和时间握手；未知型号可查看原始十六进制通知用于继续适配
- Room 本地存储、7/30/90 天趋势、历史记录、手动补录
- 体重、BMI、体脂、体水分、骨骼肌、BMR、去脂体重、皮下脂肪、内脏脂肪、肌肉、骨量、蛋白质和身体年龄
- 拍照/选图，通过本地 CodexTask HTTP 服务识别菜品、热量区间和三大营养素

## 哪些数据来自蓝牙

对大多数四电极 Fitdays 兼容秤，蓝牙直接传输的是：

| 数据 | 常见来源 |
|---|---|
| 体重、稳定状态、单位 | 秤直接传输 |
| 生物电阻抗（一个或两个阻抗值） | 体脂秤直接传输 |
| 分段阻抗 | 部分八电极 ICOMON/Fitdays 秤直接传输 |
| BMI、体脂率、体水分、骨骼肌、BMR、去脂体重、皮下/内脏脂肪、肌肉量、骨量、蛋白质、身体年龄 | 通常由 App 根据体重、阻抗、身高、年龄和性别计算 |

Bluetooth SIG `2A9C` 设备也可以直接发送部分身体成分字段；此时 App 会优先使用数据包里的值。界面会区分“设备直接数据”和“本机估算”。本项目的本机公式用于趋势参考，并不保证与 Fitdays 的私有算法逐项一致。

“Fitdays 协议”不是一个覆盖所有贴牌秤型号的单一公开协议。不同硬件可能使用 QN、ICOMON、标准 BLE、纯广播或加密协议。因此真机首次连接时，建议打开称重页底部的“协议调试信息”；如果没有体重数据，可提交设备名、服务 UUID 和不含个人资料的原始通知十六进制。

## 构建

要求 Android Studio（JDK 17+）与 Android SDK 35：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest assembleDebug
```

调试 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

蓝牙和相机必须在真机验证；模拟器只适合查看界面和运行解析测试。

## CodexTask 饮食识别

电脑端启动服务并创建具有 `text` 权限的 Service Token：

```bash
codex-task token create --help
codex-task serve --host 0.0.0.0 --port 7777 --token-file TOKEN_FILE
```

然后在 App 的 **我的 → CodexTask 饮食识别** 填写：

- Android 模拟器：`http://10.0.2.2:7777`
- Android 真机：`http://电脑的局域网地址:7777`
- Service Token：由 `codex-task token create` 生成的、只授予 `text` 的 Token

照片仅在主动识别时发送到你配置的服务。Token 保存在 Android DataStore 中，不写入源码和日志。正式分发时建议把 Token 改存 Android Keystore 加密存储，并给服务增加 TLS 反向代理。

## 参考

- UI 与本地优先思路参考 [qingheng-ios](https://github.com/jingxizc/qingheng-ios)（MIT）
- ICOMON FFB0 协议研究参考 [sacoma-lib](https://github.com/ynsgnr/sacoma-lib)（MIT）
- 标准体重秤服务参考 [Bluetooth SIG Weight Scale Service](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/WSS_v1.0.1/out/en/index-en.html)

## 隐私与健康说明

体重、身体记录和照片默认保存在设备本地；项目没有自建账号、广告或分析 SDK。BIA 会受饮水、进食、运动、皮肤接触和测量时间影响。结果适合观察趋势，不用于疾病诊断或治疗决策。

## License

MIT

